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
    return {
      ...player,
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
