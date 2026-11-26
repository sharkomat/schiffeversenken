package de.kj.games;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Verwaltet offene Einladungen für Mehrspielerpartien. Einladungen können einmal angenommen werden und
 * verfallen nach einer festen Frist.
 */
final class Spieleinladungen
{
    static final long GUELTIGKEITSDAUER_MILLIS = TimeUnit.MINUTES.toMillis(3);
    private static final long GUELTIGKEITSDAUER_NANOS = TimeUnit.MILLISECONDS.toNanos(GUELTIGKEITSDAUER_MILLIS);

    private final Map<String, Einladung> offeneEinladungen = new LinkedHashMap<>();
    private final ScheduledExecutorService ablaufplanung = Executors.newSingleThreadScheduledExecutor(
            Thread.ofPlatform().name("game-request-expiry").daemon().factory());

    /**
     * Legt eine Einladung mit öffentlicher ID und separatem Gastgeber-Token an. Plant ihre automatische
     * Entfernung nach Ablauf der Frist.
     */
    synchronized String erstelleEinladung(String name, Schwierigkeitsgrad schwierigkeitsgrad, long zeitpunkt)
    {
        String id = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString();
        offeneEinladungen.put(id, new Einladung(id, token, name, schwierigkeitsgrad, zeitpunkt));
        ablaufplanung.schedule(() -> entferneEinladung(id), GUELTIGKEITSDAUER_NANOS, TimeUnit.NANOSECONDS);
        return "{\"id\":\"%s\",\"token\":\"%s\",\"lifetimeMillis\":%d}".formatted(id, token, GUELTIGKEITSDAUER_MILLIS);
    }

    /**
     * Entfernt abgelaufene Einladungen und liefert die offenen Einladungen als JSON ohne Gastgeber-Token.
     */
    synchronized String listeEinladungenAuf(long zeitpunkt)
    {
        entferneAbgelaufeneEinladungen(zeitpunkt);
        String einladungen = offeneEinladungen.values().stream().map(einladung -> einladung.json(zeitpunkt))
                .collect(Collectors.joining(","));
        return "{\"lifetimeMillis\":%d,\"requests\":[%s]}".formatted(GUELTIGKEITSDAUER_MILLIS, einladungen);
    }

    /**
     * Entnimmt eine gültige Einladung für den Beitritt. Gibt {@code null} zurück, wenn sie fehlt, abgelaufen
     * ist oder mit dem Gastgeber-Token beansprucht wird.
     */
    synchronized Einladung beansprucheEinladung(String id, String token, long zeitpunkt)
    {
        entferneAbgelaufeneEinladungen(zeitpunkt);
        Einladung einladung = offeneEinladungen.get(id);
        if (einladung == null || einladung.token().equals(token)) return null;
        return offeneEinladungen.remove(id);
    }

    synchronized boolean wartetAufMitspieler(String token, long zeitpunkt)
    {
        entferneAbgelaufeneEinladungen(zeitpunkt);
        return offeneEinladungen.values().stream().anyMatch(einladung -> einladung.token().equals(token));
    }

    private void entferneAbgelaufeneEinladungen(long zeitpunkt)
    {
        offeneEinladungen.values().removeIf(einladung -> zeitpunkt - einladung.erstelltAm() >= GUELTIGKEITSDAUER_NANOS);
    }

    private synchronized void entferneEinladung(String id)
    {
        offeneEinladungen.remove(id);
    }

    /**
     * Speichert die öffentliche Einladungs-ID, das Gastgeber-Token und die Angaben zur geplanten Partie.
     */
    record Einladung(String id, String token, String name, Schwierigkeitsgrad schwierigkeitsgrad, long erstelltAm)
    {
        private String json(long zeitpunkt)
        {
            long alterMillis = TimeUnit.NANOSECONDS.toMillis(zeitpunkt - erstelltAm);
            return "{\"id\":\"%s\",\"name\":%s,\"difficulty\":\"%s\",\"ageMillis\":%d}"
                    .formatted(id, Json.inAnfuehrungszeichen(name), schwierigkeitsgrad.bezeichnung, alterMillis);
        }
    }
}
