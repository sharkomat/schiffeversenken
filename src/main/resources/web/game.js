"use strict";

const size = 10;
const rows = "ABCDEFGHIJ";
const play = document.querySelector("#play");
const startScreen = document.querySelector("#start-screen");
const startForm = document.querySelector("#start-form");
const start = document.querySelector("#start");
const playerName = document.querySelector("#player-name");
const difficulty = document.querySelector("#difficulty");
const startError = document.querySelector("#start-error");
const setupScreen = document.querySelector("#setup-screen");
const gameScreen = document.querySelector("#game-screen");
const gameHeader = document.querySelector(".game-header");
const caption = document.querySelector("#game-caption");
const nation = document.querySelector("#nation");
const shipList = document.querySelector("#ship-list");
const orientation = document.querySelector("#orientation");
const randomAll = document.querySelector("#random-all");
const randomRemaining = document.querySelector("#random-remaining");
const setupFeedback = document.querySelector("#setup-feedback");
const finish = document.querySelector("#finish");
const resume = document.querySelector("#resume");
const status = document.querySelector("#status");
const feedback = document.querySelector("#feedback");
const shots = document.querySelector("#shots");
const newHits = document.querySelector("#new-hits");
const oldHits = document.querySelector("#old-hits");
const unknown = document.querySelector("#unknown");
const sunk = document.querySelector("#sunk");
const ownFleet = document.querySelector("#own-fleet");
const targetGrid = document.querySelector("#target");
const target = makeBoard(targetGrid, fire);
const ownGrid = document.querySelector("#own");
const own = makeBoard(ownGrid);
const placementGrid = document.querySelector("#placement");
const placement = makeBoard(placementGrid, placeSelected);
let game;
let busy = false;
let showingStart = true;
let screen = "START";
let selectedShip;
let draggedShip;
let vertical = false;
let shipCards = [];
let listedNation;
let pulseGameId;
let lastPulsedShot;
let fleetGameId;
let renderedOwnBoard;
let renderedSunkCount;
let audioContext;
let shotSounds;

ownGrid.addEventListener("animationend", event => {
    if (event.animationName === "own-board-shot-pulse" || event.animationName === "own-board-shot-steady") {
        event.target.classList.remove("shot-pulse", "shot-pulse-hit");
    }
});

function coordinate(cell) {
    return rows[Math.floor(cell / size)] + (cell % size + 1);
}

function makeBoard(board, onClick) {
    const fragment = document.createDocumentFragment();
    const cells = [];
    function axis(label) {
        const element = document.createElement("span");
        element.className = "axis";
        element.textContent = label;
        fragment.append(element);
    }
    axis("");
    for (let i = 1; i <= size; i++) axis(i);

    for (let i = 0; i < size * size; i++) {
        const row = Math.floor(i / size);
        if (i % size === 0) axis(rows[row]);
        const element = document.createElement(onClick ? "button" : "span");
        element.className = (row + i % size) % 2 === 0 ? "cell light" : "cell";
        if (onClick) {
            element.type = "button";
            element.dataset.cell = i;
            element.addEventListener("click", () => onClick(i));
        }
        fragment.append(element);
        cells.push(element);
    }
    board.append(fragment);
    return cells;
}

function drawBoard(cells, board, interactive, canShoot) {
    const labels = {".": "Unbeschossen", s: "Schiff", h: "Schiffstreffer", m: "Wassertreffer"};
    cells.forEach((cell, i) => {
        const state = board[i];
        cell.classList.toggle("ship", state === "s");
        cell.classList.toggle("hit", state === "h");
        cell.setAttribute("aria-label", coordinate(i) + " · " + labels[state]);
        if (interactive) cell.setAttribute("aria-disabled", String(!canShoot));
        if (cell.dataset.state === state) return;
        cell.dataset.state = state;
        cell.replaceChildren();
        if (state === "h" || state === "m") {
            const mark = document.createElement("span");
            mark.className = state === "h" ? "fire" : "dot";
            mark.setAttribute("aria-hidden", "true");
            if (state === "h") mark.textContent = "🔥";
            cell.append(mark);
        }
    });
}

function pulseComputerShot() {
    if (pulseGameId !== game.id) {
        pulseGameId = game.id;
        lastPulsedShot = undefined;
        ownGrid.classList.remove("shot-pulse", "shot-pulse-hit");
        own.forEach(cell => cell.classList.remove("shot-pulse", "shot-pulse-hit"));
    }
    const shot = game.lastShot;
    if (shot?.shooter !== "COMPUTER") return;
    const shotId = shot.number ?? shot.cell;
    if (shotId === lastPulsedShot) return;

    lastPulsedShot = shotId;
    const highlight = shot.cell === null ? ownGrid : own[shot.cell];
    if (highlight.classList.contains("shot-pulse")) {
        highlight.classList.remove("shot-pulse");
        // Commit the removal so consecutive shots on the same field restart the animation.
        void highlight.offsetWidth;
    }
    highlight.classList.toggle("shot-pulse-hit", shot.result !== "WATER");
    highlight.classList.add("shot-pulse");
}

function renderBoardShips(board, ships) {
    board.querySelectorAll(".board-ship").forEach(graphic => graphic.remove());
    board.classList.toggle("has-ship-graphics", ships.length > 0);
    for (const {type, start, direction} of ships) {
        const vertical = direction === "VERTICAL";
        const row = Math.floor(start / size) + 2;
        const column = start % size + 2;
        const width = vertical ? 1 : type.length;
        const height = vertical ? type.length : 1;
        const namespace = "http://www.w3.org/2000/svg";
        const graphic = document.createElementNS(namespace, "svg");
        graphic.classList.add("board-ship");
        graphic.setAttribute("aria-hidden", "true");
        graphic.setAttribute("viewBox", "0 0 " + width + " " + height);
        graphic.setAttribute("preserveAspectRatio", "none");
        graphic.style.gridRow = row + " / span " + height;
        graphic.style.gridColumn = column + " / span " + width;

        const image = document.createElementNS(namespace, "image");
        image.setAttribute("href", type.image);
        image.setAttribute("width", String(type.length));
        image.setAttribute("height", "1");
        image.setAttribute("preserveAspectRatio", "none");
        if (vertical) image.setAttribute("transform", "translate(1 0) rotate(90)");
        graphic.append(image);
        board.append(graphic);
    }
}

function fleetRow(type, visibleHits) {
    const row = document.createElement("li");
    row.className = "fleet-ship";

    const graphic = document.createElement("div");
    graphic.className = "fleet-ship-graphic";
    graphic.style.setProperty("--ship-length", type.length);
    graphic.setAttribute("aria-hidden", "true");
    const image = document.createElement("img");
    image.src = type.image;
    image.alt = "";
    const markers = document.createElement("div");
    markers.className = "fleet-ship-markers";
    graphic.append(image, markers);
    visibleHits.forEach((hit, offset) => {
        if (!hit) return;
        const marker = document.createElement("span");
        marker.className = "fleet-ship-hit";
        marker.style.gridColumn = String(offset + 1);
        markers.append(marker);
    });

    const details = document.createElement("div");
    details.className = "fleet-ship-details";
    const name = document.createElement("strong");
    name.textContent = type.name;
    const shipClass = document.createElement("small");
    shipClass.textContent = type.shipClass;
    const summary = document.createElement("small");
    const hits = visibleHits.filter(Boolean).length;
    let state = "Intakt";
    if (hits === type.length) state = "Versenkt";
    else if (hits > 0) state = hits + " getroffen";
    summary.textContent = type.length + " Felder · " + state;
    details.append(name, shipClass, summary);
    row.append(graphic, details);
    return row;
}

function renderFleets() {
    const ships = game.ownFleet ?? [];
    const newGame = fleetGameId !== game.id;
    if (newGame) {
        fleetGameId = game.id;
        renderBoardShips(ownGrid, ships);
    }
    if (newGame || renderedOwnBoard !== game.own) {
        renderedOwnBoard = game.own;
        ownFleet.replaceChildren(...ships.map(({type, start, direction}) => {
            const step = direction === "VERTICAL" ? size : 1;
            const visibleHits = Array.from({length: type.length}, (_, offset) => game.own[start + offset * step] === "h");
            return fleetRow(type, visibleHits);
        }));
    }
    if (newGame || renderedSunkCount !== game.sunk.length) {
        renderedSunkCount = game.sunk.length;
        renderBoardShips(targetGrid, game.sunk);
        unknown.textContent = game.unknown === 1 ? "1 unbekanntes Schiff" : game.unknown + " unbekannte Schiffe";
        sunk.replaceChildren(...game.sunk.map(({type}) => fleetRow(type, Array(type.length).fill(true))));
    }
}

function render() {
    const nextScreen = showingStart ? "START" : game.phase;
    startScreen.hidden = nextScreen !== "START";
    setupScreen.hidden = nextScreen !== "SETUP";
    gameScreen.hidden = nextScreen !== "PLAY";
    play.hidden = nextScreen !== "PLAY";
    play.disabled = busy;
    start.disabled = busy;
    caption.textContent = nextScreen === "PLAY"
        ? [playerName.value.trim(), game.nation, game.difficulty, "Gegen den Computer"].filter(Boolean).join(" · ")
        : "Gegen den Computer";
    if (nextScreen === "SETUP") renderSetup();
    if (nextScreen === "PLAY") renderGame();
    if (screen !== nextScreen) {
        if (nextScreen === "PLAY") gameScreen.prepend(gameHeader);
        else startScreen.before(gameHeader);
        const headings = {START: "#start-heading", SETUP: "#setup-heading", PLAY: "#target-heading"};
        document.querySelector(headings[nextScreen]).focus();
        screen = nextScreen;
    }
}

function renderGame() {
    const canShoot = !busy && !game.winner && game.turn === "PLAYER";
    drawBoard(target, game.target, true, canShoot);
    drawBoard(own, game.own, false, false);
    renderFleets();
    pulseComputerShot();
    resume.hidden = busy || Boolean(game.winner) || game.turn !== "COMPUTER";

    const turnText = {
        PLAYER: game.winner ? "Sie haben gewonnen" : "Sie sind dran",
        COMPUTER: game.winner ? "Der Computer hat gewonnen" : "Der Computer ist dran"
    }[game.winner ?? game.turn];
    if (status.textContent !== turnText) status.textContent = turnText;
    feedback.textContent = "";
    shots.textContent = game.shots;
    newHits.textContent = game.newHits;
    oldHits.textContent = game.oldHits;
}

function renderSetup() {
    if (!nation.options.length) {
        nation.replaceChildren(...game.nations.map(option => new Option(option.label, option.id)));
    }
    nation.value = game.nation;
    nation.disabled = busy;
    orientation.disabled = busy;
    randomAll.disabled = busy;
    const allPlaced = game.placed === game.ships.length;
    randomRemaining.hidden = game.placed === 0 || allPlaced;
    randomRemaining.disabled = busy;
    orientation.textContent = "Ausrichtung: " + (vertical ? "senkrecht" : "waagerecht");
    finish.disabled = busy || !allPlaced;

    if (listedNation !== game.nation) {
        shipCards = game.ships.map(({type}, index) => makeShipCard(type, index));
        shipList.replaceChildren(...shipCards.map(({button}) => {
            const row = document.createElement("li");
            row.append(button);
            return row;
        }));
        listedNation = game.nation;
    }
    shipCards.forEach(({button, details}, index) => {
        const {placed, type} = game.ships[index];
        button.classList.toggle("is-placed", placed);
        button.draggable = !busy && !placed;
        button.setAttribute("aria-disabled", String(busy));
        if (placed) button.removeAttribute("aria-pressed");
        else button.setAttribute("aria-pressed", String(index === selectedShip));
        details.textContent = type.name + " · " + type.length + " Felder" +
            (placed ? " · Platziert, zum Entfernen klicken" : "");
    });
    drawBoard(placement, game.own, true, !busy && selectedShip !== undefined);
    setupFeedback.textContent = game.placed + " von " + game.ships.length + " Schiffen platziert.";
}

function makeShipCard(type, index) {
    const button = document.createElement("button");
    button.type = "button";
    button.className = "ship-card";
    const image = document.createElement("img");
    image.src = type.image;
    image.alt = "";
    image.draggable = false;
    const text = document.createElement("span");
    const name = document.createElement("strong");
    name.textContent = type.shipClass;
    const details = document.createElement("small");
    text.append(name, details);
    button.append(image, text);

    button.addEventListener("click", () => {
        if (busy) return;
        if (game.ships[index].placed) {
            run(async () => {
                game = await request("placement", "DELETE", String(index));
                selectedShip = index;
            });
        } else {
            selectShip(index);
        }
    });
    button.addEventListener("dragstart", event => {
        if (busy || game.ships[index].placed) {
            event.preventDefault();
            return;
        }
        event.dataTransfer.setData("text/plain", type.shipClass);
        event.dataTransfer.effectAllowed = "move";
        draggedShip = index;
        selectShip(index);
    });
    button.addEventListener("dragend", endDrag);
    return {button, details};
}

function selectShip(index) {
    selectedShip = index;
    renderSetup();
}

function isValidStart(cell) {
    const ship = game.ships[selectedShip];
    return ship?.[vertical ? "vertical" : "horizontal"][cell] === "1";
}

function placeSelected(cell) {
    if (busy || selectedShip === undefined) return;
    if (!isValidStart(cell)) {
        setupFeedback.textContent = "Dieses Schiff kann auf " + coordinate(cell) + " nicht platziert werden.";
        return;
    }
    run(async () => {
        const direction = vertical ? "VERTICAL" : "HORIZONTAL";
        game = await request("placement", "POST", selectedShip + " " + cell + " " + direction);
        selectedShip = undefined;
    });
}

function clearPreview() {
    placementGrid.querySelectorAll(".preview-valid, .preview-invalid").forEach(cell => {
        cell.classList.remove("preview-valid", "preview-invalid");
    });
}

function endDrag() {
    draggedShip = undefined;
    clearPreview();
}

placementGrid.addEventListener("dragover", event => {
    if (busy || draggedShip === undefined) return;
    clearPreview();
    const cell = event.target.closest("[data-cell]");
    if (!cell) return;
    event.preventDefault();
    event.dataTransfer.dropEffect = "move";
    const startCell = Number(cell.dataset.cell);
    const highlight = isValidStart(startCell) ? "preview-valid" : "preview-invalid";
    const axis = vertical ? Math.floor(startCell / size) : startCell % size;
    const length = Math.min(game.ships[draggedShip].type.length, size - axis);
    for (let i = 0; i < length; i++) {
        placement[startCell + i * (vertical ? size : 1)].classList.add(highlight);
    }
});
placementGrid.addEventListener("dragleave", event => {
    if (!placementGrid.contains(event.relatedTarget)) clearPreview();
});
placementGrid.addEventListener("drop", event => {
    if (draggedShip === undefined) return;
    event.preventDefault();
    endDrag();
    const cell = event.target.closest("[data-cell]");
    if (cell) placeSelected(Number(cell.dataset.cell));
});

async function request(path, method = "POST", body) {
    const headers = game ? {"X-Game-Id": game.id} : {};
    const response = await fetch("/api/" + path, {method, headers, body});
    const result = await response.json();
    if (!response.ok) throw new Error(result.error);
    return result;
}

function prepareShotAudio() {
    if (!window.AudioContext) return;
    try {
        audioContext ??= new AudioContext();
    } catch {
        return;
    }
    if (audioContext.state !== "running") void audioContext.resume().catch(() => {});
    shotSounds ??= Promise.all(["water", "hit"].map(async sound => {
        const response = await fetch("/sounds/" + sound + ".wav");
        if (!response.ok) throw new Error("Sound unavailable");
        return audioContext.decodeAudioData(await response.arrayBuffer());
    })).catch(() => {
        shotSounds = undefined;
        return [];
    });
}

async function takeShot(path, body) {
    const state = await request(path, "POST", body);
    const soundIndex = state.lastShot.result === "WATER" ? 0 : 1;
    void shotSounds?.then(sounds => {
        if (!sounds[soundIndex] || audioContext.state !== "running") return;
        const source = audioContext.createBufferSource();
        source.buffer = sounds[soundIndex];
        source.connect(audioContext.destination);
        source.start();
    }).catch(() => {});
    return state;
}

function wait(milliseconds) {
    return new Promise(resolve => setTimeout(resolve, milliseconds));
}

async function continueAfterShot() {
    while (!game.winner && game.turn === "COMPUTER") {
        render();
        await wait(game.computerWaitMillis ?? 1500);
        game = await takeShot("computer");
    }
}

async function run(action) {
    if (busy) return;
    busy = true;
    render();
    let error;
    try {
        await action();
    } catch (failure) {
        error = failure.message;
        if (game) {
            try {
                game = await request("game", "GET");
            } catch {
                // Keep the last state visible if the local server is unavailable.
            }
        }
    } finally {
        busy = false;
        if (game?.phase === "SETUP" && game.ships[selectedShip]?.placed) selectedShip = undefined;
        render();
        if (error) {
            const messages = {START: startError, SETUP: setupFeedback, PLAY: feedback};
            messages[screen].textContent = error;
        }
    }
}

function fire(cell) {
    if (busy || !game || game.winner || game.turn !== "PLAYER") return;
    prepareShotAudio();
    run(async () => {
        game = await takeShot("shot", String(cell));
        await continueAfterShot();
    });
}

startForm.addEventListener("submit", event => {
    event.preventDefault();
    if (!playerName.value.trim()) {
        startError.textContent = "Geben Sie einen Spielernamen ein.";
        playerName.focus();
        return;
    }
    startError.textContent = "";
    prepareShotAudio();
    run(async () => {
        game = await request("setup", "POST", difficulty.value);
        selectedShip = undefined;
        vertical = false;
        showingStart = false;
    });
});
nation.addEventListener("change", () => {
    const selection = nation.value;
    run(async () => {
        game = await request("nation", "POST", selection);
        selectedShip = undefined;
    });
});
orientation.addEventListener("click", () => {
    vertical = !vertical;
    renderSetup();
});
for (const [button, mode] of [[randomAll, "ALL"], [randomRemaining, "REMAINING"]]) {
    button.addEventListener("click", () => run(async () => {
        game = await request("placement/random", "POST", mode);
        selectedShip = undefined;
    }));
}
finish.addEventListener("click", () => run(async () => {
    game = await request("game");
}));
play.addEventListener("click", () => {
    showingStart = true;
    render();
});
resume.addEventListener("click", () => {
    prepareShotAudio();
    run(async () => {
        game = await request("game", "GET");
        await continueAfterShot();
    });
});
render();
