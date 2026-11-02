import { previewCheckout } from './checkout.js';
import { isSingleOut } from './gameSetup.js';
import { cricketRoundPreview } from './cricket.js';
import { mikkeRoundPreview } from './mikkeAllocation.js';
import { clockRoundPreview } from './aroundClock.js';

export function isMatchWinningRound(game, slots) {
  if (game.status !== 'IN_PROGRESS') return false;
  if (game.aroundClock)
    return (
      ['PLAY', 'REDEMPTION'].includes(game.aroundClock.phase) &&
      Boolean(clockRoundPreview(game, slots)?.won)
    );
  if (game.mikkeMus)
    return (
      ['PLAY', 'REDEMPTION'].includes(game.mikkeMus.phase) &&
      mikkeRoundPreview(game, slots).won
    );
  const player = game.players.find((entry) => entry.username === game.turn);
  return Boolean(
    player &&
    (player.legsWon ?? 0) + 1 >= (game.legsToWin ?? 1) &&
    (game.gametype === 'Cricket'
      ? cricketRoundPreview(game, slots)?.won
      : previewCheckout(player.points, slots, !isSingleOut(game.gametype))
          ?.status === 'checkout')
  );
}
