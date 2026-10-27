export function matchStats(game) {
  if (!game.legs?.length) return game.players;

  return game.players.map((player) => {
    const stats = game.legs.flatMap((leg) =>
      leg.players.filter((entry) => entry.username === player.username)
    );
    const turns = stats.reduce((total, entry) => total + entry.turns, 0);
    const scored = stats.reduce(
      (total, entry) => total + game.startingPoints - entry.points,
      0
    );
    const detailed = stats.every((entry) => entry.scoringStats != null);
    const scoringStats = detailed
      ? Object.fromEntries(
          [
            'dartsThrown',
            'firstNinePoints',
            'firstNineDarts',
            'score40',
            'score60',
            'score80',
            'score100',
            'score120',
            'score140',
            'score160',
            'score180',
          ].map((key) => [
            key,
            stats.reduce((total, entry) => total + entry.scoringStats[key], 0),
          ])
        )
      : null;
    if (scoringStats) {
      scoringStats.firstNineAverage =
        scoringStats.firstNineDarts > 0
          ? (scoringStats.firstNinePoints * 3) / scoringStats.firstNineDarts
          : 0;
    }
    return {
      ...player,
      scoringStats,
      turns,
      average: turns > 0 ? scored / turns : 0,
      highestScore: Math.max(0, ...stats.map((entry) => entry.highestScore)),
      highestCheckout: Math.max(
        0,
        ...stats.map((entry) => entry.highestCheckout ?? 0)
      ),
      bullseyes: stats.reduce((total, entry) => total + entry.bullseyes, 0),
      triple20s: stats.reduce(
        (total, entry) => total + (entry.triple20s ?? 0),
        0
      ),
    };
  });
}
