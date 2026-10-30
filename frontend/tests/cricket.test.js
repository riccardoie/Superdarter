import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { createServer } from 'vite';
import { CRICKET_TARGETS, previewCricket } from '../src/cricket.js';
import { playableModes } from '../src/gameSetup.js';
import { isMatchWinningRound } from '../src/gameCompletion.js';

const player = (points = 0, cricketMarks = {}) => ({
  username: 'alice',
  points,
  cricketMarks,
});
const slots = (...darts) =>
  darts.map(([value, multiplier]) => ({ value, multiplier }));
const closed = () =>
  Object.fromEntries(CRICKET_TARGETS.map((number) => [number, 3]));

test('cricket is offered alongside countdown games without finishing rules', () => {
  assert.deepEqual(
    playableModes([
      { gamemode: '301' },
      { gamemode: 'Cricket' },
      { gamemode: 'Killer' },
    ]),
    [
      { gamemode: '301', finishes: { double: '301' } },
      { gamemode: 'Cricket', finishes: {} },
    ]
  );
  assert.deepEqual(playableModes([]), []);
});

test('cricket preview adds marks and only scores overflow against open opponents', () => {
  const original = player(10, { 20: 2 });
  const result = previewCricket(original, [player()], slots([20, 3]));
  assert.equal(result.points, 50);
  assert.equal(result.marks[20], 3);
  assert.equal(original.cricketMarks[20], 2);
  assert.equal(
    previewCricket(original, [player(0, { 20: 3 })], slots([20, 3])).points,
    10
  );
  assert.equal(
    previewCricket(original, [player(0, { 20: 3 }), player()], slots([20, 3]))
      .points,
    50
  );
});

test('bull doubles add two marks, while non-targets and misses do not affect scoring', () => {
  const result = previewCricket(
    player(),
    [player()],
    slots([25, 2], [25, 2], [14, 3])
  );
  assert.equal(result.points, 25);
  assert.equal(result.marks[25], 3);
  assert.equal(result.marks[14], undefined);
  assert.equal(previewCricket(player(), [], slots(['', 1], [0, 1])).points, 0);
  assert.equal(previewCricket(player(), [], slots([25, 3])), null);
});

test('all targets closed is not a win when behind; tying is enough and later darts are ignored', () => {
  const marks = closed();
  marks[25] = 1;
  const behind = previewCricket(player(0, marks), [player(25)], slots([25, 2]));
  assert.equal(behind.won, false);
  const caughtUp = previewCricket(
    player(0, behind.marks),
    [player(25)],
    slots([25, 1], [20, 3])
  );
  assert.equal(caughtUp.won, true);
  assert.equal(caughtUp.points, 25);
  assert.equal(
    previewCricket(player(25, marks), [player(25)], slots([25, 2])).won,
    true
  );
});

test('cricket finish confirmation is triggered only on the deciding winning round', () => {
  const current = { ...player(0, { ...closed(), 25: 2 }), legsWon: 1 };
  const game = {
    gametype: 'Cricket',
    status: 'IN_PROGRESS',
    turn: 'alice',
    players: [current],
    legsToWin: 2,
  };
  assert.equal(isMatchWinningRound(game, slots([25, 1])), true);
  assert.equal(
    isMatchWinningRound({ ...game, legsToWin: 3 }, slots([25, 1])),
    false
  );
  assert.equal(
    isMatchWinningRound(
      { ...game, players: [current, { ...player(25), username: 'bob' }] },
      slots([25, 1])
    ),
    false
  );
});

let server;
let CricketScoreboard;
let GameSetupPage;
let CompletedLegs;
const originalStorage = globalThis.localStorage;
before(async () => {
  globalThis.localStorage = {
    getItem: () =>
      `header.${Buffer.from(JSON.stringify({ sub: 'alice' })).toString('base64url')}.signature`,
  };
  server = await createServer({
    server: { middlewareMode: true, hmr: false, ws: false },
  });
  ({ default: CricketScoreboard } = await server.ssrLoadModule(
    '/src/components/CricketScoreboard.jsx'
  ));
  ({ default: GameSetupPage } = await server.ssrLoadModule(
    '/src/pages/GameSetupPage.jsx'
  ));
  ({ default: CompletedLegs } = await server.ssrLoadModule(
    '/src/components/CompletedLegs.jsx'
  ));
});
after(async () => {
  globalThis.localStorage = originalStorage;
  await server?.close();
});

test('cricket scoreboard displays every target, marks, points and active player', () => {
  const game = {
    turn: 'alice',
    legsToWin: 2,
    players: [
      { ...player(40, { 20: 3, 19: 2, 25: 1 }), legsWon: 1 },
      { ...player(20, { 20: 3 }), username: 'Guest' },
    ],
  };
  const html = renderToStaticMarkup(createElement(CricketScoreboard, { game }));
  assert.match(html, /alice: 3 of 3 marks on 20/);
  assert.match(html, /alice: 2 of 3 marks on 19/);
  assert.match(html, /alice: 1 of 3 marks on 25/);
  assert.match(html, /alice: 0 of 3 marks on 15/);
  assert.match(html, /Bull/);
  assert.match(html, /Closed/);
  assert.match(html, /Points/);
  assert.match(html, /<td>40<\/td>/);
  assert.match(html, /Legs won/);
  assert.doesNotMatch(html, /Checkout|Average|Remaining|Now throwing/);
});

test('cricket setup explains closing and scoring without offering single or double out', () => {
  const html = renderToStaticMarkup(
    createElement(
      MemoryRouter,
      {
        initialEntries: [
          {
            pathname: '/games/new',
            state: {
              draft: {
                gametype: 'Cricket',
                finishes: {},
                players: [],
                guests: ['Guest'],
              },
            },
          },
        ],
      },
      createElement(GameSetupPage)
    )
  );
  assert.match(html, /Ready to play Cricket/);
  assert.match(html, /three marks each/);
  assert.match(html, /Start game/);
  assert.doesNotMatch(
    html,
    /Finishing rule|Single out|Double out|Reach exactly zero/
  );
});

test('completed cricket legs retain their marks instead of showing countdown statistics', () => {
  const game = {
    gametype: 'Cricket',
    turn: 'alice',
    legsToWin: 2,
    legs: [
      {
        legId: 1,
        number: 1,
        status: 'COMPLETED',
        winner: 'alice',
        players: [player(0, closed())],
      },
    ],
  };
  const html = renderToStaticMarkup(createElement(CompletedLegs, { game }));
  assert.match(html, /Cricket scoreboard/);
  assert.match(html, /alice: 3 of 3 marks on 25/);
  assert.doesNotMatch(html, /First 9|Checkout|Average/);
});
