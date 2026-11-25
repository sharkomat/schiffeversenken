const assert = require('node:assert/strict');
const test = require('node:test');

function element() {
    return {
        dataset: {},
        events: new Map(),
        options: [],
        style: {setProperty() {}},
        classList: {toggle() {}},
        addEventListener(type, handler) { this.events.set(type, handler); },
        append() {},
        before() {},
        focus() {},
        querySelector: element,
        querySelectorAll: () => [],
        removeAttribute() {},
        replaceChildren(...children) { this.children = children; },
        setAttribute() {}
    };
}

test('Gegen Computer spielen öffnet die Schiffsaufstellung', async t => {
    const selectors = new Map();
    const finde = selector => {
        if (!selectors.has(selector)) selectors.set(selector, element());
        return selectors.get(selector);
    };
    globalThis.document = {
        querySelector: finde,
        createElement: element,
        createDocumentFragment: element
    };
    globalThis.window = {};
    globalThis.Option = class {};
    t.after(() => {
        delete globalThis.document;
        delete globalThis.window;
        delete globalThis.Option;
    });
    t.mock.method(globalThis, 'setInterval', () => {});
    const anfragen = [];
    t.mock.method(globalThis, 'fetch', async (url, options) => {
        if (url === '/api/requests') return Response.json({requests: [], lifetimeMillis: 1000});
        anfragen.push([url, options.method, options.body]);
        return Response.json({
            id: 'test-partie', phase: 'SETUP', mode: 'COMPUTER', difficulty: 'Mittel',
            nation: 'DEUTSCHLAND', nations: [{label: 'Deutschland', id: 'DEUTSCHLAND'}],
            own: '.'.repeat(100), ownFleet: [], placed: 0,
            ships: [{placed: false, type: {name: 'Fregatte', shipClass: 'Testklasse', length: 3}}]
        });
    });

    await import('../../main/resources/web/game.js');
    assert.equal(finde('#start-screen').hidden, false);
    assert.equal(finde('#setup-screen').hidden, true);

    finde('#player-name').value = 'Testspieler';
    finde('#difficulty').value = 'MITTEL';
    finde('#start-form').events.get('submit')({submitter: finde('#start'), preventDefault() {}});
    await new Promise(setImmediate);

    assert.deepEqual(anfragen, [['/api/setup', 'POST', 'MITTEL']]);
    assert.equal(finde('#start-screen').hidden, true);
    assert.equal(finde('#setup-screen').hidden, false);
    assert.equal(finde('#ship-list').children.length, 1);
    assert.equal(finde('#setup-feedback').textContent, '0 von 1 Schiffen platziert.');
});
