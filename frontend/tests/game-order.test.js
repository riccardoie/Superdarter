import assert from 'node:assert/strict';
import { test } from 'node:test';
import { gamesForView, sortGames } from '../src/gameOrder.js';

test('completed and abandoned games follow active games without changing group order', () => {
  const games = [
    { gameId: 1, status: 'ABANDONED' },
    { gameId: 2, status: 'PENDING' },
    { gameId: 3, status: 'COMPLETED' },
    { gameId: 4, status: 'IN_PROGRESS' },
    { gameId: 5, status: 'ABANDONED' },
  ];
  const original = [...games];

  assert.deepEqual(
    sortGames(games).map((game) => game.gameId),
    [2, 4, 1, 3, 5]
  );
  assert.deepEqual(games, original);
});

test('an empty games list stays empty', () => {
  assert.deepEqual(sortGames([]), []);
});

test('Games shows only pending and in-progress games; Completed games shows all ended games', () => {
  const games = [
    { gameId: 1, status: 'ABANDONED' },
    { gameId: 2, status: 'PENDING' },
    { gameId: 3, status: 'COMPLETED' },
    { gameId: 4, status: 'IN_PROGRESS' },
    { gameId: 5, status: 'COMPLETED' },
  ];
  const original = [...games];
  assert.deepEqual(
    gamesForView(games).map((game) => game.gameId),
    [2, 4]
  );
  assert.deepEqual(
    gamesForView(games, true).map((game) => game.gameId),
    [1, 3, 5]
  );
  assert.deepEqual(games, original);
});

test('both game views handle empty lists and lists containing only the other category', () => {
  assert.deepEqual(gamesForView([]), []);
  assert.deepEqual(gamesForView([], true), []);
  assert.deepEqual(gamesForView([{ status: 'COMPLETED' }]), []);
  assert.deepEqual(gamesForView([{ status: 'IN_PROGRESS' }], true), []);
});
