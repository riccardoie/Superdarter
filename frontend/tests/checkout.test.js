import assert from 'node:assert/strict';
import { test } from 'node:test';
import { findCheckout, previewCheckout } from '../src/checkout.js';

const labels = (points, darts) =>
  findCheckout(points, darts)?.map((dart) => dart.label);
const empty = () => ({ value: '', multiplier: 1 });
const dart = (value, multiplier = 1) => ({ value, multiplier });

test('finds shortest conventional double-out routes', () => {
  assert.deepEqual(labels(40), ['D20']);
  assert.deepEqual(labels(32), ['D16']);
  assert.deepEqual(labels(50), ['Bull']);
  assert.deepEqual(labels(60), ['S20', 'D20']);
  assert.deepEqual(labels(100), ['T20', 'D20']);
  assert.deepEqual(labels(170), ['T20', 'T20', 'Bull']);
});

test('respects darts available and impossible checkout scores', () => {
  assert.equal(findCheckout(60, 1), null);
  assert.equal(findCheckout(170, 2), null);
  for (const points of [169, 168, 166, 165, 163, 162, 159, 171, 501]) {
    assert.equal(findCheckout(points), null);
  }
  for (const points of [0, 1, -1, 2.5, undefined, NaN]) {
    assert.equal(findCheckout(points), null);
  }
  for (const count of [0, -1, 4, 1.5])
    assert.equal(findCheckout(40, count), null);
});

test('all suggestions are legal and minimal against independently enumerated reachable totals', () => {
  const hits = [];
  for (let number = 1; number <= 20; number++) {
    for (let multiplier = 1; multiplier <= 3; multiplier++)
      hits.push(number * multiplier);
  }
  hits.push(25, 50);
  const reachable = [new Set()];
  reachable.push(
    new Set([...Array.from({ length: 20 }, (_, i) => (i + 1) * 2), 50])
  );
  for (let count = 2; count <= 3; count++) {
    reachable.push(
      new Set(
        hits.flatMap((hit) =>
          [...reachable[count - 1]].map((tail) => hit + tail)
        )
      )
    );
  }
  for (let points = 2; points <= 170; points++) {
    for (let count = 1; count <= 3; count++) {
      const shortest = [1, 2, 3].find(
        (n) => n <= count && reachable[n].has(points)
      );
      const route = findCheckout(points, count);
      assert.equal(route?.length, shortest, `${points} with ${count} darts`);
      if (!route) continue;
      let remaining = points;
      route.forEach((hit, index) => {
        remaining -= hit.points;
        if (index < route.length - 1) assert.ok(remaining > 1);
        else {
          assert.equal(remaining, 0);
          assert.equal(hit.multiplier, 2);
        }
      });
    }
  }
});

test('preview updates score and darts left, including actual misses', () => {
  const start = previewCheckout(100, [empty(), empty(), empty()]);
  assert.deepEqual(
    start.route.map((hit) => hit.label),
    ['T20', 'D20']
  );
  const after = previewCheckout(100, [dart('20', '3'), empty(), empty()]);
  assert.equal(after.remaining, 40);
  assert.equal(after.dartsLeft, 2);
  assert.deepEqual(
    after.route.map((hit) => hit.label),
    ['D20']
  );
  const misses = previewCheckout(60, [dart(0), dart('0'), empty()]);
  assert.equal(misses.remaining, 60);
  assert.equal(misses.dartsLeft, 1);
  assert.equal(misses.route, null);
});

test('preview stops at checkout or bust and restores the whole turn on bust', () => {
  assert.equal(
    previewCheckout(50, [dart(25, 2), dart(20, 3), empty()]).status,
    'checkout'
  );
  for (const slots of [
    [dart(20), dart(20), empty()],
    [dart(20), dart(19), empty()],
    [dart(20, 3), dart(20, 2), empty()],
  ]) {
    const preview = previewCheckout(40, slots);
    assert.equal(preview.status, 'bust');
    assert.equal(preview.remaining, 40);
    assert.equal(preview.route, null);
  }
});

test('preview handles a completed turn and counts blanks before later entries as misses', () => {
  const complete = previewCheckout(301, [
    dart(20, 3),
    dart(20, 3),
    dart(20, 3),
  ]);
  assert.equal(complete.status, 'complete');
  assert.equal(complete.remaining, 121);
  assert.equal(complete.route, null);
  const gap = previewCheckout(40, [empty(), dart(20, 2), empty()]);
  assert.equal(gap.remaining, 0);
  assert.equal(gap.status, 'checkout');
  assert.equal(gap.dartsLeft, 1);
  assert.equal(previewCheckout(undefined, []), null);
});

test('single-out suggestions allow singles, triples, one remaining and 180', () => {
  assert.deepEqual(
    findCheckout(1, 3, false).map((hit) => hit.label),
    ['S1']
  );
  assert.deepEqual(
    findCheckout(20, 3, false).map((hit) => hit.label),
    ['S20']
  );
  assert.deepEqual(
    findCheckout(60, 1, false).map((hit) => hit.label),
    ['T20']
  );
  assert.deepEqual(
    findCheckout(25, 1, false).map((hit) => hit.label),
    ['25']
  );
  assert.deepEqual(
    findCheckout(180, 3, false).map((hit) => hit.label),
    ['T20', 'T20', 'T20']
  );
  assert.equal(findCheckout(181, 3, false), null);
});

test('single-out preview leaves one, finishes on any dart and still busts on overshoot', () => {
  const oneLeft = previewCheckout(21, [dart(20), empty(), empty()], false);
  assert.equal(oneLeft.status, 'playing');
  assert.equal(oneLeft.remaining, 1);
  assert.deepEqual(
    oneLeft.route.map((hit) => hit.label),
    ['S1']
  );
  for (const [points, slots] of [
    [21, [dart(20), dart(1)]],
    [60, [dart(20, 3), dart(20)]],
    [25, [dart(25)]],
    [1, [dart(1)]],
  ]) {
    assert.equal(previewCheckout(points, slots, false).status, 'checkout');
  }
  const bust = previewCheckout(21, [dart(20), dart(2)], false);
  assert.equal(bust.status, 'bust');
  assert.equal(bust.remaining, 21);
});
