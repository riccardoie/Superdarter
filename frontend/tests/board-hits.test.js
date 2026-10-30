import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createElement } from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { createServer } from 'vite';
import { boardHitStats, heatColor } from '../src/boardHits.js';
import { SEGMENTS, point, arcPath } from '../src/dartboardGeometry.js';

const fixture = {
  20: { total: 7, singles: 4, doubles: 2, triples: 1 },
  25: { total: 3, singles: 1, doubles: 2, triples: 0 },
  0: { total: 10, singles: 10 },
};

test('board totals exclude misses and count darts, not points or duplicate single regions', () => {
  const stats = boardHitStats(fixture);
  assert.equal(stats.total, 10);
  assert.equal(stats.max, 4);
  assert.equal(stats.rows.length, 21);
  assert.deepEqual(
    stats.rows.find((row) => row.number === 20),
    {
      number: 20,
      singles: 4,
      doubles: 2,
      triples: 1,
      total: 7,
    }
  );
  assert.deepEqual(stats.rows[0], {
    number: 1,
    singles: 0,
    doubles: 0,
    triples: 0,
    total: 0,
  });
});

test('missing, empty and malformed data remain zero, and triple bull is excluded', () => {
  for (const hits of [undefined, null, {}]) {
    const stats = boardHitStats(hits);
    assert.equal(stats.total, 0);
    assert.equal(stats.max, 0);
    assert.equal(stats.rows.length, 21);
  }
  assert.equal(
    boardHitStats({
      1: { singles: -1, doubles: '2', triples: NaN },
      25: { triples: 10 },
    }).total,
    0
  );
});

test('heat colours have a neutral zero and a shared relative scale', () => {
  assert.equal(heatColor(0, 4), '#263b46');
  assert.equal(heatColor(0, 0), '#263b46');
  assert.equal(heatColor(4, 4), 'hsl(0 90% 50%)');
  assert.equal(heatColor(2, 4), heatColor(1, 2));
  assert.notEqual(heatColor(1, 4), heatColor(2, 4));
});

test('shared geometry preserves standard board ordering and orientation', () => {
  assert.equal(SEGMENTS.length, 20);
  assert.equal(new Set(SEGMENTS).size, 20);
  assert.equal(SEGMENTS[0], 20);
  assert.deepEqual(point(80, -90), { x: 110, y: 30 });
  assert.match(arcPath(72, 80, -99, -81), /A 80 80/);
});

let server;
let ProfileBoardHits;
let BoardHitMap;
let BoardHitNumbers;

before(async () => {
  server = await createServer({ server: { middlewareMode: true, hmr: false } });
  ({
    default: ProfileBoardHits,
    BoardHitMap,
    BoardHitNumbers,
  } = await server.ssrLoadModule('/src/components/ProfileBoardHits.jsx'));
});

after(async () => {
  await server?.close();
});

test('profile board keeps total, legend and view options without explanatory text', () => {
  const html = renderToStaticMarkup(
    createElement(ProfileBoardHits, { boardHits: fixture, username: 'alice' })
  );
  assert.match(html, /alice’s board hits/);
  assert.match(html, /<strong>10<\/strong> darts hit the board/);
  assert.match(html, /aria-pressed="true">Dartboard/);
  assert.match(html, /aria-pressed="false">Numbers/);
  assert.match(html, /No hits/);
  assert.match(html, /Most hits/);
  assert.doesNotMatch(html, /board-hit-note|Recorded since|regions share/);
});

test('heat map maps each multiplier and both bull regions to the correct colour', () => {
  const html = renderToStaticMarkup(
    createElement(BoardHitMap, boardHitStats(fixture))
  );
  assert.equal((html.match(/<path /g) ?? []).length, 80);
  assert.equal((html.match(/Single 20: 4 hits/g) ?? []).length, 2);
  assert.match(html, /fill="hsl\(0 90% 50%\)"><title>Single 20: 4 hits/);
  assert.match(html, /fill="hsl\(28 90% 61%\)"><title>Double 20: 2 hits/);
  assert.match(html, /fill="hsl\(41 90% 67%\)"><title>Triple 20: 1 hits/);
  assert.match(
    html,
    /r="10" fill="hsl\(41 90% 67%\)"><title>Single 25: 1 hits/
  );
  assert.match(html, /r="4" fill="hsl\(28 90% 61%\)"><title>Double 25: 2 hits/);
  assert.doesNotMatch(html, /Triple 25/);
});

test('numbers view includes all numbers and multiplier counts, without triple bull', () => {
  const html = renderToStaticMarkup(
    createElement(BoardHitNumbers, boardHitStats(fixture))
  );
  assert.equal((html.match(/<tr>/g) ?? []).length, 22);
  assert.match(
    html,
    /<th scope="row">20<\/th><td>4<\/td><td>2<\/td><td>1<\/td><td>7<\/td>/
  );
  assert.match(
    html,
    /25 \(Bull\)<\/th><td>1<\/td><td>2<\/td><td>—<\/td><td>3<\/td>/
  );
});

test('empty profiles show a neutral board and a clear empty state', () => {
  const html = renderToStaticMarkup(createElement(ProfileBoardHits));
  assert.doesNotMatch(html, /No board hits recorded yet/);
  assert.match(html, /<strong>0<\/strong>/);
  assert.doesNotMatch(html, /NaN|undefined|hsl\(/);
});
