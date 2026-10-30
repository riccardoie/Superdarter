import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { createServer } from 'vite';

let server;
let DashboardContent;
let HomePage;
let AuthContext;
const originalStorage = globalThis.localStorage;

before(async () => {
  globalThis.localStorage = {
    getItem: () =>
      `header.${Buffer.from(JSON.stringify({ sub: 'alice' })).toString('base64url')}.signature`,
  };
  server = await createServer({
    server: { middlewareMode: true, hmr: false, ws: false },
  });
  ({ DashboardContent } = await server.ssrLoadModule(
    '/src/components/HomeDashboard.jsx'
  ));
  ({ default: HomePage } = await server.ssrLoadModule(
    '/src/pages/HomePage.jsx'
  ));
  ({ AuthContext } = await server.ssrLoadModule('/src/context/AuthContext.js'));
});
after(async () => {
  globalThis.localStorage = originalStorage;
  await server?.close();
});

const game = (gameId, status, createdAt = '2026-10-30T10:00:00Z') => ({
  gameId,
  status,
  createdAt,
  gametype: '501',
  legsToWin: 1,
  players: [{ username: 'alice', legsWon: 0 }],
});
const render = (props = {}) =>
  renderToStaticMarkup(
    createElement(
      MemoryRouter,
      null,
      createElement(DashboardContent, {
        username: 'alice',
        games: [],
        stats: null,
        onCreate() {},
        onRetry() {},
        ...props,
      })
    )
  );

test('logged-in dashboard has personal greeting, creation action and useful destinations', () => {
  const html = render();
  assert.match(html, /Welcome back/);
  assert.match(html, /alice/);
  assert.match(html, /New game/);
  for (const path of ['/profile', '/games', '/friends'])
    assert.ok(html.includes(`href="${path}"`));
  assert.doesNotMatch(html, /Join Superdarter|Professional darts player/);
});

test('dashboard displays real stats, keeps zero values and formats averages', () => {
  const html = render({
    stats: {
      playedMatches: 12,
      wonMatches: 0,
      avgPoints: 42.125,
      highestCheckout: 80,
    },
  });
  for (const value of ['12', '0', '42.13', '80'])
    assert.ok(html.includes(`<dd>${value}</dd>`));
});

test('dashboard welcome uses the webp photo instead of the next-up badge', () => {
  const html = render();
  assert.match(html, /stephen-bunting-2-68db7e7d2794d.webp/);
  assert.match(html, /alt="Darts player celebrating"/);
  assert.doesNotMatch(html, /Next up|See you at the oche|dashboard-target/);
});

test('empty and loading states do not invent games or stats', () => {
  assert.match(render(), /No active games right now/);
  assert.match(render(), /Not available/);
  const loading = render({ games: null, stats: undefined });
  assert.match(loading, /Loading games/);
  assert.match(loading, /Loading statistics/);
  assert.doesNotMatch(loading, /No active games/);
});

test('active and historical games are separate and history is newest first', () => {
  const html = render({
    games: [
      game(1, 'IN_PROGRESS'),
      game(2, 'COMPLETED', '2026-10-01'),
      game(3, 'ABANDONED'),
      game(4, 'PENDING'),
    ],
  });
  assert.ok(
    html.indexOf('href="/games/1"') < html.indexOf('Last time at the oche')
  );
  assert.ok(
    html.indexOf('href="/games/4"') < html.indexOf('Last time at the oche')
  );
  assert.ok(html.indexOf('href="/games/3"') < html.indexOf('href="/games/2"'));
  assert.match(html, /href="\/completed-games"/);
});

test('each data section handles failure independently with a retry', () => {
  const html = render({ gamesError: true, stats: { avgPoints: 30 } });
  assert.match(html, /Couldn’t load your games/);
  assert.match(html, /Retry/);
  assert.match(html, /30.00/);
  assert.doesNotMatch(html, /No active games/);
  assert.match(render({ statsError: true }), /Couldn’t load your statistics/);
});

test('home page switches between public landing page and authenticated dashboard', () => {
  const home = (isAuthenticated) =>
    renderToStaticMarkup(
      createElement(
        MemoryRouter,
        null,
        createElement(
          AuthContext.Provider,
          { value: { isAuthenticated } },
          createElement(HomePage)
        )
      )
    );
  assert.match(home(false), /Join Superdarter/);
  assert.doesNotMatch(home(false), /Welcome back/);
  assert.match(home(true), /Welcome back/);
  assert.doesNotMatch(home(true), /Join Superdarter/);
});
