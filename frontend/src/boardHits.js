export const BOARD_NUMBERS = Array.from({ length: 20 }, (_, i) => i + 1).concat(
  25
);

function count(value) {
  return Number.isSafeInteger(value) && value > 0 ? value : 0;
}

export function boardHitStats(boardHits) {
  const rows = BOARD_NUMBERS.map((number) => {
    const hits = boardHits?.[number];
    const singles = count(hits?.singles);
    const doubles = count(hits?.doubles);
    const triples = number === 25 ? 0 : count(hits?.triples);
    return {
      number,
      singles,
      doubles,
      triples,
      total: singles + doubles + triples,
    };
  });
  return {
    rows,
    total: rows.reduce((sum, row) => sum + row.total, 0),
    max: Math.max(
      0,
      ...rows.flatMap((row) => [row.singles, row.doubles, row.triples])
    ),
  };
}

export function heatColor(hits, max) {
  if (!hits || !max) return '#263b46';
  const intensity = Math.min(1, hits / max);
  return `hsl(${Math.round(55 * (1 - intensity))} 90% ${Math.round(72 - 22 * intensity)}%)`;
}
