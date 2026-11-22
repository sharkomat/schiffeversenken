package de.kj.games;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

final class GameRequests {
    static final long LIFETIME_MILLIS = TimeUnit.MINUTES.toMillis(3);
    private static final long LIFETIME_NANOS = TimeUnit.MILLISECONDS.toNanos(LIFETIME_MILLIS);

    private final Map<String, Request> open = new LinkedHashMap<>();
    private final ScheduledExecutorService expiry = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("game-request-expiry").daemon().factory());

    synchronized String create(String name, Difficulty difficulty, long now) {
        String id = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString();
        open.put(id, new Request(id, token, name, difficulty, now));
        expiry.schedule(() -> remove(id), LIFETIME_NANOS, TimeUnit.NANOSECONDS);
        return "{\"id\":\"%s\",\"token\":\"%s\",\"lifetimeMillis\":%d}".formatted(id, token, LIFETIME_MILLIS);
    }

    synchronized String list(long now) {
        expire(now);
        String requests = open.values().stream().map(request -> request.json(now))
                .collect(Collectors.joining(","));
        return "{\"lifetimeMillis\":%d,\"requests\":[%s]}".formatted(LIFETIME_MILLIS, requests);
    }

    synchronized Request claim(String id, String token, long now) {
        expire(now);
        Request request = open.get(id);
        if (request == null || request.token().equals(token)) return null;
        return open.remove(id);
    }

    synchronized boolean waiting(String token, long now) {
        expire(now);
        return open.values().stream().anyMatch(request -> request.token().equals(token));
    }

    private void expire(long now) {
        open.values().removeIf(request -> now - request.createdAt() >= LIFETIME_NANOS);
    }

    private synchronized void remove(String id) {
        open.remove(id);
    }

    record Request(String id, String token, String name, Difficulty difficulty, long createdAt) {
        private String json(long now) {
            long ageMillis = TimeUnit.NANOSECONDS.toMillis(now - createdAt);
            return "{\"id\":\"%s\",\"name\":%s,\"difficulty\":\"%s\",\"ageMillis\":%d}"
                    .formatted(id, Json.quote(name), difficulty.label, ageMillis);
        }
    }
}
