// Only trailing empty slots are unused darts; gaps would lose throw order.
export function canSubmitRound(slots) {
  const firstEmpty = slots.findIndex((slot) => slot.value === '');
  return slots[0].value !== '' &&
    (firstEmpty === -1 || slots.slice(firstEmpty).every((slot) => slot.value === ''));
}

export function serializeRound(slots) {
  return slots
    .filter((slot) => slot.value !== '')
    .map((slot) => `${slot.value} ${slot.multiplier}`)
    .join(' ');
}
