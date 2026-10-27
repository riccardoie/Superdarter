import { previewCheckout } from './checkout.js';

export function isMatchWinningRound(game, slots) {
  if (game.status !== 'IN_PROGRESS') return false;
  const player = game.players.find((entry) => entry.username === game.turn);
  return Boolean(
    player &&
    (player.legsWon ?? 0) + 1 >= (game.legsToWin ?? 1) &&
    previewCheckout(player.points, slots)?.status === 'checkout'
  );
}
