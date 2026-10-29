export function sortGames(games) {
  const ended = (game) => ['COMPLETED', 'ABANDONED'].includes(game.status);
  return [...games].sort((a, b) => Number(ended(a)) - Number(ended(b)));
}
export function gamesForView(games, completed = false) {
  const statuses = completed
    ? ['COMPLETED', 'ABANDONED']
    : ['PENDING', 'IN_PROGRESS'];
  return games.filter((game) => statuses.includes(game.status));
}
