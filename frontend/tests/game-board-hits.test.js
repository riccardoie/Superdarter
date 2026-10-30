import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';
import { sumBoardHits, boardHitStats } from '../src/boardHits.js';
import { matchStats } from '../src/gameStats.js';

const hits = (singles = 0, doubles = 0, triples = 0) => ({
  total: singles + doubles + triples,
  singles,
  doubles,
  triples,
});
const alice = {
  username: 'alice',
  turns: 2,
  points: 0,
  highestScore: 180,
  bullseyes: 0,
  boardHits: { 20: hits(1, 1) },
};
const guest = {
  username: 'Guest',
  guest: true,
  turns: 1,
  points: 301,
  highestScore: 0,
  bullseyes: 1,
  boardHits: { 25: hits(0, 1) },
};
const game = {
  gametype: '301',
  startingPoints: 301,
  legsToWin: 2,
  players: [
    { ...guest, boardHits: { 20: hits(0, 0, 2) } },
    { ...alice, boardHits: { 20: hits(0, 0, 1) } },
  ],
  legs: [
    {
      legId: 1,
      number: 1,
      status: 'COMPLETED',
      winner: 'alice',
      players: [alice, guest],
    },
    {
      legId: 2,
      number: 2,
      status: 'IN_PROGRESS',
      players: [
        { ...guest, boardHits: { 20: hits(0, 0, 2) } },
        { ...alice, boardHits: { 20: hits(0, 0, 1) } },
      ],
    },
  ],
};

test('overall game heat maps sum only that player’s legs, without duplicating the current leg', () => {
  const totals = matchStats(game);
  const aliceTotal = totals.find((entry) => entry.username === 'alice');
  const guestTotal = totals.find((entry) => entry.username === 'Guest');
  assert.deepEqual(aliceTotal.boardHits[20], hits(1, 1, 1));
  assert.deepEqual(guestTotal.boardHits[20], hits(0, 0, 2));
  assert.deepEqual(guestTotal.boardHits[25], hits(0, 1));
  assert.equal(boardHitStats(aliceTotal.boardHits).total, 3);
  assert.equal(boardHitStats(guestTotal.boardHits).total, 3);
  assert.deepEqual(alice.boardHits[20], hits(1, 1));
});

test('game hit aggregation handles empty, historic and single-leg fallback data', () => {
  assert.equal(boardHitStats(sumBoardHits([undefined, null, {}])).total, 0);
  assert.equal(Object.keys(sumBoardHits([])).length, 21);
  assert.deepEqual(
    matchStats({ players: [alice] })[0].boardHits,
    alice.boardHits
  );
  assert.equal(
    boardHitStats(sumBoardHits([{ 0: hits(99), 21: hits(99) }])).total,
    0
  );
});

let server;
let GameStatsTable;
let CompletedLegs;
let GameBoardHits;
before(async () => {
  server = await createServer({
    server: { middlewareMode: true, hmr: false, ws: false },
  });
  ({ default: GameStatsTable } = await server.ssrLoadModule(
    '/src/components/GameStatsTable.jsx'
  ));
  ({ default: CompletedLegs } = await server.ssrLoadModule(
    '/src/components/CompletedLegs.jsx'
  ));
  ({ default: GameBoardHits } = await server.ssrLoadModule(
    '/src/components/GameBoardHits.jsx'
  ));
});
after(async () => {
  await server?.close();
});

test('each player gets a compact heat map and view switch inside their More stats', () => {
  const html = renderToStaticMarkup(
    createElement(GameStatsTable, {
      players: [alice, guest],
      startingPoints: 301,
      showTurns: false,
    })
  );
  assert.equal(
    (html.match(/class="profile-board-hits card board-hits-compact"/g) ?? [])
      .length,
    2
  );
  assert.equal((html.match(/aria-label="Board hits view"/g) ?? []).length, 2);
  assert.match(html, /<h3[^>]*>Board hits<\/h3>/);
  assert.match(html, /<strong>2<\/strong> darts hit the board/);
  assert.match(html, /<strong>1<\/strong> darts hit the board/);
  assert.match(html, /Single 20: 1 hits/);
  assert.match(html, /Double 25: 1 hits/);
});

test('completed leg details show only that leg while overall details show summed hits', () => {
  const legHtml = renderToStaticMarkup(createElement(CompletedLegs, { game }));
  assert.match(legHtml, /<strong>2<\/strong> darts hit the board/);
  assert.doesNotMatch(legHtml, /Triple 20: [12] hits/);
  const overall = renderToStaticMarkup(
    createElement(GameStatsTable, {
      players: matchStats(game),
      startingPoints: 301,
    })
  );
  assert.equal(
    (overall.match(/<strong>3<\/strong> darts hit the board/g) ?? []).length,
    2
  );
  assert.match(overall, /Triple 20: 1 hits/);
  assert.match(overall, /Triple 20: 2 hits/);
});

test('cricket player heat maps retain player labels and handle no recorded hits', () => {
  const html = renderToStaticMarkup(
    createElement(GameBoardHits, {
      players: [{ username: 'Guest', boardHits: {} }],
    })
  );
  assert.match(html, /More stats/);
  assert.match(html, /Guest/);
  assert.match(html, /<strong>0<\/strong> darts hit the board/);
  assert.doesNotMatch(html, /NaN|undefined/);
});
