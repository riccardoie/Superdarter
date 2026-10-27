export function sortGames(games) {
  const ended = (game) => ['COMPLETED', 'ABANDONED'].includes(game.status);
  return [...games].sort((a, b) => Number(ended(a)) - Number(ended(b)));
}
