import assert from 'node:assert/strict';
import { test } from 'node:test';
import { sortGames } from '../src/gameOrder.js';

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
