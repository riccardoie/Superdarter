import { jwtDecode } from 'jwt-decode';

const API_BASE_URL = (import.meta.env.VITE_API_URL || '').replace(/\/$/, '');

let unauthorizedHandler = null;

export function setUnauthorizedHandler(fn) {
  unauthorizedHandler = fn;
}

async function request(path, { method = 'GET', body, auth = true } = {}) {
  const token = localStorage.getItem('token');
  const headers = { 'Content-Type': 'application/json' };
  if (auth && token) headers.Authorization = `Bearer ${token}`;
  const res = await fetch(`${API_BASE_URL}${path}`, {
    method,
    headers,
    body: body ? JSON.stringify(body) : undefined,
  });
  if (res.status === 401  && auth) {
    unauthorizedHandler?.();
    throw new Error('Unauthorized');
  }
  if (!res.ok) {
    // Errors use the ErrorResponse shape ({timestamp,status,error,message});
    // fall back to the raw body for the bodyless 401/403 from the security
    // filter chain, which never reaches the backend exception handler.
    const raw = await res.text().catch(() => '');
    let message = raw;
    try {
      const parsed = JSON.parse(raw);
      if (parsed?.message) message = parsed.message;
    } catch {
      // not JSON - keep the raw text
    }
    const err = new Error(message || `Request failed: ${res.status}`);
    err.status = res.status;
    throw err;
  }
  // 204 (and void 200s) carry no body, so res.json() would reject on an empty
  // stream. Return [] so list-shaped callers render an empty list rather than
  // hanging on a null state.
  const text = await res.text();
  return text ? JSON.parse(text) : [];
}

export function register({ username, password, email }) {
  return request('/api/auth/register', {
    method: 'POST',
    body: { username, password, email },
    auth: false,
  });
}

export function login({ username, password }) {
  return request('/api/auth/login', {
    method: 'POST',
    body: { username, password },
    auth: false,
  });
}

export function getSessions({ gamemode } = {}) {
  const token = localStorage.getItem('token');
  const username = getUsername(token);
  const gamemodeQuery = gamemode ? `&gameRequest=${encodeURIComponent(gamemode)}` : '';
  return request(`/api/session?username=${encodeURIComponent(username)}${gamemodeQuery}`);
}

export function getAllGamemodes() {
  return request('/api/gamemode');
}

// Fetch cumulative stats for the signed-in player identified by the JWT subject.
export function getPlayerStats() {
  const token = localStorage.getItem('token');
  const username = getUsername(token);
  return request('/api/playerStats', {
    method: 'POST',
    body: [{ username }],
  });
}

export function getSessionById(id) {
  return request(`/api/session/${id}`);
}

// Latest real GameResponse per game (turn/isFinished/gametype), written ONLY from
// real backend responses (startGame/playRound). Resets on page reload.
const gameResponses = {};

export async function startGame({ sessionId, gameType }) {
  const res = await request(`/api/session/${sessionId}/games?gametype=${gameType}`);
  gameResponses[res.gameId] = res;
  return res;
}

export async function createSession({ gamemodes, players = [] }) {
  return request('/api/session', {
    method: 'POST',
    body: {
      games: gamemodes.map((gamemode) => ({ gameType: gamemode })),
      players: players.map((username) => ({ username })),
    },
  });
}

// Requires being owner and session not finished Body is an array
// of GameRequests. Returns the FULL updated SessionResponse
export async function addGamesToSession({ sessionId, gamemodes }) {
  return request(`/api/session/${sessionId}`, {
    method: 'POST',
    body: gamemodes.map((gameType) => ({ gameType })),
  });
}

// Finish a session. Owner-only (403), 404 if unknown, 200 with an EMPTY body.
export function finishSession({ sessionId }) {
  return request(`/api/session/${sessionId}`, { method: 'PUT' });
}

// Delete a session permanently. Owner-only while the session is active (403),
// 404 if unknown, 204 with an EMPTY body. Cascades to its games and stats.
export function deleteSession({ sessionId }) {
  return request(`/api/session/${sessionId}`, { method: 'DELETE' });
}

// Delete the next UNSTARTED game of `gamemode` from a session
// the backend picks the first unstarted game of that type
// 404 if no unstarted game of that type is left.
export function deleteGame({ sessionId, gamemode }) {
  return request(
    `/api/session/${sessionId}/games?gametype=${encodeURIComponent(gamemode)}`,
    { method: 'DELETE' }
  );
}

export const MAX_TOTAL_PLAYERS = 10;

export async function getFinishedGames({ sessionId }) {
  return request(`/api/session/${sessionId}/finished-games`);
}

function mapStats(stats) {
  return stats.map((s) => ({
    username: s.userName,
    points: s.points,
    turns: s.turns,
    bullseyes: s.bullseyes,
    triple20s: s.triple20s,
    highestScore: s.highestScore,
    highestCheckout: s.highestCheckout,
    checkoutAccuracy: s.checkoutAccuracy,
  }));
}

// Fetch current game state: per-player stats from the real stats endpoint;
// turn/isFinished/gametype from the most recent real GameResponse while
// playing, or from the session's finished-games list after a reload.
export async function getGameState({ sessionId, gameId }) {
  const id = Number(gameId);
  const stats = await request(`/api/session/${sessionId}/games/${id}/stats`);
  const meta = gameResponses[id] ?? {};

  if (meta.turn) {
    // Live path (played in this page session): GameResponse cache is warm.
    return {
      gameId: id,
      gametype: meta.gametype,
      startingPoints: stats[0]?.startingPoints,
      isFinished: Boolean(meta.isFinished),
      winner: meta.isFinished ? meta.turn : null,
      turn: meta.turn,
      players: mapStats(stats),
    };
  }

  // Reload path: no cached GameResponse — derive finished/winner from the
  // session's finished-games list; turn = winner (finished) or the player
  // with the fewest turns (in-progress, best-effort).
  const finished = await getFinishedGames({ sessionId });
  const finishedGame = finished.find((f) => Number(f.gameId) === id);
  const minTurns = Math.min(...stats.map((s) => s.turns));
  const turn = stats.find((s) => s.turns === minTurns)?.userName;
  return {
    gameId: id,
    gametype: finishedGame?.gametype ?? meta.gametype,
    startingPoints: stats[0]?.startingPoints,
    isFinished: Boolean(finishedGame),
    winner: finishedGame?.winner ?? null,
    turn: finishedGame?.winner ?? turn,
    players: mapStats(stats),
  };
}

// Submit a round of 3 darts — REAL backend call. The response carries the next
// turn / finished state; the scoreboard is refreshed via getGameState after.
export async function playRound({ sessionId, gameId, username, score }) {
  const res = await request(`/api/session/${sessionId}/games`, {
    method: 'PUT',
    body: { username, score, gameId },
  });
  gameResponses[res.gameId] = res;
  return res;
}

export function getUsername(token) {
  const payload = jwtDecode(token);
  return payload.sub;
}
