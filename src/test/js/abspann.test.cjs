const assert = require('node:assert/strict');
const test = require('node:test');

class Element {
    constructor() {
        this.children = [];
        this.events = {};
        this.attributes = {};
        this.dataset = {};
        this.style = {setProperty: (name, value) => { this.style[name] = value; }};
        this.open = false;
    }
    addEventListener(name, handler) { this.events[name] = handler; }
    setAttribute(name, value) { this.attributes[name] = value; }
    toggleAttribute(name, value) {
        if (value) this.setAttribute(name, '');
        else delete this.attributes[name];
    }
    append(...nodes) { this.children.push(...nodes); }
    replaceChildren(...nodes) { this.children = nodes; }
    showModal() { this.open = true; }
    close() { this.open = false; this.events.close(); }
}

test("Abspann zeigt das Ergebnis und wiederholt Treffer und Siegermusik", async t => {
    const dialog = new Element();
    const replay = new Element();
    const heading = new Element();
    const fleet = new Element();
    const elements = {'#result-dialog': dialog, '#result-replay': replay, '#result-heading': heading, '#result-fleet': fleet};
    let starts = 0;
    let stops = 0;
    let tonVorbereitet = 0;
    const audio = {
        state: 'running', currentTime: 0, destination: {},
        createBufferSource: () => ({
            addEventListener() {}, connect(node) { return node; }, disconnect() {},
            start() { starts++; }, stop() { stops++; }
        }),
        createGain: () => ({gain: {setTargetAtTime() {}}, connect() {}, disconnect() {}})
    };
    const sounds = [];
    const document = {querySelector: selector => elements[selector], createElement: () => new Element()};
    const vorherigesDokument = globalThis.document;
    globalThis.document = document;
    t.after(() => {
        if (vorherigesDokument === undefined) delete globalThis.document;
        else globalThis.document = vorherigesDokument;
    });
    const {erstelleAbspann} = await import("../../main/resources/web/abspann.js");
    const abspann = erstelleAbspann({
        spielfeldgroesse: 10, getAudioContext: () => audio,
        bereiteTonVor: () => tonVorbereitet++,
        ladeTon: async name => { sounds.push(name); return {}; }
    });
    const ship = (start, name) => ({start, direction: 'HORIZONTAL', type: {name, length: 2, image: 'test.svg'}});
    const spielstand = {
        id: '1', finished: true, winner: 'PLAYER', ownFleet: [ship(20, 'Eigenes Schiff')],
        sunk: [ship(8, 'Versenktes Schiff')], revealed: [ship(30, 'Überlebendes Schiff')],
        replayHits: [{own: false, cell: 8, timeMillis: 500, sunk: false},
            {own: false, cell: 9, timeMillis: 500, sunk: true}]
    };
    abspann.zeigeAbspann(spielstand, 'PLAY');
    await new Promise(setImmediate);
    assert.equal(dialog.open, true);
    assert.equal(heading.textContent, 'Gewonnen');
    assert.equal(fleet.children.length, 3);
    assert.ok('data-sunk' in fleet.children[0].attributes);
    assert.equal(fleet.children[0].children[0].style['--sink-time'], '15700ms');
    const markers = fleet.children[0].children[0].children[1].children;
    assert.deepEqual(markers.map(marker => marker.style['--hit-time']), ['900ms', '15000ms']);
    assert.equal(starts, 1);
    assert.equal(replay.hidden, false);
    const original = fleet.children[0];
    abspann.zeigeAbspann(spielstand, 'PLAY');
    assert.equal(fleet.children[0], original);
    replay.events.click();
    assert.notEqual(fleet.children[0], original);
    await new Promise(setImmediate);
    assert.equal(tonVorbereitet, 1);
    assert.equal(starts, 2);
    assert.equal(stops, 1);
    abspann.zeigeAbspann(spielstand, 'START');
    assert.equal(dialog.open, false);
    assert.equal(stops, 2);
    abspann.zeigeAbspann({...spielstand, id: '2', winner: 'COMPUTER'}, 'PLAY');
    assert.equal(heading.textContent, 'Verloren');
    abspann.zeigeAbspann({...spielstand, id: '2'}, 'START');
    abspann.zeigeAbspann({...spielstand, id: '3', winner: null, ownFleet: [], sunk: [], revealed: [], replayHits: []}, 'PLAY');
    assert.equal(heading.textContent, 'Unentschieden');
    assert.equal(replay.hidden, true);
    assert.deepEqual(sounds, ['result-win', 'result-win']);
});
