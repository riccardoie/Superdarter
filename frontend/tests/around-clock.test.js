import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { createServer } from 'vite';
import { clockRoundPreview, clockTargetLabel } from '../src/aroundClock.js';
import { playableModes } from '../src/gameSetup.js';
import { isMatchWinningRound } from '../src/gameCompletion.js';

const game = {
  gametype: 'Around the Clock',
  status: 'IN_PROGRESS',
  turn: 'alice',
  legsToWin: 2,
  aroundClock: {
    phase: 'PLAY',
    currentPosition: 0,
    bullHistory: [],
    bullCandidates: [0, 1],
    bullAttempt: 1,
  },
  players: [
    {
      username: 'alice',
      position: 0,
      points: 1,
      turns: 0,
      legsWon: 0,
      boardHits: {},
    },
    {
      username: 'Guest',
      position: 1,
      points: 21,
      turns: 4,
      legsWon: 1,
      boardHits: {},
    },
  ],
  permissions: { canScore: true, canUndo: true },
};
const dart = (value, multiplier = 1) => ({ value, multiplier });
const at = (target) => ({
  ...game,
  players: [{ ...game.players[0], points: target }, game.players[1]],
});
let server;
let Scoreboard;
let Setup;
let CompletedLegs;
let BullOff;
let Notice;
const originalStorage = globalThis.localStorage;

before(async () => {
  globalThis.localStorage = {
    getItem: () =>
      `header.${Buffer.from(JSON.stringify({ sub: 'alice' })).toString('base64url')}.signature`,
  };
  server = await createServer({
    server: { middlewareMode: true, hmr: false, ws: false },
  });
  ({ default: Scoreboard } = await server.ssrLoadModule(
    '/src/components/AroundClockScoreboard.jsx'
  ));
  ({ default: Setup } = await server.ssrLoadModule(
    '/src/pages/GameSetupPage.jsx'
  ));
  ({ default: CompletedLegs } = await server.ssrLoadModule(
    '/src/components/CompletedLegs.jsx'
  ));
  ({ default: BullOff } = await server.ssrLoadModule(
    '/src/components/MikkeBullOff.jsx'
  ));
  ({ default: Notice } = await server.ssrLoadModule(
    '/src/components/MikkeRedemptionNotice.jsx'
  ));
});
after(async () => {
  globalThis.localStorage = originalStorage;
  await server?.close();
});

test('Around the Clock is offered as a standalone game without finishing variants', () => {
  assert.deepEqual(playableModes([{ gamemode: 'Around the Clock' }]), [
    { gamemode: 'Around the Clock', finishes: {} },
  ]);
});

test('only the required number advances and multipliers skip the correct numbers', () => {
  assert.equal(clockRoundPreview(game, [dart(1, 2)]).target, 3);
  assert.equal(clockRoundPreview(game, [dart(1, 3)]).target, 4);
  assert.equal(
    clockRoundPreview(game, [dart(2, 3), dart(25, 2), dart(1)]).target,
    2
  );
  assert.equal(
    clockRoundPreview(game, [dart(1, 3), dart(4, 2), dart(6)]).target,
    7
  );
  assert.equal(clockRoundPreview(game, [dart(''), dart(0)]).target, 1);
});

test('all numbered multipliers stop at bull and can never win by themselves', () => {
  for (let target = 1; target <= 20; target++) {
    for (let multiplier = 1; multiplier <= 3; multiplier++) {
      const preview = clockRoundPreview(at(target), [dart(target, multiplier)]);
      assert.equal(preview.target, Math.min(21, target + multiplier));
      assert.equal(preview.won, false);
    }
  }
  assert.equal(clockRoundPreview(at(18), [dart(18, 3)]).target, 21);
  assert.equal(clockTargetLabel(21), 'Bull');
  assert.equal(clockTargetLabel(0), 'Finished');
});

test('either bull can finish only after numbers, with later darts ignored', () => {
  for (const multiplier of [1, 2]) {
    assert.equal(clockRoundPreview(at(21), [dart(20, 3)]).won, false);
    assert.equal(
      clockRoundPreview(at(18), [dart(18, 3), dart(25, multiplier), dart(1)])
        .won,
      true
    );
    assert.equal(clockRoundPreview(game, [dart(25, multiplier)]).won, false);
  }
  assert.equal(clockRoundPreview(at(21), [dart(25, 3)]), null);
  assert.equal(
    clockRoundPreview({ ...game, turn: 'missing' }, [dart(25)]),
    null
  );
});

test('finishing throws request confirmation during play and redemption, not bull-offs', () => {
  for (const phase of ['PLAY', 'REDEMPTION']) {
    const finishing = {
      ...at(15),
      aroundClock: { ...game.aroundClock, phase },
    };
    assert.equal(
      isMatchWinningRound(finishing, [dart(15, 3), dart(18, 3), dart(25)]),
      true
    );
    assert.equal(
      isMatchWinningRound(finishing, [dart(15, 3), dart(18, 3)]),
      false
    );
  }
  assert.equal(
    isMatchWinningRound({ ...at(21), aroundClock: { phase: 'BULL_OFF' } }, [
      dart(25),
    ]),
    false
  );
  assert.equal(
    isMatchWinningRound({ ...at(21), status: 'COMPLETED' }, [dart(25)]),
    false
  );
});

test('setup shows clock rules, leg count and redemption without countdown finishing rules', () => {
  const html = renderToStaticMarkup(
    createElement(
      MemoryRouter,
      {
        initialEntries: [
          {
            pathname: '/games/new',
            state: {
              draft: {
                gametype: 'Around the Clock',
                players: [],
                guests: [],
                finishes: {},
              },
            },
          },
        ],
      },
      createElement(Setup)
    )
  );
  assert.match(html, /Hit 1–20 in order/);
  assert.match(html, /separate bull dart/);
  assert.match(html, /legs-to-win/);
  assert.match(html, /redemption/);
  assert.doesNotMatch(html, /Finishing rule|Mikke Mus mode|Reach exactly zero/);
});

test('clock scoreboard shows player targets and preview, without countdown averages', () => {
  const html = renderToStaticMarkup(
    createElement(Scoreboard, { game, slots: [dart(1, 2)] })
  );
  assert.match(html, /aria-label="alice: 3" aria-live="polite">3<\/td>/);
  assert.doesNotMatch(html, /Next target:|Ready to finish|player-hint/);
  assert.match(html, /Guest: Bull/);
  assert.match(html, /More stats/);
  assert.doesNotMatch(html, /Remaining points|First 9|Avg:|Checkout/);
});

test('target cells update live through bull and finish, and reset when input changes', () => {
  const render = (slots) =>
    renderToStaticMarkup(createElement(Scoreboard, { game: at(18), slots }));
  assert.match(
    render([dart(18, 3)]),
    /aria-label="alice: Bull" aria-live="polite">Bull<\/td>/
  );
  assert.match(
    render([dart(18, 3), dart(25)]),
    /aria-label="alice: Finished" aria-live="polite">Finished<\/td>/
  );
  assert.match(render([]), /aria-label="alice: 18"/);
  assert.match(render([dart(19, 3)]), /aria-label="alice: 18"/);
  assert.match(render([dart(99)]), /aria-label="alice: 18"/);
  assert.match(render([dart(18, 3)]), /aria-label="Guest: Bull"/);
});

test('bull-offs and completed games display saved targets, not turn previews', () => {
  for (const phase of ['BULL_OFF', 'BULL_DECISION', 'FINISHED']) {
    const html = renderToStaticMarkup(
      createElement(Scoreboard, {
        game: { ...game, aroundClock: { phase } },
        slots: [dart(1, 3)],
      })
    );
    assert.match(html, /aria-label="alice: 1"/);
  }
});

test('completed clock legs retain each player’s target and heat maps', () => {
  const completed = {
    ...game,
    legs: [
      {
        legId: 1,
        number: 1,
        status: 'COMPLETED',
        winner: 'alice',
        players: [{ ...game.players[0], points: 0 }, game.players[1]],
      },
    ],
  };
  const html = renderToStaticMarkup(
    createElement(CompletedLegs, { game: completed })
  );
  assert.match(html, /Leg 1 — alice wins/);
  assert.match(html, /alice: Finished/);
  assert.match(html, /Guest: Bull/);
  assert.match(html, /More stats/);
  assert.doesNotMatch(html, /Remaining points|First 9/);
});

test('clock redemption and tiebreakers use the existing animations and bull-off controls', () => {
  const redemption = {
    ...game,
    aroundClock: { ...game.aroundClock, phase: 'REDEMPTION' },
  };
  assert.match(
    renderToStaticMarkup(createElement(Notice, { game: redemption })),
    /Redemption!/
  );
  const tied = {
    ...game,
    aroundClock: { ...game.aroundClock, phase: 'BULL_OFF' },
  };
  assert.match(
    renderToStaticMarkup(createElement(Notice, { game: tied })),
    /TIEBREAKER!/
  );
  assert.match(
    renderToStaticMarkup(createElement(BullOff, { game: tied })),
    /Outer bull/
  );
  const decision = {
    ...tied,
    aroundClock: { ...tied.aroundClock, phase: 'BULL_DECISION' },
  };
  assert.match(
    renderToStaticMarkup(createElement(BullOff, { game: decision })),
    /Guest is closest/
  );
});
