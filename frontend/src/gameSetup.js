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
