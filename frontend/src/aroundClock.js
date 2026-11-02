import { dartInputError } from './dartRound.js';

export function clockTargetLabel(target) {
  return Number(target) === 0
    ? 'Finished'
    : Number(target) === 21
      ? 'Bull'
      : String(target);
}

export function clockRoundPreview(game, slots) {
  const player = game.players.find((entry) => entry.username === game.turn);
  if (!player || slots.some(dartInputError)) return null;
  let target = Number(player.points);
  for (const slot of slots) {
    if (slot.value === '' || target === 0) continue;
    const number = Number(slot.value);
    if (target === 21) {
      if (number === 25) target = 0;
    } else if (number === target) {
      target = Math.min(21, target + Number(slot.multiplier));
    }
  }
  return { target, won: target === 0 };
}
