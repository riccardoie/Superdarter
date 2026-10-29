import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';
import { MemoryRouter } from 'react-router-dom';
import {
  matchFormatLabel,
  standingsInStartingOrder,
} from '../src/matchFormat.js';
import { matchStats } from '../src/gameStats.js';

let server;
let api;
let GameStatsTable;
let CheckoutSuggestion;
let GameCard;
let CreateGame;
let GameSetupPage;
let MatchProgress;
let LegTargetPicker;
let GameScoreboard;
let CompletedLegs;
const originalFetch = globalThis.fetch;
const originalLocalStorage = globalThis.localStorage;

function scoringStats(overrides = {}) {
  return {
    dartsThrown: 0,
    firstNinePoints: 0,
    firstNineDarts: 0,
    firstNineAverage: 0,
    score40: 0,
    score60: 0,
    score80: 0,
    score100: 0,
    score120: 0,
    score140: 0,
    score160: 0,
    score180: 0,
    ...overrides,
  };
}

test('match detailed stats sum counters and weight first-nine average by darts', () => {
  const player = {
    username: 'alice',
    turns: 4,
    points: 0,
    highestScore: 180,
    bullseyes: 0,
    triple20s: 3,
  };
  const first = scoringStats({
    dartsThrown: 6,
    firstNinePoints: 120,
    firstNineDarts: 3,
    firstNineAverage: 120,
    score100: 1,
    score180: 1,
  });
  const second = scoringStats({
    dartsThrown: 12,
    firstNinePoints: 450,
    firstNineDarts: 9,
    firstNineAverage: 150,
    score100: 2,
    score180: 2,
  });
  const game = {
    startingPoints: 301,
    players: [player],
    legs: [
      { players: [{ ...player, scoringStats: first }] },
      { players: [{ ...player, scoringStats: second }] },
    ],
  };
  assert.deepEqual(
    matchStats(game)[0].scoringStats,
    scoringStats({
      dartsThrown: 18,
      firstNinePoints: 570,
      firstNineDarts: 12,
      firstNineAverage: 142.5,
      score100: 3,
      score180: 3,
    })
  );
  game.legs[0].players[0].scoringStats = null;
  assert.equal(matchStats(game)[0].scoringStats, null);
});

for (const showTurns of [true, false]) {
  test(`recorded detailed stats render with showTurns=${showTurns}`, () => {
    const html = renderToStaticMarkup(
      createElement(GameStatsTable, {
        players: [
          {
            username: 'alice',
            turns: 4,
            points: 0,
            triple20s: 9,
            scoringStats: scoringStats({
              dartsThrown: 10,
              firstNineAverage: 125.25,
              score100: 2,
              score180: 1,
            }),
          },
        ],
        startingPoints: 301,
        showTurns,
      })
    );
    assert.match(html, /data-label="Darts thrown">10<\/(td|dd)>/);
    assert.match(html, /data-label="First 9 avg">125\.3<\/td>/);
    assert.match(html, /data-label="100\+">2<\/dd>/);
    assert.match(html, /data-label="180">1<\/dd>/);
    assert.match(html, /data-label="40\+">0<\/dd>/);
    assert.doesNotMatch(html, /Checkout accuracy/);
  });
}

test('match stats aggregate all legs with a turn-weighted average', () => {
  const player = {
    username: 'alice',
    points: 0,
    turns: 5,
    highestScore: 100,
    highestCheckout: 40,
    bullseyes: 1,
    triple20s: 1,
  };
  const game = {
    startingPoints: 301,
    players: [player],
    legs: [
      {
        players: [
          {
            ...player,
            turns: 3,
            highestScore: 180,
            highestCheckout: 121,
            bullseyes: 2,
            triple20s: 2,
          },
        ],
      },
      { players: [player] },
    ],
  };
  assert.deepEqual(matchStats(game), [
    {
      ...player,
      scoringStats: null,
      turns: 8,
      highestScore: 180,
      highestCheckout: 121,
      bullseyes: 3,
      triple20s: 3,
      average: 602 / 8,
    },
  ]);
});

test('match stats handle unplayed legs and missing leg history', () => {
  const player = {
    username: 'alice',
    points: 301,
    turns: 0,
    highestScore: 0,
    bullseyes: 0,
  };
  assert.deepEqual(matchStats({ players: [player] }), [player]);
  assert.equal(
    matchStats({
      players: [player],
      startingPoints: 301,
      legs: [{ players: [player] }],
    })[0].average,
    0
  );
});

test('leg table shows scoring stats without checkout accuracy', () => {
  const html = renderToStaticMarkup(
    createElement(GameStatsTable, {
      players: [
        {
          username: 'alice',
          turns: 8,
          average: 75.25,
          highestScore: 180,
          highestCheckout: 121,
          bullseyes: 3,
        },
      ],
      winner: 'alice',
      startingPoints: 301,
    })
  );
  assert.doesNotMatch(html, /data-label="Points"|>Points<|180s/);
  assert.match(html, /data-label="Turns">8<\/td>/);
  assert.match(html, /data-label="Average">75\.3<\/td>/);
  assert.match(html, /data-label="Highest score">180<\/td>/);
  assert.match(html, /data-label="Highest checkout">121<\/td>/);
  assert.match(
    html,
    /data-label="First 9 avg"><span title="Not available for this game">—<\/span>/
  );
  assert.match(html, /data-label="Bullseyes">3<\/dd>/);
  assert.match(html, /<details class="game-stats-details"><summary>More stats/);
  assert.match(html, /<dl class="game-stats-counts">/);
  assert.doesNotMatch(html, /Checkout accuracy/);
});

for (const showTurns of [true, false]) {
  test(`darts thrown and score counts are placeholders with showTurns=${showTurns}`, () => {
    const html = renderToStaticMarkup(
      createElement(GameStatsTable, {
        players: [
          { username: 'alice', turns: 8, average: 75.25, triple20s: 2 },
        ],
        startingPoints: 301,
        showTurns,
      })
    );
    for (const label of [
      'Darts thrown',
      '40+',
      '60+',
      '80+',
      '100+',
      '120+',
      '140+',
      '160+',
    ]) {
      assert.ok(
        html.includes(
          `data-label="${label}"><span title="Not available for this game">—</span>`
        )
      );
    }
    assert.match(
      html,
      /data-label="180"><span title="Not available for this game">—<\/span>/
    );
  });
}

test('winner table hides turns and checkout accuracy', () => {
  const html = renderToStaticMarkup(
    createElement(GameStatsTable, {
      players: [
        {
          username: 'alice',
          turns: 8,
          average: 75.25,
          highestScore: 180,
          bullseyes: 3,
          legsWon: 2,
        },
      ],
      winner: 'alice',
      startingPoints: 301,
      showTurns: false,
      showLegsWon: true,
    })
  );
  assert.doesNotMatch(html, /data-label="Turns"|>Turns</);
  assert.match(html, /data-label="Legs won">2<\/td>/);
  assert.match(html, /colSpan="6"|colspan="6"/);
  assert.match(
    html,
    /data-label="First 9 avg"><span title="Not available for this game">—<\/span>/
  );
  assert.match(html, /data-label="Average">75\.3<\/td>/);
  assert.doesNotMatch(html, /Checkout accuracy/);
});

before(async () => {
  server = await createServer({ server: { middlewareMode: true } });
  api = await server.ssrLoadModule('/src/api.js');
  ({ default: GameStatsTable } = await server.ssrLoadModule(
    '/src/components/GameStatsTable.jsx'
  ));
  ({ default: CheckoutSuggestion } = await server.ssrLoadModule(
    '/src/components/CheckoutSuggestion.jsx'
  ));
  ({ default: GameCard } = await server.ssrLoadModule(
    '/src/components/GameCard.jsx'
  ));
  ({ default: CreateGame } = await server.ssrLoadModule(
    '/src/components/CreateGame.jsx'
  ));
  ({ default: GameSetupPage } = await server.ssrLoadModule(
    '/src/pages/GameSetupPage.jsx'
  ));
  ({ default: MatchProgress } = await server.ssrLoadModule(
    '/src/components/MatchProgress.jsx'
  ));
  ({ default: LegTargetPicker } = await server.ssrLoadModule(
    '/src/components/LegTargetPicker.jsx'
  ));
  ({ default: GameScoreboard } = await server.ssrLoadModule(
    '/src/components/GameScoreboard.jsx'
  ));
  ({ default: CompletedLegs } = await server.ssrLoadModule(
    '/src/components/CompletedLegs.jsx'
  ));
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
          gameId,
          gametype: String(startingPoints),
          startingPoints,
          status,
          winner: status === 'COMPLETED' ? 'bob' : null,
          turn: status === 'IN_PROGRESS' ? 'alice' : null,
          players: [
            {
              username: 'alice',
              points: startingPoints - 180,
              turns: 3,
            },
          ],
        };
        return new Response(JSON.stringify(body));
      };

      const game = await api.getGameState({ gameId });
      assert.equal(game.startingPoints, startingPoints);
      assert.equal(calls.length, 1);
      const html = renderToStaticMarkup(
        createElement(GameStatsTable, {
          players: game.players,
          winner: game.winner,
          startingPoints: game.startingPoints,
        })
      );
      assert.match(html, /data-label="Average">60\.0<\/td>/);
      assert.doesNotMatch(html, /NaN/);
    });
  }
}

test('create, list and round commands use the standalone game contract', async () => {
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({
      url,
      method: options.method,
      body: options.body && JSON.parse(options.body),
    });
    return new Response(JSON.stringify({ gameId: 7, revision: 2 }));
  };
  await api.createGame({ gametype: '501', players: ['bob'] });
  await api.getGames({ gamemode: '301' });
  await api.startGame({ gameId: 7 });
  await api.playRound({
    gameId: 7,
    legId: 9,
    revision: 1,
    username: 'alice',
    score: '',
  });
  await api.abandonGame({ gameId: 7 });
  await api.deleteGame({ gameId: 7 });
  assert.deepEqual(
    calls.map((call) => [call.url, call.method]),
    [
      ['/api/games', 'POST'],
      ['/api/games?gametype=301', 'GET'],
      ['/api/games/7/start', 'POST'],
      ['/api/games/7/rounds', 'POST'],
      ['/api/games/7/abandon', 'POST'],
      ['/api/games/7', 'DELETE'],
    ]
  );
  assert.deepEqual(calls[0].body, {
    gametype: '501',
    players: [{ username: 'bob' }],
    legsToWin: 1,
  });
  assert.deepEqual(calls[3].body, {
    username: 'alice',
    score: '',
    legId: 9,
    revision: 1,
  });
});

test('stale round failures preserve conflict status and never retry automatically', async () => {
  let count = 0;
  globalThis.fetch = async () => {
    count++;
    return new Response(JSON.stringify({ message: 'Game state changed' }), {
      status: 409,
    });
  };
  await assert.rejects(
    api.playRound({
      gameId: 7,
      legId: 9,
      revision: 1,
      username: 'alice',
      score: '',
    }),
    (error) => error.status === 409 && error.message === 'Game state changed'
  );
  assert.equal(count, 1);
});

test('round undo submits the current revision and returns the restored game', async () => {
  const calls = [];
  const restored = {
    gameId: 7,
    revision: 4,
    status: 'IN_PROGRESS',
    turn: 'alice',
  };
  globalThis.fetch = async (url, options) => {
    calls.push({ url, method: options.method, body: JSON.parse(options.body) });
    return new Response(JSON.stringify(restored));
  };
  assert.deepEqual(await api.undoRound({ gameId: 7, revision: 3 }), restored);
  assert.deepEqual(calls, [
    { url: '/api/games/7/undo', method: 'POST', body: { revision: 3 } },
  ]);
});

test('stale undo is not retried against a newer round', async () => {
  let count = 0;
  globalThis.fetch = async () => {
    count++;
    return new Response(JSON.stringify({ message: 'Game state changed' }), {
      status: 409,
    });
  };
  await assert.rejects(
    api.undoRound({ gameId: 7, revision: 3 }),
    (error) => error.status === 409
  );
  assert.equal(count, 1);
});

test('average is zero before the first turn', () => {
  const html = renderToStaticMarkup(
    createElement(GameStatsTable, {
      players: [{ username: 'alice', points: 501, turns: 0 }],
      startingPoints: 501,
    })
  );
  assert.match(html, /data-label="Average">0<\/td>/);
  assert.doesNotMatch(html, /NaN|Infinity/);
});

test('game cards link directly to games and distinguish abandonment from a win', () => {
  for (const status of ['PENDING', 'IN_PROGRESS', 'COMPLETED', 'ABANDONED']) {
    const winner = status === 'COMPLETED' ? 'bob' : null;
    const html = renderToStaticMarkup(
      createElement(
        MemoryRouter,
        null,
        createElement(GameCard, {
          game: {
            gameId: 7,
            gametype: '501',
            status,
            winner,
            createdAt: '2026-10-26T12:00:00Z',
            players: [{ username: 'alice' }, { username: 'bob' }],
          },
        })
      )
    );
    assert.match(html, /href="\/games\/7"/);
    assert.match(html, /alice: 0 · bob: 0/);
    assert.equal((html.match(/class="match-card-players"/g) ?? []).length, 1);
    assert.match(html, /26 Oct 2026/);
    if (winner) assert.match(html, /Winner: bob/);
    else assert.doesNotMatch(html, /Winner:/);
    if (status === 'ABANDONED') assert.match(html, /Abandoned/);
    assert.doesNotMatch(html, /session/i);
  }
});

test('creation form automatically includes the owner in the ten-player limit', () => {
  const payload = Buffer.from(JSON.stringify({ sub: 'alice' })).toString(
    'base64url'
  );
  globalThis.localStorage = { getItem: () => `header.${payload}.signature` };
  const html = renderToStaticMarkup(
    createElement(CreateGame, { onClose() {}, onContinue() {} })
  );
  assert.match(html, /Create game/);
  assert.match(html, /alice \(you\)/);
  assert.match(html, /1 \/ 10/);
  assert.match(html, /Continue/);
  assert.match(html, /Player type/);
  assert.match(html, /Guest player/);
  assert.doesNotMatch(html, /First to win|type="number"|Double-out/);
  assert.doesNotMatch(html, /session/i);
  globalThis.localStorage = { getItem: () => null };
});

test('game setup shows final details without creating or starting anything', () => {
  const payload = Buffer.from(JSON.stringify({ sub: 'alice' })).toString(
    'base64url'
  );
  globalThis.localStorage = { getItem: () => `header.${payload}.signature` };
  globalThis.fetch = () => assert.fail('setup must not send any requests');
  const draft = {
    gametype: '501',
    players: ['bob', 'carol'],
    guests: ['Guest One'],
    finishes: { double: '501', single: '501 Single Out' },
  };
  const html = renderToStaticMarkup(
    createElement(
      MemoryRouter,
      {
        initialEntries: [{ pathname: '/games/new', state: { draft } }],
      },
      createElement(GameSetupPage)
    )
  );
  assert.match(html, /Ready to play 501/);
  assert.match(html, /alice \(you\)/);
  assert.match(html, /bob/);
  assert.match(html, /carol/);
  assert.match(html, /Guest One/);
  assert.match(html, /Their results stay in this match, not in a profile/);
  assert.match(html, /First to win/);
  assert.match(html, /type="number" min="1" max="99" step="1" required=""/);
  assert.match(html, /Single out/);
  assert.match(html, /Double out/);
  assert.match(html, /type="radio" name="finish" checked="" value="double"/);
  assert.match(html, /Cancel setup/);
  assert.match(html, /Start game/);
  assert.match(html, /Nothing is saved until you press Start game/);
  globalThis.localStorage = { getItem: () => null };
});

test('first-to creation sends the selected win target', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/games');
    assert.deepEqual(JSON.parse(options.body), {
      gametype: '301',
      players: [],
      legsToWin: 3,
    });
    return new Response(
      JSON.stringify({ gameId: 7, legsToWin: 3, format: 'FIRST_TO_LEGS' })
    );
  };
  const game = await api.createGame({ gametype: '301', legsToWin: 3 });
  assert.equal(game.legsToWin, 3);
  assert.equal(matchFormatLabel(game.legsToWin), 'First to 3 legs');
  assert.equal(matchFormatLabel(1), 'Single leg');
});

test('guest creation keeps guests separate from account usernames in the API request', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/games');
    assert.deepEqual(JSON.parse(options.body), {
      gametype: '301',
      players: [{ username: 'bob' }],
      guests: ['Guest One'],
      legsToWin: 2,
    });
    return new Response(JSON.stringify({ gameId: 8 }));
  };
  await api.createGame({
    gametype: '301',
    players: ['bob'],
    guests: ['Guest One'],
    legsToWin: 2,
  });
});

test('guest markers are hidden on the scoreboard but retained in history and match statistics', () => {
  const player = {
    username: 'Guest One',
    guest: true,
    points: 40,
    turns: 2,
    legsWon: 1,
  };
  const game = {
    gameId: 8,
    gametype: '301',
    startingPoints: 301,
    status: 'COMPLETED',
    winner: 'Guest One',
    createdAt: '2026-10-28T12:00:00Z',
    players: [player],
  };
  const scoreboard = renderToStaticMarkup(
    createElement(GameScoreboard, { game, slots: [] })
  );
  assert.match(scoreboard, /Guest One/);
  assert.doesNotMatch(scoreboard, /player-guest-label/);
  const table = renderToStaticMarkup(
    createElement(GameStatsTable, {
      players: [player],
      startingPoints: 301,
      winner: 'Guest One',
    })
  );
  assert.match(table, /player-guest-label/);
  assert.match(table, /game-stats-winner/);
  const card = renderToStaticMarkup(
    createElement(MemoryRouter, null, createElement(GameCard, { game }))
  );
  assert.match(card, /Guest One \(guest\): 1/);
  assert.match(card, /Winner: Guest One/);
});

test('leg target has separate step buttons, increments correctly and respects limits', () => {
  let value;
  const picker = LegTargetPicker({
    value: '3',
    onChange: (next) => {
      value = next;
    },
  });
  const [decrease, , increase] = picker.props.children[1].props.children;
  decrease.props.onClick();
  assert.equal(value, '2');
  increase.props.onClick();
  assert.equal(value, '4');
  for (const [count, lowerDisabled, upperDisabled] of [
    ['1', true, false],
    ['99', false, true],
  ]) {
    const tree = LegTargetPicker({ value: count, onChange() {} });
    const [lower, input, upper] = tree.props.children[1].props.children;
    assert.equal(lower.props.disabled, lowerDisabled);
    assert.equal(upper.props.disabled, upperDisabled);
    assert.equal(input.type, 'input');
  }
  const html = renderToStaticMarkup(
    createElement(LegTargetPicker, { value: '3', onChange() {} })
  );
  assert.ok(html.indexOf('Decrease legs to win') < html.indexOf('<input'));
  assert.ok(html.indexOf('Increase legs to win') > html.indexOf('<input'));
});

test('leg standings retain original order as starting players rotate', () => {
  const original = ['alice', 'bob', 'carol'].map((username) => ({
    username,
    legsWon: 0,
  }));
  for (let offset = 0; offset < original.length; offset++) {
    const players = [
      ...original.slice(offset),
      ...original.slice(0, offset),
    ].map((player) => ({
      ...player,
      legsWon: player.username === 'alice' ? 2 : 1,
    }));
    const game = {
      legsToWin: 3,
      startingPoints: 501,
      gametype: '501',
      currentLegId: 10 + offset,
      players,
      legs: [
        { legId: 10, number: 1, status: 'IN_PROGRESS', players: original },
      ],
    };
    const ordered = standingsInStartingOrder(game);
    assert.deepEqual(
      ordered.map((player) => player.username),
      ['alice', 'bob', 'carol']
    );
    assert.equal(ordered[0].legsWon, 2);
    assert.equal(game.players[0].username, original[offset].username);
    const html = renderToStaticMarkup(
      createElement(GameScoreboard, { game, slots: [] })
    );
    const standings = html.slice(html.indexOf('<details'));
    assert.ok(
      standings.indexOf('title="alice"') < standings.indexOf('title="bob"')
    );
    assert.ok(
      standings.indexOf('title="bob"') < standings.indexOf('title="carol"')
    );
  }
});

test('multi-leg standings and completed leg stats survive reload', async () => {
  const players = [
    { username: 'alice', points: 301, turns: 0, legsWon: 1 },
    { username: 'bob', points: 301, turns: 0, legsWon: 0 },
  ];
  const body = {
    gameId: 7,
    legsToWin: 2,
    gametype: '301',
    startingPoints: 301,
    status: 'IN_PROGRESS',
    currentLegId: 12,
    turn: 'bob',
    players,
    legs: [
      {
        legId: 11,
        number: 1,
        status: 'COMPLETED',
        winner: 'alice',
        players: [
          { ...players[0], points: 0, turns: 6 },
          { ...players[1], points: 121, turns: 5 },
        ],
      },
      { legId: 12, number: 2, status: 'IN_PROGRESS', winner: null, players },
    ],
  };
  globalThis.fetch = async () => new Response(JSON.stringify(body));
  const game = await api.getGameState({ gameId: 7 });
  assert.equal(game.currentLegId, 12);
  assert.equal(game.turn, 'bob');
  const html = renderToStaticMarkup(createElement(MatchProgress, { game }));
  assert.match(html, /First to 2 legs/);
  assert.match(html, /Leg 2 · 301/);
  assert.doesNotMatch(html, /Completed legs|match-standings/);
  const scoreboard = renderToStaticMarkup(
    createElement(GameScoreboard, { game, slots: [] })
  );
  assert.match(
    scoreboard,
    /game-scoreboard-stats"><span class="game-scoreboard-leg-wins">Legs: 0 \/ 2<\/span><span class="game-scoreboard-turns">Turns: 0/
  );
  assert.match(
    scoreboard,
    /<span class="game-scoreboard-checkout"><\/span><span class="game-scoreboard-points" aria-label="Remaining points: 301">301<\/span>/
  );
  const history = renderToStaticMarkup(createElement(CompletedLegs, { game }));
  assert.match(
    history,
    /<button type="button" class="match-leg-toggle" aria-expanded="false"/
  );
  assert.match(history, /Show completed legs \(1\)/);
  assert.match(history, /id="completed-legs-panel" hidden=""/);
  assert.match(history, /Leg 1 — alice wins/);
  assert.match(history, /<section class="game-stats-section"><h2>Leg 1/);
  assert.doesNotMatch(history, /<summary>Leg /);
  assert.match(history, /data-label="Average">50\.2/);
  assert.doesNotMatch(history, /<th[^>]*>Turns<\/th>/);
  assert.doesNotMatch(history, /<th[^>]*>Darts thrown<\/th>/);
  assert.match(history, /<dt>Darts thrown<\/dt>/);
  assert.match(history, /<dt>Bullseyes<\/dt>/);
  assert.doesNotMatch(history, /NaN/);
  const card = renderToStaticMarkup(
    createElement(
      MemoryRouter,
      null,
      createElement(GameCard, {
        game: { ...game, createdAt: '2026-10-26T12:00:00Z' },
      })
    )
  );
  assert.match(card, /First to 2 legs/);
  assert.match(card, /alice: 1 · bob: 0/);
  assert.equal(
    renderToStaticMarkup(
      createElement(MatchProgress, { game: { legsToWin: 1 } })
    ),
    ''
  );
});

test('live scoreboard displays the total average across all legs', () => {
  const player = {
    username: 'alice',
    points: 201,
    turns: 2,
    highestScore: 100,
    bullseyes: 0,
    legsWon: 1,
  };
  const game = {
    legsToWin: 2,
    startingPoints: 301,
    turn: 'alice',
    players: [player],
    legs: [
      { players: [{ ...player, points: 0, turns: 4 }] },
      { players: [player] },
    ],
  };
  const html = renderToStaticMarkup(
    createElement(GameScoreboard, { game, slots: [] })
  );
  assert.match(
    html,
    /game-scoreboard-average" title="Average across all legs">Avg: 66\.8/
  );
  assert.match(html, /Turns: 2/);
  assert.match(html, /Remaining points: 201/);
});

test('live scoreboard shows zero average before the first turn', () => {
  const game = {
    startingPoints: 501,
    players: [{ username: 'alice', points: 501, turns: 0 }],
  };
  const html = renderToStaticMarkup(
    createElement(GameScoreboard, { game, slots: [] })
  );
  assert.match(html, /Avg: 0\.0/);
  assert.doesNotMatch(html, /NaN|Infinity/);
});

test('scoreboard focuses on the current player and keeps all standings collapsed', () => {
  const players = ['alice', 'bob', 'carol'].map((username, index) => ({
    username,
    points: 301 - index * 20,
    turns: index,
    legsWon: 0,
  }));
  for (let index = 0; index < players.length; index++) {
    const html = renderToStaticMarkup(
      createElement(GameScoreboard, {
        game: { startingPoints: 301, turn: players[index].username, players },
        slots: [],
      })
    );
    const current = html.slice(
      html.indexOf('<section'),
      html.indexOf('</section>')
    );
    const next = html.slice(html.indexOf('<aside'), html.indexOf('</aside>'));
    assert.match(current, /Now throwing/);
    assert.ok(current.includes(`title="${players[index].username}"`));
    assert.ok(
      next.includes(`title="${players[(index + 1) % players.length].username}"`)
    );
    assert.equal([...html.matchAll(/class="game-scoreboard-row/g)].length, 1);
    assert.match(
      html,
      /<details class="game-all-standings"><summary>All players \(3\)/
    );
    assert.doesNotMatch(html, /<details[^>]*\sopen/);
  }
});

test('solo scoreboard shows all standings statistics directly, without next player or All players', () => {
  const html = renderToStaticMarkup(
    createElement(GameScoreboard, {
      game: {
        startingPoints: 301,
        turn: 'alice',
        players: [
          {
            username: 'alice',
            points: 181,
            turns: 2,
            highestScore: 80,
            scoringStats: { firstNineAverage: 60 },
          },
        ],
      },
      slots: [],
    })
  );
  assert.match(html, /game-turn-solo/);
  assert.doesNotMatch(html, /<aside|Next up|All players|game-all-standings/);
  assert.match(html, /Avg: 60\.0/);
  assert.match(html, /First 9 avg: 60\.0/);
  assert.match(html, /Best score: 80/);
  assert.match(html, /Legs: 0 \/ 1/);
  assert.match(html, /Remaining points: 181/);
});

test('dartboard compact scoreboard keeps the current score, checkout and expandable statistics', () => {
  const html = renderToStaticMarkup(
    createElement(GameScoreboard, {
      game: {
        startingPoints: 301,
        turn: 'alice',
        players: [
          { username: 'alice', points: 40, turns: 5, highestScore: 100 },
          { username: 'bob', points: 120, turns: 4, highestScore: 80 },
        ],
      },
      slots: [],
      compact: true,
    })
  );
  assert.match(html, /game-turn-overview-compact/);
  assert.match(html, /Now throwing/);
  assert.match(html, /Remaining points: 40/);
  assert.match(html, /d20/);
  assert.match(html, /<summary>All players \(2\)/);
  assert.match(html, /<table class="game-standings-table"/);
});

test('all players table displays the requested statistics and handles missing first-nine data', () => {
  const html = renderToStaticMarkup(
    createElement(GameScoreboard, {
      game: {
        startingPoints: 301,
        turn: 'alice',
        players: [
          {
            username: 'alice',
            points: 181,
            turns: 2,
            highestScore: 80,
            legsWon: 1,
            scoringStats: { firstNineAverage: 65.25 },
          },
          { username: 'bob', points: 301, turns: 0 },
        ],
      },
      slots: [],
    })
  );
  const table = html.slice(html.indexOf('<table'), html.indexOf('</table>'));
  assert.deepEqual(
    [...table.matchAll(/<th scope="col">(.*?)<\/th>/g)].map((match) =>
      match[1].replace(/<br\s*\/?\s*>/g, ' ')
    ),
    ['Players', 'Avg', 'First 9 avg', 'Best score', 'Legs', 'Points left']
  );
  assert.match(
    table,
    /<td>60\.0<\/td><td>65\.3<\/td><td>80<\/td><td>1<\/td><td aria-label="Remaining points: 181">181<\/td>/
  );
  assert.match(table, /<td>0\.0<\/td><td>—<\/td><td>0<\/td><td>0<\/td>/);
  assert.match(table, /<tr aria-current="true">/);
});

test('scoreboard reserves checkout space and always puts remaining points last', () => {
  const game = {
    legsToWin: 3,
    startingPoints: 501,
    turn: 'alice',
    players: [
      { username: 'alice', points: 100, turns: 5, legsWon: 1 },
      { username: 'bob', points: 501, turns: 4, legsWon: 0 },
    ],
    legs: [],
  };
  const html = renderToStaticMarkup(
    createElement(GameScoreboard, { game, slots: [] })
  );
  assert.equal(
    [...html.matchAll(/class="game-scoreboard-checkout"/g)].length,
    1
  );
  assert.match(html, /t20 → d20/);
  const stats = html.slice(html.indexOf('game-scoreboard-stats'));
  assert.ok(
    stats.indexOf('game-scoreboard-leg-wins') <
      stats.indexOf('game-scoreboard-turns')
  );
  assert.ok(
    stats.indexOf('game-scoreboard-turns') <
      stats.indexOf('game-scoreboard-checkout')
  );
  assert.ok(
    stats.indexOf('game-scoreboard-checkout') <
      stats.indexOf('game-scoreboard-points')
  );
});

test('checkout suggestion renders compact routes and hides unavailable checkouts', () => {
  const render = (points, slots) =>
    renderToStaticMarkup(createElement(CheckoutSuggestion, { points, slots }));
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

test('single-out scoreboard offers the last single instead of hiding the checkout', () => {
  const game = {
    gametype: '301 Single Out',
    startingPoints: 301,
    turn: 'alice',
    players: [{ username: 'alice', points: 1, turns: 5 }],
  };
  const render = (gametype) =>
    renderToStaticMarkup(
      createElement(GameScoreboard, { game: { ...game, gametype }, slots: [] })
    );
  assert.match(render('301 Single Out'), /aria-label="Checkout: 1"/);
  assert.doesNotMatch(render('301'), /aria-label="Checkout:/);
});
