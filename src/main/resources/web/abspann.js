/**
 * Verknüpft den Ergebnisdialog mit der Wiedergabe der Schiffstreffer und der Siegermusik.
 */
export function erstelleAbspann({spielfeldgroesse, getAudioContext, bereiteTonVor, ladeTon}) {
    const siegermusikDatei = "result-win";
    const resultDialog = document.querySelector("#result-dialog");
    const resultReplay = document.querySelector("#result-replay");
    const resultHeading = document.querySelector("#result-heading");
    const resultFleet = document.querySelector("#result-fleet");
    let angezeigtePartie;
    let spielstandFuerWiederholung;
    let resultMusic;
    let resultPlayback = 0;

    function bereiteSiegermusikVor() {
        if (getAudioContext()) void ladeTon(siegermusikDatei).catch(() => {});
    }

    function erstelleSchiffselemente(ships, hits, own) {
        let offset = 0;
        let survivorOffset = 0;
        const gap = .5;
        const entries = ships.map(ship => {
            const step = ship.direction === "VERTICAL" ? spielfeldgroesse : 1;
            const impacts = Array.from({length: ship.type.length}, (_, segment) => hits.get(ship.start + segment * step));
            return {ship, impacts, sinking: impacts.find(impact => impact?.sunk)};
        });
        // Keep ships that sink late near the edges, clear of the survivors' path to the center.
        entries.sort((first, second) => (second.sinking?.time ?? -1) - (first.sinking?.time ?? -1));
        const survivors = entries.filter(({sinking}) => !sinking);
        const survivorWidth = survivors.reduce((width, {ship}) => width + ship.type.length, 0)
            + Math.max(0, survivors.length - 1) * gap;

        return entries.map(({ship, impacts, sinking}, index) => {
            const item = document.createElement("li");
            item.className = "result-ship";
            item.toggleAttribute("data-own", own);
            item.style.setProperty("--ship-length", ship.type.length);
            const start = own
                ? "calc(98% - " + (offset + ship.type.length) + " * var(--ship-unit))"
                : "calc(2% + " + offset + " * var(--ship-unit))";
            item.style.setProperty("--start-x", start);
            offset += ship.type.length + gap;

            const graphic = document.createElement("div");
            graphic.className = "result-ship-graphic";
            if (sinking) {
                item.setAttribute("data-sunk", "");
                item.style.setProperty("--arrival-x", "calc(" + start + (own ? " - 5%" : " + 5%") + ")");
                graphic.style.setProperty("--sink-time", (sinking.time + 700) + "ms");
                const tilt = [-28, 0, 28][(index + Number(own)) % 3];
                graphic.style.setProperty("--sink-tilt", tilt + "deg");
            } else {
                const centerOffset = survivorOffset - survivorWidth / 2;
                item.style.setProperty("--arrival-x", "calc(50% + " + centerOffset + " * var(--ship-unit))");
                survivorOffset += ship.type.length + gap;
            }

            const image = document.createElement("img");
            image.src = ship.type.image;
            image.alt = ship.type.name + ", " + impacts.filter(Boolean).length
                + " von " + ship.type.length + " Feldern getroffen";
            const markers = document.createElement("div");
            markers.className = "result-ship-hits";
            markers.setAttribute("aria-hidden", "true");
            impacts.forEach((impact, segment) => {
                if (!impact) return;
                const flame = document.createElement("span");
                flame.className = "fire";
                flame.textContent = "🔥";
                flame.style.gridColumn = String(segment + 1);
                flame.style.setProperty("--hit-time", impact.time + "ms");
                markers.append(flame);
            });
            graphic.append(image, markers);
            item.append(graphic);
            return item;
        });
    }

    function zeigeAbspann(spielstand, bildschirm) {
        if (bildschirm !== "PLAY" || !spielstand.finished) {
            if (resultDialog.open) resultDialog.close();
            return;
        }
        spielstandFuerWiederholung = spielstand;
        if (angezeigtePartie === spielstand.id) return;
        angezeigtePartie = spielstand.id;
        const outcome = !spielstand.winner ? "draw" : spielstand.winner === "PLAYER" ? "win" : "loss";
        resultDialog.dataset.outcome = outcome;
        resultHeading.textContent = {win: "Gewonnen", loss: "Verloren", draw: "Unentschieden"}[outcome];
        resultDialog.showModal();
        starteWiedergabe(spielstand);
    }

    function starteWiedergabe(spielstand) {
        stoppeSiegermusik();
        const ownHits = new Map();
        const enemyHits = new Map();
        const hits = spielstand.replayHits;
        if (hits.length) {
            const first = hits[0].timeMillis;
            const duration = hits.at(-1).timeMillis - first;
            const minimumGap = 160;
            const remainingTime = 14100 - (hits.length - 1) * minimumGap;
            hits.forEach((hit, index) => {
                const progress = duration ? (hit.timeMillis - first) / duration : index / Math.max(1, hits.length - 1);
                const time = Math.round(900 + index * minimumGap + progress * remainingTime);
                (hit.own ? ownHits : enemyHits).set(hit.cell, {...hit, time});
            });
        }
        resultFleet.replaceChildren(
            ...erstelleSchiffselemente([...spielstand.sunk, ...spielstand.revealed], enemyHits, false),
            ...erstelleSchiffselemente(spielstand.ownFleet, ownHits, true)
        );
        resultReplay.hidden = resultFleet.children.length === 0;
        void spieleSiegermusik(resultDialog.dataset.outcome, resultPlayback);
    }

    resultReplay.addEventListener("click", () => {
        bereiteTonVor();
        starteWiedergabe(spielstandFuerWiederholung);
    });

    async function spieleSiegermusik(outcome, playback) {
        const audioContext = getAudioContext();
        if (outcome !== "win" || !audioContext) return;
        try {
            const buffer = await ladeTon(siegermusikDatei);
            if (audioContext.state !== "running") await audioContext.resume();
            if (!resultDialog.open || playback !== resultPlayback) return;
            const source = audioContext.createBufferSource();
            const volume = audioContext.createGain();
            volume.gain.value = .45;
            source.buffer = buffer;
            source.connect(volume).connect(audioContext.destination);
            source.addEventListener("ended", () => {
                if (resultMusic?.source === source) resultMusic = undefined;
                source.disconnect();
                volume.disconnect();
            }, {once: true});
            resultMusic = {source, volume};
            source.start();
        } catch {
            // The result is still available when audio playback fails.
        }
    }

    function stoppeSiegermusik() {
        resultPlayback++;
        if (!resultMusic) return;
        const {source, volume} = resultMusic;
        resultMusic = undefined;
        const now = getAudioContext().currentTime;
        volume.gain.setTargetAtTime(0, now, .035);
        source.stop(now + .18);
    }

    resultDialog.addEventListener("close", stoppeSiegermusik);

    return {zeigeAbspann, bereiteSiegermusikVor};
}
