import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';
import { MemoryRouter } from 'react-router-dom';

let server;
let api;
let GameStatsTable;
let CheckoutSuggestion;
let GameCard;
let CreateGame;
const originalFetch = globalThis.fetch;
const originalLocalStorage = globalThis.localStorage;

before(async () => {
  server = await createServer({ server: { middlewareMode: true } });
  api = await server.ssrLoadModule('/src/api.js');
  ({ default: GameStatsTable } = await server.ssrLoadModule('/src/components/GameStatsTable.jsx'));
  ({ default: CheckoutSuggestion } = await server.ssrLoadModule('/src/components/CheckoutSuggestion.jsx'));
  ({ default: GameCard } = await server.ssrLoadModule('/src/components/GameCard.jsx'));
  ({ default: CreateGame } = await server.ssrLoadModule('/src/components/CreateGame.jsx'));
  globalThis.localStorage = { getItem: () => null };
});

after(async () => {
  globalThis.fetch = originalFetch;
  globalThis.localStorage = originalLocalStorage;
  await server?.close();
});

for (const startingPoints of [301, 501]) {
  for (const status of ['IN_PROGRESS', 'COMPLETED', 'ABANDONED']) {
    test(`${startingPoints}: average reloads directly for ${status}`, async () => {
      const gameId = startingPoints;
      const calls = [];
      globalThis.fetch = async (url) => {
        calls.push(url);
        assert.equal(url, `/api/games/${gameId}`);
        const body = {
          gameId, gametype: String(startingPoints), startingPoints, status,
          winner: status === 'COMPLETED' ? 'bob' : null,
          turn: status === 'IN_PROGRESS' ? 'alice' : null,
          players: [{
            username: 'alice',
            points: startingPoints - 180,
            turns: 3,
          }],
        };
        return new Response(JSON.stringify(body));
      };

      const game = await api.getGameState({ gameId });
      assert.equal(game.startingPoints, startingPoints);
      assert.equal(calls.length, 1);
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

test('create, list and round commands use the standalone game contract', async () => {
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, method: options.method, body: options.body && JSON.parse(options.body) });
    return new Response(JSON.stringify({ gameId: 7, revision: 2 }));
  };
  await api.createGame({ gametype: '501', players: ['bob'] });
  await api.getGames({ gamemode: '301' });
  await api.startGame({ gameId: 7 });
  await api.playRound({ gameId: 7, legId: 9, revision: 1, username: 'alice', score: '' });
  await api.abandonGame({ gameId: 7 });
  await api.deleteGame({ gameId: 7 });
  assert.deepEqual(calls.map((call) => [call.url, call.method]), [
    ['/api/games', 'POST'], ['/api/games?gametype=301', 'GET'],
    ['/api/games/7/start', 'POST'], ['/api/games/7/rounds', 'POST'],
    ['/api/games/7/abandon', 'POST'], ['/api/games/7', 'DELETE'],
  ]);
  assert.deepEqual(calls[0].body, { gametype: '501', players: [{ username: 'bob' }] });
  assert.deepEqual(calls[3].body, { username: 'alice', score: '', legId: 9, revision: 1 });
});

test('stale round failures preserve conflict status and never retry automatically', async () => {
  let count = 0;
  globalThis.fetch = async () => {
    count++;
    return new Response(JSON.stringify({ message: 'Game state changed' }), { status: 409 });
  };
  await assert.rejects(api.playRound({ gameId: 7, legId: 9, revision: 1, username: 'alice', score: '' }),
    (error) => error.status === 409 && error.message === 'Game state changed');
  assert.equal(count, 1);
});

test('average is zero before the first turn', () => {
  const html = renderToStaticMarkup(createElement(GameStatsTable, {
    players: [{ username: 'alice', points: 501, turns: 0 }],
    startingPoints: 501,
  }));
  assert.match(html, /data-label="Average">0<\/td>/);
  assert.doesNotMatch(html, /NaN|Infinity/);
});

test('game cards link directly to games and distinguish abandonment from a win', () => {
  for (const status of ['PENDING', 'IN_PROGRESS', 'COMPLETED', 'ABANDONED']) {
    const winner = status === 'COMPLETED' ? 'bob' : null;
    const html = renderToStaticMarkup(createElement(MemoryRouter, null,
      createElement(GameCard, { game: { gameId: 7, gametype: '501',
        status, winner, createdAt: '2026-10-26T12:00:00Z',
        players: [{ username: 'alice' }, { username: 'bob' }] } })));
    assert.match(html, /href="\/games\/7"/);
    assert.match(html, /alice · bob/);
    assert.match(html, /26 Oct 2026/);
    if (winner) assert.match(html, /Winner: bob/);
    else assert.doesNotMatch(html, /Winner:/);
    if (status === 'ABANDONED') assert.match(html, /Abandoned/);
    assert.doesNotMatch(html, /session/i);
  }
});

test('creation form automatically includes the owner in the ten-player limit', () => {
  const payload = Buffer.from(JSON.stringify({ sub: 'alice' })).toString('base64url');
  globalThis.localStorage = { getItem: () => `header.${payload}.signature` };
  const html = renderToStaticMarkup(createElement(CreateGame, { onClose() {}, onCreated() {} }));
  assert.match(html, /Create game/);
  assert.match(html, /alice \(you\)/);
  assert.match(html, /1 \/ 10/);
  assert.doesNotMatch(html, /session/i);
  globalThis.localStorage = { getItem: () => null };
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
