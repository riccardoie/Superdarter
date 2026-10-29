export function dartInputError(slot) {
  if (slot.value === '') return '';
  if (!/^(?:[0-9]|1[0-9]|20|25)$/.test(String(slot.value))) {
    return 'Enter a whole number from 1 to 20, or 25.';
  }
  const max = Number(slot.value) === 0 ? 1 : Number(slot.value) === 25 ? 2 : 3;
  if (
    !/^[1-3]$/.test(String(slot.multiplier)) ||
    Number(slot.multiplier) > max
  ) {
    return `Enter a multiplier from 1 to ${max}.`;
  }
  return '';
}

// Blank slots are misses; only the scoring rules can end a turn early at checkout.
export function canSubmitRound(slots) {
  return !slots.some((slot) => dartInputError(slot));
}

export function serializeRound(slots) {
  return slots
    .map((slot) =>
      slot.value === '' ? '0 1' : `${slot.value} ${slot.multiplier}`
    )
    .join(' ');
}

export function undoLastDart(slots) {
  const index = slots.findLastIndex((slot) => slot.value !== '');
  return {
    slots: slots.map((slot, i) =>
      i === index ? { value: '', multiplier: 1 } : slot
    ),
    activeDart: Math.max(index, 0),
  };
}
