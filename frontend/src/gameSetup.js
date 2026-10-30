// The API exposes each finishing rule as a separate gametype. Group only the
// countdown modes that this client can play, keeping the exact API identifiers.
export function countdownModes(gametypes) {
  const modes = new Map();
  for (const { gamemode } of gametypes) {
    const match = /^(301|501)( Single Out)?$/.exec(gamemode);
    if (!match) continue;
    const base = match[1];
    if (!modes.has(base)) modes.set(base, { gamemode: base, finishes: {} });
    modes.get(base).finishes[match[2] ? 'single' : 'double'] = gamemode;
  }
  return [...modes.values()];
}

export function isSingleOut(gametype) {
  return gametype === '301 Single Out' || gametype === '501 Single Out';
}

export function playableModes(gametypes) {
  const modes = countdownModes(gametypes);
  if (gametypes.some(({ gamemode }) => gamemode === 'Cricket')) {
    modes.push({ gamemode: 'Cricket', finishes: {} });
  }
  return modes;
}

export function playerNameError(
  name,
  { owner, players, guests, maxPlayers, friends }
) {
  if (!name) return 'Enter a player name';
  if (name.length > 255) return 'Player names must be at most 255 characters';
  if (name === owner) return 'That is you — you are already in the game';
  if (players.includes(name) || guests.includes(name))
    return 'That name is already added';
  if (players.length + guests.length + 1 >= maxPlayers) {
    return `Max ${maxPlayers} players in total (including you and guests)`;
  }
  if (friends && !friends.some((friend) => friend.username === name)) {
    return 'Only accepted friends can be added as registered players.';
  }
  return '';
}

export function gameFriendSuggestions(
  friends,
  input,
  { owner, players, guests, maxPlayers }
) {
  if (players.length + guests.length + 1 >= maxPlayers) return [];
  const selected = new Set([owner, ...players, ...guests]);
  const query = input.trim().toLowerCase();
  return friends
    .filter(
      (friend) =>
        !selected.has(friend.username) &&
        friend.username.toLowerCase().includes(query)
    )
    .map((friend) => friend.username)
    .sort((a, b) => a.localeCompare(b));
}

// Creation is deferred until Start. If starting fails, remove the pending game.
// Retain its ID only if cleanup fails, so retries never create duplicate games.
export async function startDraftGame(config, api, pendingGame = null) {
  const game = pendingGame ?? (await api.createGame(config));
  try {
    return await api.startGame({ gameId: game.gameId });
  } catch (cause) {
    const error = new Error(`Failed to start game: ${cause.message}`, {
      cause,
    });
    try {
      await api.deleteGame({ gameId: game.gameId });
    } catch {
      error.pendingGame = game;
      error.message += ' Retry starting, or cancel to remove the pending game.';
    }
    throw error;
  }
}
