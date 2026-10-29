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

// Only trailing empty slots are unused darts; gaps would lose throw order.
export function canSubmitRound(slots) {
  if (slots.some((slot) => dartInputError(slot))) return false;
  const firstEmpty = slots.findIndex((slot) => slot.value === '');
  return (
    firstEmpty === -1 ||
    slots.slice(firstEmpty).every((slot) => slot.value === '')
  );
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
    slots: slots.map((slot, i) =>
      i === index ? { value: '', multiplier: 1 } : slot
    ),
    activeDart: Math.max(index, 0),
  };
}
