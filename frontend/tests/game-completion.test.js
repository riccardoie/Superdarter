import assert from 'node:assert/strict';
import { test } from 'node:test';
import { isMatchWinningRound } from '../src/gameCompletion.js';

const game = {
  status: 'IN_PROGRESS',
  turn: 'alice',
  legsToWin: 1,
  players: [{ username: 'alice', points: 40, legsWon: 0 }],
};
const dart = (value, multiplier = 1) => ({ value, multiplier });

test('single-leg winning checkout requires match confirmation', () => {
  assert.equal(
    isMatchWinningRound(game, [dart(20, 2), dart(''), dart('')]),
    true
  );
});

test('only a deciding leg requires match confirmation', () => {
  assert.equal(
    isMatchWinningRound({ ...game, legsToWin: 2 }, [dart(20, 2)]),
    false
  );
  assert.equal(
    isMatchWinningRound(
      { ...game, legsToWin: 2, players: [{ ...game.players[0], legsWon: 1 }] },
      [dart(20, 2)]
    ),
    true
  );
});

test('ordinary rounds, misses and busts do not require match confirmation', () => {
  for (const slots of [
    [dart(20)],
    [dart(0)],
    [dart(20, 3)],
    [dart(20), dart(20)],
  ]) {
    assert.equal(isMatchWinningRound(game, slots), false);
  }
});

test('checkout prediction ignores darts after the winning double', () => {
  assert.equal(isMatchWinningRound(game, [dart(20, 2), dart(20, 3)]), true);
});

test('ended games and unknown current players do not request confirmation', () => {
  assert.equal(
    isMatchWinningRound({ ...game, status: 'COMPLETED' }, [dart(20, 2)]),
    false
  );
  assert.equal(
    isMatchWinningRound({ ...game, turn: 'bob' }, [dart(20, 2)]),
    false
  );
});

test('single-out match wins use the selected rule, not double-out prediction', () => {
  for (const gametype of ['301 Single Out', '501 Single Out']) {
    const singleOut = {
      ...game,
      gametype,
      players: [{ username: 'alice', points: 1, legsWon: 0 }],
    };
    assert.equal(isMatchWinningRound(singleOut, [dart(1), dart(20, 3)]), true);
    assert.equal(
      isMatchWinningRound({ ...singleOut, legsToWin: 2 }, [dart(1)]),
      false
    );
    assert.equal(isMatchWinningRound(singleOut, [dart(2)]), false);
    assert.equal(
      isMatchWinningRound({ ...singleOut, gametype: '301' }, [dart(1)]),
      false
    );
  }
});
