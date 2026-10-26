export function matchFormatLabel(legsToWin = 1) {
  return legsToWin === 1 ? 'Single leg' : `First to ${legsToWin} legs`;
}

export function standingsInStartingOrder(game) {
  const original = game.legs?.[0]?.players ?? game.players;
  const order = new Map(original.map((player, index) => [player.username, index]));
  return [...game.players].sort((a, b) =>
    (order.get(a.username) ?? Infinity) - (order.get(b.username) ?? Infinity)
  );
}
