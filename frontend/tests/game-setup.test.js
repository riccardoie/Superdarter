import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  countdownModes,
  isSingleOut,
  playerNameError,
  gameFriendSuggestions,
  startDraftGame,
} from '../src/gameSetup.js';

test('groups API modes into starting scores and exact finishing-rule identifiers', () => {
  assert.deepEqual(
    countdownModes(
      [
        '501',
        '301',
        '301 Single Out',
        '501 Single Out',
        'Cricket',
        'Killer',
      ].map((gamemode) => ({ gamemode }))
    ),
    [
      {
        gamemode: '501',
        finishes: { double: '501', single: '501 Single Out' },
      },
      {
        gamemode: '301',
        finishes: { double: '301', single: '301 Single Out' },
      },
    ]
  );
});

test('offers only finishing rules actually returned by the backend', () => {
  assert.deepEqual(countdownModes([{ gamemode: '301 Single Out' }]), [
    { gamemode: '301', finishes: { single: '301 Single Out' } },
  ]);
  assert.deepEqual(countdownModes([{ gamemode: 'Cricket' }]), []);
  assert.equal(isSingleOut('301 Single Out'), true);
  assert.equal(isSingleOut('501 Single Out'), true);
  assert.equal(isSingleOut('301'), false);
  assert.equal(isSingleOut('501'), false);
});

const config = {
  gametype: '501 Single Out',
  players: ['bob'],
  guests: ['Guest One'],
  legsToWin: 3,
};

test('registered players and guests share unique names and one player limit', () => {
  const names = {
    owner: 'alice',
    players: ['bob'],
    guests: ['Guest One'],
    maxPlayers: 10,
  };
  assert.equal(playerNameError('Guest Two', names), '');
  for (const name of ['', 'alice', 'bob', 'Guest One', 'x'.repeat(256)]) {
    assert.notEqual(playerNameError(name, names), '');
  }
  assert.match(
    playerNameError('Guest Two', { ...names, maxPlayers: 3 }),
    /Max 3 players/
  );
});

test('registered opponents must be accepted friends, while guest validation stays independent', () => {
  const names = { owner: 'alice', players: [], guests: [], maxPlayers: 10 };
  assert.match(
    playerNameError('bob', { ...names, friends: [] }),
    /accepted friends/
  );
  assert.equal(
    playerNameError('bob', { ...names, friends: [{ username: 'bob' }] }),
    ''
  );
  assert.match(
    playerNameError('Bob', { ...names, friends: [{ username: 'bob' }] }),
    /accepted friends/
  );
  assert.equal(playerNameError('Guest', names), '');
});

test('friend suggestions filter case-insensitively and exclude selected players, guests and self', () => {
  const friends = ['alice', 'bob', 'Bobby', 'carol', 'dave'].map(
    (username) => ({ username })
  );
  const selection = {
    owner: 'alice',
    players: ['bob'],
    guests: ['dave'],
    maxPlayers: 10,
  };
  assert.deepEqual(gameFriendSuggestions(friends, ' BO ', selection), [
    'Bobby',
  ]);
  assert.deepEqual(gameFriendSuggestions(friends, '', selection), [
    'Bobby',
    'carol',
  ]);
  assert.deepEqual(gameFriendSuggestions(friends, 'unknown', selection), []);
  assert.deepEqual(
    gameFriendSuggestions(friends, '', { ...selection, maxPlayers: 3 }),
    []
  );
});

test('draft selection is local; starting creates the configured game then starts its ID', async () => {
  const calls = [];
  const api = {
    createGame: async (body) => {
      calls.push(['create', body]);
      return { gameId: 7 };
    },
    startGame: async (body) => {
      calls.push(['start', body]);
      return { gameId: 7, status: 'IN_PROGRESS' };
    },
    deleteGame: async () => assert.fail('successful start must not delete'),
  };
  countdownModes([{ gamemode: '501 Single Out' }]);
  assert.deepEqual(calls, []);
  assert.deepEqual(await startDraftGame(config, api), {
    gameId: 7,
    status: 'IN_PROGRESS',
  });
  assert.deepEqual(calls, [
    ['create', config],
    ['start', { gameId: 7 }],
  ]);
});

test('invalid players fail creation without attempting start or deletion', async () => {
  const error = Object.assign(new Error('User not found'), { status: 404 });
  await assert.rejects(
    startDraftGame(config, {
      createGame: async () => {
        throw error;
      },
      startGame: async () => assert.fail('must not start'),
      deleteGame: async () => assert.fail('nothing was created'),
    }),
    (actual) => actual === error
  );
});

test('failed starts remove the pending game', async () => {
  const deleted = [];
  await assert.rejects(
    startDraftGame(config, {
      createGame: async () => ({ gameId: 8 }),
      startGame: async () => {
        throw new Error('offline');
      },
      deleteGame: async (body) => deleted.push(body),
    }),
    (error) => {
      assert.match(error.message, /Failed to start game: offline/);
      assert.equal(error.pendingGame, undefined);
      return true;
    }
  );
  assert.deepEqual(deleted, [{ gameId: 8 }]);
});

test('failed cleanup retains the pending ID and retry never creates a second game', async () => {
  let pendingGame;
  await assert.rejects(
    startDraftGame(config, {
      createGame: async () => ({ gameId: 9 }),
      startGame: async () => {
        throw new Error('offline');
      },
      deleteGame: async () => {
        throw new Error('offline');
      },
    }),
    (error) => {
      pendingGame = error.pendingGame;
      assert.deepEqual(pendingGame, { gameId: 9 });
      return true;
    }
  );
  const started = await startDraftGame(
    config,
    {
      createGame: async () => assert.fail('must reuse pending game'),
      startGame: async ({ gameId }) => ({ gameId, status: 'IN_PROGRESS' }),
    },
    pendingGame
  );
  assert.deepEqual(started, { gameId: 9, status: 'IN_PROGRESS' });
});
