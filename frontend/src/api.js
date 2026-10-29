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
  if (res.status === 401 && auth) {
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

export function getGames({ gamemode } = {}) {
  const query = gamemode ? `?gametype=${encodeURIComponent(gamemode)}` : '';
  return request(`/api/games${query}`);
}

export function getAllGamemodes() {
  return request('/api/gamemode');
}

export function getFriends() {
  return request('/api/friends');
}

export function getFriendRequests() {
  return request('/api/friends/requests');
}

export function sendFriendRequest({ username }) {
  return request('/api/friends/requests', {
    method: 'POST',
    body: { username },
  });
}

export function acceptFriendRequest({ requestId }) {
  return request(`/api/friends/requests/${requestId}/accept`, {
    method: 'POST',
  });
}

export function dismissFriendRequest({ requestId }) {
  return request(`/api/friends/requests/${requestId}`, { method: 'DELETE' });
}

// Default to the signed-in player; profile links can request another account's stats.
export function getPlayerStats({ username } = {}) {
  const token = localStorage.getItem('token');
  const player = username ?? getUsername(token);
  return request('/api/playerStats', {
    method: 'POST',
    body: [{ username: player }],
  });
}

export function startGame({ gameId }) {
  return request(`/api/games/${gameId}/start`, { method: 'POST' });
}

export function rematchGame({ gameId }) {
  return request(`/api/games/${gameId}/rematch`, { method: 'POST' });
}

export function createGame({
  gametype,
  players = [],
  guests = [],
  legsToWin = 1,
}) {
  return request('/api/games', {
    method: 'POST',
    body: {
      gametype,
      legsToWin,
      players: players.map((username) => ({ username })),
      ...(guests.length ? { guests } : {}),
    },
  });
}

export function abandonGame({ gameId }) {
  return request(`/api/games/${gameId}/abandon`, { method: 'POST' });
}

export function deleteGame({ gameId }) {
  return request(`/api/games/${gameId}`, { method: 'DELETE' });
}

export const MAX_TOTAL_PLAYERS = 10;

// A single authoritative response works equally during play and after a browser reload.
export function getGameState({ gameId }) {
  return request(`/api/games/${gameId}`);
}

export function playRound({ gameId, username, score, legId, revision }) {
  return request(`/api/games/${gameId}/rounds`, {
    method: 'POST',
    body: { username, score, legId, revision },
  });
}

export function undoRound({ gameId, revision }) {
  return request(`/api/games/${gameId}/undo`, {
    method: 'POST',
    body: { revision },
  });
}

export function getUsername(token) {
  const payload = jwtDecode(token);
  return payload.sub;
}
