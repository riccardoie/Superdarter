import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { createServer } from 'vite';

let server;
let GamesPage;
let SidebarMenu;
let FriendsPage;
const originalLocalStorage = globalThis.localStorage;

before(async () => {
  const token = `header.${Buffer.from(JSON.stringify({ sub: 'alice' })).toString('base64url')}.signature`;
  globalThis.localStorage = { getItem: () => token };
  server = await createServer({ server: { middlewareMode: true, hmr: false } });
  ({ default: GamesPage } = await server.ssrLoadModule(
    '/src/pages/GamesPage.jsx'
  ));
  ({ default: SidebarMenu } = await server.ssrLoadModule(
    '/src/components/SidebarMenu.jsx'
  ));
  ({ default: FriendsPage } = await server.ssrLoadModule(
    '/src/pages/FriendsPage.jsx'
  ));
});

after(async () => {
  globalThis.localStorage = originalLocalStorage;
  await server?.close();
});

test('Friends page starts empty and Add friend opens a dialog instead of an inline form', () => {
  const html = renderToStaticMarkup(createElement(FriendsPage));
  assert.match(html, /<h1>Friends<\/h1>/);
  assert.match(html, /Add friend/);
  assert.match(html, /No friends yet/);
  assert.doesNotMatch(
    html,
    /Frontend preview|No friend requests are sent|friend-username/
  );
  assert.match(html, /aria-haspopup="dialog"/);
  assert.match(html, /aria-expanded="false"/);
});

test('Friends is available through its own menu link', () => {
  const html = renderToStaticMarkup(
    createElement(
      MemoryRouter,
      {
        initialEntries: ['/friends'],
      },
      createElement(SidebarMenu, { isOpen: true, onClose: () => {} })
    )
  );
  assert.match(html, /aria-current="page"[^>]*href="\/friends"/);
  assert.match(html, />Friends<\/a>/);
});

test('the completed games page has its own title and no game creation controls', () => {
  const render = (completed) =>
    renderToStaticMarkup(
      createElement(MemoryRouter, null, createElement(GamesPage, { completed }))
    );
  const active = render(false);
  const completed = render(true);
  assert.match(active, /<h1>Games<\/h1>/);
  assert.match(active, /Create game/);
  assert.match(completed, /<h1>Completed games<\/h1>/);
  assert.doesNotMatch(completed, /Create game/);
});

test('the sidebar links to completed games separately from active games', () => {
  const html = renderToStaticMarkup(
    createElement(
      MemoryRouter,
      {
        initialEntries: ['/completed-games'],
      },
      createElement(SidebarMenu, { isOpen: true, onClose: () => {} })
    )
  );
  assert.match(html, /href="\/games"/);
  assert.match(html, /aria-current="page"[^>]*href="\/completed-games"/);
  assert.match(html, /Completed games/);
});
