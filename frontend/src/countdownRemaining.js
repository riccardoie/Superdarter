import { previewCheckout } from './checkout.js';
import { dartInputError } from './dartRound.js';
import { isSingleOut } from './gameSetup.js';

export const TYPING_PAUSE_MS = 800;

export function countdownRemainingPreview(game, slots) {
  if (
    !['301', '501', '301 Single Out', '501 Single Out'].includes(
      game.gametype
    ) ||
    game.status !== 'IN_PROGRESS' ||
    !slots.some((slot) => slot.value !== '') ||
    slots.some(dartInputError)
  )
    return null;
  const player = game.players.find((entry) => entry.username === game.turn);
  return player
    ? previewCheckout(player.points, slots, !isSingleOut(game.gametype))
    : null;
}

export function afterTypingPause(onReady) {
  const timer = setTimeout(onReady, TYPING_PAUSE_MS);
  return () => clearTimeout(timer);
}
