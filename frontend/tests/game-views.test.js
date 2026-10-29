import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { createServer } from 'vite';

let server;
let GamesPage;
let SidebarMenu;
let FriendsPage;
let FriendsLists;
let ProfilePage;
let api;
const originalLocalStorage = globalThis.localStorage;
const originalFetch = globalThis.fetch;

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
  ({ default: FriendsLists } = await server.ssrLoadModule(
    '/src/components/FriendsLists.jsx'
  ));
  api = await server.ssrLoadModule('/src/api.js');
  ({ default: ProfilePage } = await server.ssrLoadModule(
    '/src/pages/ProfilePage.jsx'
  ));
});

after(async () => {
  globalThis.fetch = originalFetch;
  globalThis.localStorage = originalLocalStorage;
  await server?.close();
});

test('Friends page loads server data and Add friend opens a dialog instead of an inline form', () => {
  const html = renderToStaticMarkup(createElement(FriendsPage));
  assert.match(html, /<h1>Friends<\/h1>/);
  assert.match(html, /Add friend/);
  assert.match(html, /Loading friends/);
  assert.doesNotMatch(html, /No friends yet/);
  assert.doesNotMatch(
    html,
    /Frontend preview|No friend requests are sent|friend-username/
  );
  assert.match(html, /aria-haspopup="dialog"/);
  assert.match(html, /aria-expanded="false"/);
});

test('friend lists display accepted friends and the correct invitation actions', () => {
  const html = renderToStaticMarkup(
    createElement(
      MemoryRouter,
      null,
      createElement(FriendsLists, {
        friends: [{ username: 'bob' }],
        incoming: [{ requestId: 1, sender: 'carol', recipient: 'alice' }],
        outgoing: [{ requestId: 2, sender: 'alice', recipient: 'dave' }],
        busy: '',
        onAccept: () => {},
        onDismiss: () => {},
      })
    )
  );
  assert.match(html, /Your friends \(1\)/);
  assert.match(html, /Accept friend request from carol/);
  assert.match(html, /Decline friend request from carol/);
  assert.match(html, /Withdraw friend request to dave/);
  const accepted = html.slice(
    html.indexOf('aria-label="Friends"'),
    html.indexOf('id="outgoing-friends-title"')
  );
  assert.match(accepted, /bob/);
  assert.match(accepted, /href="\/profile\/bob"/);
  assert.doesNotMatch(accepted, /carol|dave/);
});

test('friend profile links safely encode usernames', () => {
  const html = renderToStaticMarkup(
    createElement(
      MemoryRouter,
      null,
      createElement(FriendsLists, {
        friends: [{ username: 'bob smith' }],
        incoming: [],
        outgoing: [],
        busy: '',
        onAccept: () => {},
        onDismiss: () => {},
      })
    )
  );
  assert.match(html, /href="\/profile\/bob%20smith"/);
  assert.match(html, /View bob smith’s profile/);
});

test('friend profiles provide navigation back to Friends without changing own-profile navigation', () => {
  const render = (path) =>
    renderToStaticMarkup(
      createElement(
        MemoryRouter,
        { initialEntries: [path] },
        createElement(
          Routes,
          null,
          createElement(Route, {
            path: '/profile/:username',
            element: createElement(ProfilePage),
          }),
          createElement(Route, {
            path: '/profile',
            element: createElement(ProfilePage),
          })
        )
      )
    );
  assert.match(render('/profile/bob'), /href="\/friends"/);
  assert.match(render('/profile/bob'), /Loading profile/);
  assert.doesNotMatch(render('/profile'), /Back to Friends/);
});

test('profile stats API requests the selected friend while defaulting to the current user', async () => {
  const calls = [];
  globalThis.fetch = async (url, options) => {
    calls.push({ url, ...options });
    return new Response(
      JSON.stringify([{ username: JSON.parse(options.body)[0].username }])
    );
  };
  assert.deepEqual(await api.getPlayerStats({ username: 'bob' }), [
    { username: 'bob' },
  ]);
  assert.deepEqual(await api.getPlayerStats(), [{ username: 'alice' }]);
  assert.deepEqual(
    calls.map((call) => JSON.parse(call.body)),
    [[{ username: 'bob' }], [{ username: 'alice' }]]
  );
  assert.ok(
    calls.every(
      (call) => call.method === 'POST' && call.url.endsWith('/api/playerStats')
    )
  );
  assert.ok(
    calls.every((call) => call.headers.Authorization.startsWith('Bearer '))
  );
});

test('friend lists prevent repeated invitation actions while a request is in flight', () => {
  const html = renderToStaticMarkup(
    createElement(FriendsLists, {
      friends: [],
      incoming: [{ requestId: 1, sender: 'bob' }],
      outgoing: [],
      busy: 'accept-1',
      onAccept: () => {},
      onDismiss: () => {},
    })
  );
  assert.match(html, /Accepting/);
  assert.equal([...html.matchAll(/disabled=""/g)].length, 2);
  assert.match(html, /No friends yet/);
  assert.doesNotMatch(html, /Sent requests/);
});

test('friends API sends authenticated list, invitation, acceptance and withdrawal requests', async () => {
  const calls = [];
  const pending = {
    requestId: 12,
    sender: 'alice',
    recipient: 'bob',
    status: 'PENDING',
  };
  const accepted = {
    ...pending,
    status: 'ACCEPTED',
    acceptedAt: '2026-10-29T12:00:00Z',
  };
  const responses = [
    [],
    { incoming: [], outgoing: [] },
    pending,
    accepted,
    null,
  ];
  globalThis.fetch = async (url, options) => {
    calls.push({ path: new URL(url, 'http://localhost').pathname, ...options });
    const response = responses.shift();
    return response === null
      ? new Response(null, { status: 204 })
      : new Response(JSON.stringify(response));
  };
  assert.deepEqual(await api.getFriends(), []);
  assert.deepEqual(await api.getFriendRequests(), {
    incoming: [],
    outgoing: [],
  });
  assert.deepEqual(await api.sendFriendRequest({ username: 'bob' }), pending);
  assert.deepEqual(await api.acceptFriendRequest({ requestId: 12 }), accepted);
  assert.deepEqual(await api.dismissFriendRequest({ requestId: 12 }), []);
  assert.deepEqual(
    calls.map(({ path, method }) => [path, method]),
    [
      ['/api/friends', 'GET'],
      ['/api/friends/requests', 'GET'],
      ['/api/friends/requests', 'POST'],
      ['/api/friends/requests/12/accept', 'POST'],
      ['/api/friends/requests/12', 'DELETE'],
    ]
  );
  assert.deepEqual(JSON.parse(calls[2].body), { username: 'bob' });
  assert.equal(calls[3].body, undefined);
  assert.ok(
    calls.every(({ headers }) => headers.Authorization.startsWith('Bearer '))
  );
});

test('friend request failures preserve useful backend messages without retrying', async () => {
  for (const [status, message] of [
    [404, 'User not found'],
    [409, 'A friend request is pending'],
  ]) {
    let calls = 0;
    globalThis.fetch = async () => {
      calls++;
      return new Response(JSON.stringify({ message }), { status });
    };
    await assert.rejects(
      api.sendFriendRequest({ username: 'bob' }),
      (error) => error.status === status && error.message === message
    );
    assert.equal(calls, 1);
  }
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
