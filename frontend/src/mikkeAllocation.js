import { dartInputError } from './dartRound.js';

export function needsAllocation(slot) {
  return (
    slot.value !== '' &&
    Number(slot.value) > 0 &&
    Number(slot.value) !== 25 &&
    Number(slot.multiplier) > 1 &&
    !slot.automaticAllocation &&
    !dartInputError(slot)
  );
}

export function allocationChoices(slot) {
  const bull = Number(slot.value) === 25;
  if (bull)
    return [{ value: 'BULL', label: `Bull (${slot.multiplier} marks)` }];
  return [
    {
      value: bull ? 'BULL' : 'NUMBER',
      label: `${bull ? 'Bull' : slot.value} (${slot.multiplier} marks)`,
    },
    {
      value: Number(slot.multiplier) === 2 ? 'DOUBLE' : 'TREBLE',
      label: Number(slot.multiplier) === 2 ? 'One double' : 'One treble',
    },
  ];
}

export function pendingAllocationIndex(slots) {
  return slots.findIndex((slot) => needsAllocation(slot) && !slot.allocation);
}

// Preview in dart order so a target closed earlier this turn is already closed
// when deciding whether the next dart needs a popup. Never mutate server marks.
export function resolveMikkeAllocations(game, slots) {
  return mikkeRoundPreview(game, slots).slots;
}

export function mikkeRoundPreview(game, slots) {
  const marks = {
    ...game.players.find((player) => player.username === game.turn)?.mikkeMarks,
  };
  const numbers = ['20', '19', '18', '17', '16', '15', '14'];
  const forced = game.gametype === 'Mikke Mus Forced';
  const resolved = slots.map((original) => {
    const slot = { ...original };
    if (slot.automaticAllocation) slot.allocation = null;
    delete slot.automaticAllocation;
    const number = Number(slot.value);
    const multiplier = Number(slot.multiplier);
    const target = number === 25 ? 'B' : String(number);
    const category = multiplier === 2 ? 'D' : 'T';
    if (number === 25) {
      slot.allocation = 'BULL';
    } else if (needsAllocation(slot) && (marks[category] ?? 0) >= 3) {
      slot.allocation = 'NUMBER';
      slot.automaticAllocation = true;
    } else if (needsAllocation(slot) && (marks[target] ?? 0) >= 3) {
      slot.allocation = multiplier === 2 ? 'DOUBLE' : 'TREBLE';
      slot.automaticAllocation = true;
    }
    if (slot.value === '' || dartInputError(slot)) return slot;
    const required = numbers.find((key) => (marks[key] ?? 0) < 3);
    const exact = !required || required === String(number);
    const use =
      slot.allocation ??
      (needsAllocation(slot) ? null : number === 25 ? 'BULL' : 'NUMBER');
    let key;
    let amount = 1;
    if (
      use === 'NUMBER' &&
      numbers.includes(String(number)) &&
      (!forced || exact)
    ) {
      key = String(number);
      amount = multiplier;
    } else if (['DOUBLE', 'TREBLE'].includes(use) && (!forced || exact)) {
      key = use === 'DOUBLE' ? 'D' : 'T';
    } else if (
      use === 'BULL' &&
      (!forced || (!required && marks.D === 3 && marks.T === 3))
    ) {
      key = 'B';
      amount = multiplier;
    }
    if (key) marks[key] = Math.min(3, (marks[key] ?? 0) + amount);
    return slot;
  });
  return {
    slots: resolved,
    marks,
    won:
      [...numbers, 'D', 'T', 'B'].every((key) => (marks[key] ?? 0) >= 3) &&
      pendingAllocationIndex(resolved) < 0 &&
      !resolved.some(dartInputError),
  };
}
