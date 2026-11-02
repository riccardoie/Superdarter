import assert from 'node:assert/strict';
import { before, after, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';
import {
  afterTypingPause,
  countdownRemainingPreview,
  TYPING_PAUSE_MS,
} from '../src/countdownRemaining.js';

const dart = (value, multiplier = 1) => ({ value, multiplier });
const game = (gametype = '301', points = 301) => ({
  gameId: 1,
  currentLegId: 2,
  revision: 4,
  gametype,
  status: 'IN_PROGRESS',
  turn: 'alice',
  players: [
    { username: 'alice', points },
    { username: 'bob', points: 501 },
  ],
});

test('paused preview supports 301 and 501 with either finishing rule', () => {
  for (const gametype of ['301', '501', '301 Single Out', '501 Single Out']) {
    const start = gametype.startsWith('301') ? 301 : 501;
    const preview = countdownRemainingPreview(game(gametype, start), [
      dart(20, 3),
      dart(''),
      dart(''),
    ]);
    assert.equal(preview.remaining, start - 60);
    assert.equal(preview.dartsLeft, 2);
  }
});

test('preview reflects partial rounds, edited multipliers, misses and interior gaps', () => {
  assert.equal(
    countdownRemainingPreview(game(), [dart('20'), dart('')]).remaining,
    281
  );
  assert.equal(
    countdownRemainingPreview(game(), [dart('20', 3), dart('')]).remaining,
    241
  );
  assert.equal(
    countdownRemainingPreview(game(), [dart('20', 3), dart(19, 3), dart('')])
      .remaining,
    184
  );
  assert.equal(
    countdownRemainingPreview(game(), [dart(''), dart(20, 3), dart(0)])
      .remaining,
    241
  );
});

test('busts retain the turn starting score and checkouts stop on the winning dart', () => {
  for (const slots of [[dart(20)], [dart(19)], [dart(20, 3)]]) {
    const preview = countdownRemainingPreview(game('301', 20), slots);
    assert.equal(preview.status, 'bust');
    assert.equal(preview.remaining, 20);
  }
  assert.equal(
    countdownRemainingPreview(game('301', 40), [dart(20, 2), dart(20, 3)])
      .remaining,
    0
  );
  assert.equal(
    countdownRemainingPreview(game('301 Single Out', 20), [dart(19)]).remaining,
    1
  );
  assert.equal(
    countdownRemainingPreview(game('501 Single Out', 20), [dart(20)]).status,
    'checkout'
  );
});

test('blank, invalid, inactive and non-countdown inputs have no remaining-score preview', () => {
  assert.equal(countdownRemainingPreview(game(), [dart(''), dart('')]), null);
  for (const slot of [dart('2x'), dart(99), dart(25, 3), dart(20, 4)]) {
    assert.equal(countdownRemainingPreview(game(), [slot]), null);
  }
  for (const gametype of [
    'Cricket',
    'Mikke Mus Forced',
    'Mikke Mus Relaxed',
    'Around the Clock',
  ]) {
    assert.equal(countdownRemainingPreview(game(gametype), [dart(20)]), null);
  }
  for (const status of ['PENDING', 'COMPLETED', 'ABANDONED']) {
    assert.equal(
      countdownRemainingPreview({ ...game(), status }, [dart(20)]),
      null
    );
  }
  assert.equal(
    countdownRemainingPreview({ ...game(), turn: 'unknown' }, [dart(20)]),
    null
  );
});

test('remaining-score preview waits for the full typing pause', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  let shown = false;
  afterTypingPause(() => {
    shown = true;
  });
  t.mock.timers.tick(TYPING_PAUSE_MS - 1);
  assert.equal(shown, false);
  t.mock.timers.tick(1);
  assert.equal(shown, true);
});

test('edits cancel the old timer and restart the pause; unmounting cancels pending previews', (t) => {
  t.mock.timers.enable({ apis: ['setTimeout'] });
  const results = [];
  const cancelFirst = afterTypingPause(() => results.push('old'));
  t.mock.timers.tick(500);
  cancelFirst();
  const cancelSecond = afterTypingPause(() => results.push('new'));
  t.mock.timers.tick(TYPING_PAUSE_MS - 1);
  assert.deepEqual(results, []);
  t.mock.timers.tick(1);
  assert.deepEqual(results, ['new']);
  cancelSecond();
  const cancelUnmounted = afterTypingPause(() => results.push('unmounted'));
  cancelUnmounted();
  t.mock.timers.tick(TYPING_PAUSE_MS);
  assert.deepEqual(results, ['new']);
});

let server;
let CountdownRemainingPreview;
before(async () => {
  server = await createServer({
    server: { middlewareMode: true, hmr: false, ws: false },
  });
  ({ default: CountdownRemainingPreview } = await server.ssrLoadModule(
    '/src/components/CountdownRemainingPreview.jsx'
  ));
});
after(async () => {
  await server?.close();
});

test('remaining preview is initially hidden and reserves a small status line', () => {
  const html = renderToStaticMarkup(
    createElement(CountdownRemainingPreview, {
      game: game(),
      slots: [dart(20, 3)],
    })
  );
  assert.match(html, /game-round-remaining/);
  assert.match(html, /role="status"/);
  assert.doesNotMatch(html, /Remaining:|241/);
});
