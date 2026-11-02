import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { createServer } from 'vite';
import { playableModes, isMikkeMus } from '../src/gameSetup.js';
import { isMatchWinningRound } from '../src/gameCompletion.js';
import {
  needsAllocation,
  allocationChoices,
  pendingAllocationIndex,
  resolveMikkeAllocations,
} from '../src/mikkeAllocation.js';

let server;
let MikkeScoreboard;
let MikkeBullOff;
let MikkeAllocationDialog;
let MikkeRedemptionNotice;
let GameSetupPage;
let api;
const originalStorage = globalThis.localStorage;
const originalFetch = globalThis.fetch;
const players = [
  {
    username: 'alice',
    position: 0,
    mikkeMarks: { 20: 3, 14: 2, D: 1, T: 3, B: 0 },
    boardHits: {},
  },
  {
    username: 'Guest',
    guest: true,
    position: 1,
    mikkeMarks: {},
    boardHits: {},
  },
];
const game = {
  gametype: 'Mikke Mus Forced',
  players,
  turn: 'alice',
  legsToWin: 2,
  mikkeMus: {
    phase: 'PLAY',
    currentPosition: 0,
    bullHistory: [],
    bullCandidates: [0, 1],
  },
  permissions: { canScore: true, canUndo: true },
};

before(async () => {
  globalThis.localStorage = {
    getItem: () =>
      `header.${Buffer.from(JSON.stringify({ sub: 'alice' })).toString('base64url')}.signature`,
  };
  server = await createServer({
    server: { middlewareMode: true, hmr: false, ws: false },
  });
  ({ default: MikkeScoreboard } = await server.ssrLoadModule(
    '/src/components/MikkeScoreboard.jsx'
  ));
  ({ default: MikkeBullOff } = await server.ssrLoadModule(
    '/src/components/MikkeBullOff.jsx'
  ));
  ({ default: GameSetupPage } = await server.ssrLoadModule(
    '/src/pages/GameSetupPage.jsx'
  ));
  api = await server.ssrLoadModule('/src/api.js');
  ({ default: MikkeRedemptionNotice } = await server.ssrLoadModule(
    '/src/components/MikkeRedemptionNotice.jsx'
  ));
  ({ default: MikkeAllocationDialog } = await server.ssrLoadModule(
    '/src/components/MikkeAllocationDialog.jsx'
  ));
});
after(async () => {
  globalThis.localStorage = originalStorage;
  globalThis.fetch = originalFetch;
  await server?.close();
});

test('Mikke Mus groups available variants into one game choice', () => {
  const modes = playableModes(
    ['Mikke Mus Forced', 'Mikke Mus Relaxed', 'Killer'].map((gamemode) => ({
      gamemode,
    }))
  );
  assert.deepEqual(modes, [
    {
      gamemode: 'Mikke Mus',
      finishes: {},
      variants: { Forced: 'Mikke Mus Forced', Relaxed: 'Mikke Mus Relaxed' },
    },
  ]);
  assert.equal(isMikkeMus('Mikke Mus Forced'), true);
  assert.equal(isMikkeMus('Cricket'), false);
  assert.equal(isMikkeMus('Mikke Mus'), true);
  assert.deepEqual(
    playableModes([{ gamemode: 'Mikke Mus Relaxed' }])[0].variants,
    { Relaxed: 'Mikke Mus Relaxed' }
  );
});

test('Mikke Mus scoreboard includes 14, doubles, trebles and bull with no countdown points', () => {
  const html = renderToStaticMarkup(createElement(MikkeScoreboard, { game }));
  for (const label of ['14', 'Doubles', 'Trebles', 'Bull'])
    assert.ok(html.includes(label));
  assert.match(html, /alice: 2 of 3 marks on 14/);
  assert.match(html, /alice: 1 of 3 marks on Doubles/);
  assert.doesNotMatch(html, /Required number:/);
  assert.doesNotMatch(html, /Bull unlocks after/);
  assert.doesNotMatch(html.split('cricket-table-wrap')[0], /alice|Guest/);
  assert.match(html, /More stats/);
  assert.doesNotMatch(html, /Remaining points|First 9|Now throwing/);
});

test('setup explains the forced bull lock and redemption without a finishing rule', () => {
  const html = renderToStaticMarkup(
    createElement(
      MemoryRouter,
      {
        initialEntries: [
          {
            pathname: '/games/new',
            state: {
              draft: {
                gametype: 'Mikke Mus',
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
  assert.match(html, /Bull unlocks after numbers, doubles and trebles/);
  assert.match(html, /redemption/);
  assert.match(html, /Mikke Mus mode/);
  assert.match(html, /checked="" value="Forced"/);
  assert.match(html, /value="Relaxed"/);
  assert.doesNotMatch(html, /Finishing rule|Reach exactly zero/);
});

test('allocation popups offer exactly one number or category choice', () => {
  for (const [value, multiplier, category] of [
    [20, 2, 'DOUBLE'],
    [19, 3, 'TREBLE'],
  ]) {
    const slot = { value, multiplier, allocation: null };
    assert.equal(needsAllocation(slot), true);
    const choices = allocationChoices(slot);
    assert.deepEqual(
      choices.map((choice) => choice.value),
      [value === 25 ? 'BULL' : 'NUMBER', category]
    );
    const html = renderToStaticMarkup(
      createElement(MikkeAllocationDialog, { slot, index: 1 })
    );
    assert.match(html, /role="dialog" aria-modal="true"/);
    assert.match(html, /How should dart 2 count/);
    assert.match(html, /Decide later/);
    assert.doesNotMatch(html, /<select/);
  }
  for (const slot of [
    { value: '', multiplier: 2 },
    { value: 0, multiplier: 2 },
    { value: 20, multiplier: 1 },
    { value: 25, multiplier: 1 },
    { value: 25, multiplier: 2 },
    { value: 25, multiplier: 3 },
    { value: 99, multiplier: 2 },
  ]) {
    assert.equal(needsAllocation(slot), false);
  }
});

test('unresolved choices block submission in dart order until each is assigned', () => {
  const slots = [
    { value: 20, multiplier: 2, allocation: null },
    { value: 19, multiplier: 3, allocation: null },
    { value: '', multiplier: 1 },
  ];
  assert.equal(pendingAllocationIndex(slots), 0);
  // Dismissing a popup leaves the same choice pending.
  assert.equal(pendingAllocationIndex(slots), 0);
  slots[0].allocation = 'DOUBLE';
  assert.equal(pendingAllocationIndex(slots), 1);
  slots[1].allocation = 'NUMBER';
  assert.equal(pendingAllocationIndex(slots), -1);
  // Editing a dart invalidates its old category decision.
  slots[0] = { value: 18, multiplier: 2, allocation: null };
  assert.equal(pendingAllocationIndex(slots), 0);
});

test('closed targets automatically allocate doubles and trebles without a popup', () => {
  const closed = {
    ...game,
    gametype: 'Mikke Mus Relaxed',
    players: [
      { ...players[0], mikkeMarks: { ...players[0].mikkeMarks, T: 0 } },
    ],
  };
  const slots = resolveMikkeAllocations(closed, [
    { value: 20, multiplier: 2, allocation: null },
    { value: 20, multiplier: 3, allocation: 'NUMBER' },
    { value: 14, multiplier: 2, allocation: null },
  ]);
  assert.deepEqual(
    slots.map((slot) => slot.allocation),
    ['DOUBLE', 'TREBLE', null]
  );
  assert.equal(needsAllocation(slots[0]), false);
  assert.equal(needsAllocation(slots[1]), false);
  assert.equal(pendingAllocationIndex(slots), 2);
  assert.equal(closed.players[0].mikkeMarks.D, 1);
});

test('allocation preview follows same-turn closures and recalculates after edits', () => {
  const relaxed = {
    ...game,
    gametype: 'Mikke Mus Relaxed',
    players: [
      { ...players[0], mikkeMarks: { ...players[0].mikkeMarks, T: 0 } },
    ],
  };
  const entries = [
    { value: 14, multiplier: 1, allocation: 'NUMBER' },
    { value: 14, multiplier: 2, allocation: null },
    { value: 14, multiplier: 3, allocation: null },
  ];
  const resolved = resolveMikkeAllocations(relaxed, entries);
  assert.deepEqual(
    resolved.map((slot) => slot.allocation),
    ['NUMBER', 'DOUBLE', 'TREBLE']
  );
  assert.equal(pendingAllocationIndex(resolved), -1);
  resolved[0] = { value: 0, multiplier: 1, allocation: 'NUMBER' };
  const edited = resolveMikkeAllocations(relaxed, resolved);
  assert.equal(pendingAllocationIndex(edited), 1);
  assert.equal(edited[1].allocation, null);
});

test('forced preview does not close an out-of-order number', () => {
  const resolved = resolveMikkeAllocations(game, [
    { value: 14, multiplier: 1, allocation: 'NUMBER' },
    { value: 14, multiplier: 2, allocation: null },
  ]);
  assert.equal(pendingAllocationIndex(resolved), 1);
});

test('closed doubles and trebles automatically count toward numbers', () => {
  const closed = {
    ...game,
    gametype: 'Mikke Mus Relaxed',
    players: [{ ...players[0], mikkeMarks: { D: 3, T: 3 } }],
  };
  const resolved = resolveMikkeAllocations(closed, [
    { value: 20, multiplier: 2, allocation: null },
    { value: 19, multiplier: 3, allocation: 'TREBLE' },
  ]);
  assert.deepEqual(
    resolved.map((slot) => slot.allocation),
    ['NUMBER', 'NUMBER']
  );
  assert.equal(pendingAllocationIndex(resolved), -1);
});

test('category closures earlier in the turn remove later popups', () => {
  const almost = {
    ...game,
    gametype: 'Mikke Mus Relaxed',
    players: [{ ...players[0], mikkeMarks: { D: 2, T: 2 } }],
  };
  for (const [multiplier, allocation] of [
    [2, 'DOUBLE'],
    [3, 'TREBLE'],
  ]) {
    const entries = [
      { value: 20, multiplier, allocation },
      { value: 19, multiplier, allocation: null },
    ];
    const resolved = resolveMikkeAllocations(almost, entries);
    assert.equal(resolved[1].allocation, 'NUMBER');
    assert.equal(needsAllocation(resolved[1]), false);
    resolved[0] = { value: 0, multiplier: 1, allocation: 'NUMBER' };
    assert.equal(
      pendingAllocationIndex(resolveMikkeAllocations(almost, resolved)),
      1
    );
  }
});

test('bullseye always counts as bull, even with bull closed and doubles open', () => {
  const closedBull = {
    ...game,
    players: [{ ...players[0], mikkeMarks: { B: 3, D: 0 } }],
  };
  const resolved = resolveMikkeAllocations(closedBull, [
    { value: 25, multiplier: 2, allocation: 'DOUBLE' },
  ]);
  assert.equal(resolved[0].allocation, 'BULL');
  assert.equal(needsAllocation(resolved[0]), false);
  assert.deepEqual(
    allocationChoices(resolved[0]).map((choice) => choice.value),
    ['BULL']
  );
});

test('bull-off displays recorded throws and offers each physical region', () => {
  const bull = {
    ...game,
    mikkeMus: {
      ...game.mikkeMus,
      phase: 'BULL_OFF',
      bullHistory: [{ position: 1, region: 1, attempt: 1 }],
    },
  };
  const html = renderToStaticMarkup(
    createElement(MikkeBullOff, { game: bull })
  );
  assert.match(html, /Guest — Outer bull/);
  for (const region of ['Outside bull', 'Outer bull', 'Bullseye'])
    assert.ok(html.includes(region));
  assert.match(html, /alice’s throw/);
  assert.doesNotMatch(html, /Throw one dart each/);
});

test('outside-bull ties offer a distance choice or rethrow, with spectator controls disabled', () => {
  const bull = {
    ...game,
    permissions: { canScore: false, canUndo: false },
    mikkeMus: { ...game.mikkeMus, phase: 'BULL_DECISION' },
  };
  const html = renderToStaticMarkup(
    createElement(MikkeBullOff, { game: bull })
  );
  assert.match(html, /alice is closest/);
  assert.match(html, /Guest is closest/);
  assert.match(html, /Still tied — throw again/);
  assert.match(html, /The tied throws are outside bull/);
  assert.equal((html.match(/disabled=""/g) ?? []).length, 4);
});

test('API sends allocations and separate revision-checked distance/rethrow decisions', async () => {
  const requests = [];
  globalThis.fetch = async (url, options) => {
    requests.push([url, JSON.parse(options.body)]);
    return new Response('{}', { status: 200 });
  };
  try {
    await api.playRound({
      gameId: 4,
      username: 'alice',
      score: '20 2',
      legId: 7,
      revision: 10,
      allocations: ['DOUBLE'],
    });
    await api.resolveBullOff({
      gameId: 4,
      legId: 7,
      revision: 11,
      winner: 'Guest',
    });
    await api.resolveBullOff({
      gameId: 4,
      legId: 7,
      revision: 12,
      rethrow: true,
    });
    assert.deepEqual(requests[0], [
      '/api/games/4/rounds',
      {
        username: 'alice',
        score: '20 2',
        legId: 7,
        revision: 10,
        allocations: ['DOUBLE'],
      },
    ]);
    assert.deepEqual(requests[1], [
      '/api/games/4/bull-off',
      { legId: 7, revision: 11, winner: 'Guest', rethrow: false },
    ]);
    assert.deepEqual(requests[2], [
      '/api/games/4/bull-off',
      { legId: 7, revision: 12, rethrow: true },
    ]);
  } finally {
    globalThis.fetch = originalFetch;
  }
});

test('incomplete Mikke Mus turns do not ask for finish confirmation', () => {
  assert.equal(
    isMatchWinningRound({ ...game, status: 'IN_PROGRESS' }, [
      { value: 25, multiplier: 2 },
    ]),
    false
  );
});

test('Mikke Mus finish confirmation covers both modes, every leg and redemption', () => {
  const marks = Object.fromEntries(
    ['20', '19', '18', '17', '16', '15', '14', 'D', 'T', 'B'].map((key) => [
      key,
      3,
    ])
  );
  for (const gametype of ['Mikke Mus Forced', 'Mikke Mus Relaxed']) {
    for (const phase of ['PLAY', 'REDEMPTION']) {
      const finishing = {
        ...game,
        gametype,
        status: 'IN_PROGRESS',
        legsToWin: 3,
        mikkeMus: { ...game.mikkeMus, phase },
        players: [
          { ...players[0], legsWon: 0, mikkeMarks: { ...marks, B: 1 } },
        ],
      };
      assert.equal(
        isMatchWinningRound(finishing, [{ value: 25, multiplier: 2 }]),
        true
      );
      assert.equal(
        isMatchWinningRound(finishing, [{ value: 25, multiplier: 1 }]),
        false
      );
      assert.equal(
        isMatchWinningRound({ ...finishing, status: 'COMPLETED' }, [
          { value: 25, multiplier: 2 },
        ]),
        false
      );
      assert.equal(
        isMatchWinningRound({ ...finishing, mikkeMus: { phase: 'BULL_OFF' } }, [
          { value: 25, multiplier: 2 },
        ]),
        false
      );
    }
  }
});

test('finish preview respects forced bull locks and number ordering', () => {
  const marks = Object.fromEntries(
    ['20', '19', '18', '17', '16', '15', '14', 'D', 'T', 'B'].map((key) => [
      key,
      3,
    ])
  );
  const finishing = {
    ...game,
    status: 'IN_PROGRESS',
    players: [{ ...players[0], mikkeMarks: { ...marks, T: 2, B: 1 } }],
  };
  assert.equal(
    isMatchWinningRound(finishing, [
      { value: 25, multiplier: 2 },
      { value: 1, multiplier: 3, allocation: 'TREBLE' },
    ]),
    false
  );
  assert.equal(
    isMatchWinningRound(finishing, [
      { value: 1, multiplier: 3, allocation: 'TREBLE' },
      { value: 25, multiplier: 2 },
    ]),
    true
  );
});

test('Mikke Mus scoreboard can leave More stats to the scoring controls', () => {
  const html = renderToStaticMarkup(
    createElement(MikkeScoreboard, { game, showStats: false })
  );
  assert.match(html, /Mikke Mus marks/);
  assert.doesNotMatch(html, /More stats/);
});

test('redemption notice appears only for an active redemption turn', () => {
  const redemption = {
    ...game,
    status: 'IN_PROGRESS',
    mikkeMus: { ...game.mikkeMus, phase: 'REDEMPTION' },
  };
  const html = renderToStaticMarkup(
    createElement(MikkeRedemptionNotice, { game: redemption })
  );
  assert.match(html, /Redemption!/);
  assert.match(html, /One final turn to finish/);
  assert.match(html, /role="status"/);
  assert.match(html, /mikke-redemption-notice/);
  for (const phase of ['PLAY', 'BULL_DECISION', 'FINISHED']) {
    assert.equal(
      renderToStaticMarkup(
        createElement(MikkeRedemptionNotice, {
          game: { ...redemption, mikkeMus: { phase } },
        })
      ),
      ''
    );
  }
  assert.equal(
    renderToStaticMarkup(
      createElement(MikkeRedemptionNotice, {
        game: { ...redemption, status: 'COMPLETED' },
      })
    ),
    ''
  );
});

test('tied finishers get the matching TIEBREAKER animation for a bull-off', () => {
  const tied = {
    ...game,
    status: 'IN_PROGRESS',
    mikkeMus: { ...game.mikkeMus, phase: 'BULL_OFF', bullAttempt: 1 },
  };
  const html = renderToStaticMarkup(
    createElement(MikkeRedemptionNotice, { game: tied })
  );
  assert.match(html, /TIEBREAKER!/);
  assert.match(html, /Closest to bull wins/);
  assert.match(html, /mikke-redemption-notice/);
  assert.doesNotMatch(html, /Redemption!/);
  assert.equal(
    renderToStaticMarkup(
      createElement(MikkeRedemptionNotice, {
        game: { ...tied, status: 'COMPLETED' },
      })
    ),
    ''
  );
});
