// Only trailing empty slots are unused darts; gaps would lose throw order.
export function canSubmitRound(slots) {
  const firstEmpty = slots.findIndex((slot) => slot.value === '');
  return firstEmpty === -1 || slots.slice(firstEmpty).every((slot) => slot.value === '');
}

export function serializeRound(slots) {
  return slots
    .filter((slot) => slot.value !== '')
    .map((slot) => `${slot.value} ${slot.multiplier}`)
    .join(' ');
}

export function undoLastDart(slots) {
  const index = slots.findLastIndex((slot) => slot.value !== '');
  return {
    slots: slots.map((slot, i) => i === index ? { value: '', multiplier: 1 } : slot),
    activeDart: Math.max(index, 0),
  };
}
