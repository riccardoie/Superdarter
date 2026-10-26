const MAX_DARTS = 3;
const DOUBLE_ORDER = [20, 16, 8, 10, 12, 18, 4, 6, 2, 14, 15, 17, 19, 1, 3, 5, 7, 9, 11, 13, 25];
const TARGETS = [];
for (const multiplier of [1, 2, 3]) {
  for (let number = 20; number >= 1; number--) {
    TARGETS.push({ number, multiplier, points: number * multiplier,
      label: `${['', 'S', 'D', 'T'][multiplier]}${number}` });
  }
}
TARGETS.push({ number: 25, multiplier: 1, points: 25, label: '25' });
TARGETS.push({ number: 25, multiplier: 2, points: 50, label: 'Bull' });

function difficulty(route) {
  return route.filter((dart) => dart.multiplier === 3 || dart.number === 25).length;
}

function prefer(candidate, current) {
  if (!current) return true;
  const difference = difficulty(candidate) - difficulty(current);
  if (difference !== 0) return difference < 0;
  return DOUBLE_ORDER.indexOf(candidate.at(-1).number) <
    DOUBLE_ORDER.indexOf(current.at(-1).number);
}

// Search shortest routes first. All intermediate scores must remain above one.
export function findCheckout(points, dartsLeft = MAX_DARTS) {
  if (!Number.isInteger(points) || points < 2 || points > 170 ||
      !Number.isInteger(dartsLeft) || dartsLeft < 1 || dartsLeft > MAX_DARTS) return null;

  const memo = new Map();
  function search(remaining, count) {
    const key = `${remaining}:${count}`;
    if (memo.has(key)) return memo.get(key);
    let best = null;
    for (const dart of TARGETS) {
      const next = remaining - dart.points;
      let route = null;
      if (count === 1) {
        if (next === 0 && dart.multiplier === 2) route = [dart];
      } else if (next > 1) {
        const tail = search(next, count - 1);
        if (tail) route = [dart, ...tail];
      }
      if (route && prefer(route, best)) best = route;
    }
    memo.set(key, best);
    return best;
  }
  for (let count = 1; count <= dartsLeft; count++) {
    const route = search(points, count);
    if (route) return route;
  }
  return null;
}

// Preview only consecutive entered darts, never treat unused slots as misses.
export function previewCheckout(points, slots) {
  if (!Number.isInteger(points) || points < 2) return null;
  let remaining = points;
  let thrown = 0;
  for (const slot of slots) {
    if (slot.value === '') break;
    const number = Number(slot.value);
    const multiplier = Number(slot.multiplier);
    remaining -= number * multiplier;
    thrown++;
    if (remaining < 0 || remaining === 1 || (remaining === 0 && multiplier !== 2)) {
      return { status: 'bust', remaining: points, dartsLeft: 0, route: null };
    }
    if (remaining === 0) {
      return { status: 'checkout', remaining: 0, dartsLeft: MAX_DARTS - thrown, route: null };
    }
  }
  const dartsLeft = MAX_DARTS - thrown;
  return { status: dartsLeft === 0 ? 'complete' : 'playing', remaining, dartsLeft,
    route: findCheckout(remaining, dartsLeft) };
}
