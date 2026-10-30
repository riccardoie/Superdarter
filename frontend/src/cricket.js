import { dartInputError } from './dartRound.js';

export const CRICKET_TARGETS = [20, 19, 18, 17, 16, 15, 25];

// Mirrors the server for score previews and final-round confirmation only.
export function previewCricket(player, opponents, slots) {
  const marks = { ...player.cricketMarks };
  let points = player.points;
  let won = false;
  for (const slot of slots) {
    if (dartInputError(slot)) return null;
    const number = Number(slot.value);
    const multiplier = Number(slot.multiplier);
    if (CRICKET_TARGETS.includes(number)) {
      const before = marks[number] ?? 0;
      const extra = Math.max(0, before + multiplier - 3);
      marks[number] = Math.min(3, before + multiplier);
      if (opponents.some((other) => (other.cricketMarks?.[number] ?? 0) < 3)) {
        points += extra * number;
      }
    }
    won =
      CRICKET_TARGETS.every((target) => (marks[target] ?? 0) === 3) &&
      opponents.every((other) => points >= other.points);
    if (won) break;
  }
  return { points, marks, won, scored: points - player.points };
}

export function cricketRoundPreview(game, slots) {
  const player = game.players.find((entry) => entry.username === game.turn);
  return player
    ? previewCricket(
        player,
        game.players.filter((entry) => entry !== player),
        slots
      )
    : null;
}
