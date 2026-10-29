import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import { createServer } from 'vite';

let server;
let GamesPage;
let SidebarMenu;

before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false } });
  ({ default: GamesPage } = await server.ssrLoadModule(
    '/src/pages/GamesPage.jsx'
  ));
  ({ default: SidebarMenu } = await server.ssrLoadModule(
    '/src/components/SidebarMenu.jsx'
  ));
});

after(async () => {
  await server?.close();
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
