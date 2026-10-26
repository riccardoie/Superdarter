import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';

let server;
let api;
let GameStatsTable;
let CheckoutSuggestion;
const originalFetch = globalThis.fetch;
const originalLocalStorage = globalThis.localStorage;

before(async () => {
  server = await createServer({ server: { middlewareMode: true } });
  api = await server.ssrLoadModule('/src/api.js');
  ({ default: GameStatsTable } = await server.ssrLoadModule('/src/components/GameStatsTable.jsx'));
  ({ default: CheckoutSuggestion } = await server.ssrLoadModule('/src/components/CheckoutSuggestion.jsx'));
  globalThis.localStorage = { getItem: () => null };
});

after(async () => {
  globalThis.fetch = originalFetch;
  globalThis.localStorage = originalLocalStorage;
  await server?.close();
});

for (const startingPoints of [301, 501]) {
  for (const cached of [false, true]) {
    test(`${startingPoints}: average is correct ${cached ? 'during play' : 'after reload'}`, async () => {
      const gameId = startingPoints + (cached ? 1000 : 0);
      const calls = [];
      globalThis.fetch = async (url) => {
        calls.push(url);
        let body;
        if (url.endsWith('/stats')) {
          body = [{
            userName: 'alice',
            points: startingPoints - 180,
            turns: 3,
            startingPoints,
          }];
        } else if (url.endsWith('/finished-games')) {
          body = [];
        } else if (url.includes('?gametype=')) {
          body = { gameId, gametype: String(startingPoints), turn: 'alice', isFinished: false };
        } else {
          throw new Error(`Unexpected request: ${url}`);
        }
        return new Response(JSON.stringify(body));
      };

      if (cached) await api.startGame({ sessionId: 1, gameType: String(startingPoints) });
      const game = await api.getGameState({ sessionId: 1, gameId });
      assert.equal(game.startingPoints, startingPoints);
      assert.equal(calls.some((url) => url.endsWith('/finished-games')), !cached);
      const html = renderToStaticMarkup(createElement(GameStatsTable, {
        players: game.players,
        winner: game.winner,
        startingPoints: game.startingPoints,
      }));
      assert.match(html, /data-label="Average">60\.0<\/td>/);
      assert.doesNotMatch(html, /NaN/);
    });
  }
}

test('average is zero before the first turn', () => {
  const html = renderToStaticMarkup(createElement(GameStatsTable, {
    players: [{ username: 'alice', points: 501, turns: 0 }],
    startingPoints: 501,
  }));
  assert.match(html, /data-label="Average">0<\/td>/);
  assert.doesNotMatch(html, /NaN|Infinity/);
});

test('checkout suggestion renders compact routes and hides unavailable checkouts', () => {
  const render = (points, slots) => renderToStaticMarkup(createElement(CheckoutSuggestion, { points, slots }));
  const empty = { value: '', multiplier: 1 };
  assert.match(render(60, [empty, empty, empty]), />20 → d20<\/span>/);
  assert.match(render(100, [empty, empty, empty]), />t20 → d20<\/span>/);
  assert.match(render(50, [empty, empty, empty]), />d25<\/span>/);
  assert.match(render(60, [empty, empty, empty]), /role="status"/);
  assert.equal(render(169, [empty, empty, empty]), '');
  assert.equal(render(40, [{ value: 20, multiplier: 2 }, empty, empty]), '');
  assert.equal(render(40, [{ value: 20, multiplier: 3 }, empty, empty]), '');
  assert.equal(render(301, Array(3).fill({ value: 20, multiplier: 3 })), '');
  assert.equal(render(undefined, [empty, empty, empty]), '');
});
