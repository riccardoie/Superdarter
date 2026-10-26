import assert from 'node:assert/strict';
import { test } from 'node:test';
import { canSubmitRound, serializeRound, undoLastDart } from '../src/dartRound.js';
import { previewCheckout } from '../src/checkout.js';

const dart = (value = '', multiplier = 1) => ({ value, multiplier });

test('a single dart can be submitted without filling unused slots', () => {
  const slots = [dart('20', 2), dart(), dart()];
  assert.equal(canSubmitRound(slots), true);
  assert.equal(serializeRound(slots), '20 2');
});

test('two or three darts can be submitted and misses retain their position', () => {
  const slots = [dart(0), dart(20, 2), dart()];
  assert.equal(canSubmitRound(slots), true);
  assert.equal(serializeRound(slots), '0 1 20 2');
  slots[2] = dart('0');
  assert.equal(canSubmitRound(slots), true);
  assert.equal(serializeRound(slots), '0 1 20 2 0 1');
});

test('empty rounds submit as all misses, but gaps between throws cannot be submitted', () => {
  const emptyRound = [dart(), dart(), dart()];
  assert.equal(canSubmitRound(emptyRound), true);
  assert.equal(serializeRound(emptyRound), '');
  assert.equal(canSubmitRound([dart(), dart(20), dart()]), false);
  assert.equal(canSubmitRound([dart(20), dart(), dart(20)]), false);
});

test('an explicitly thrown miss can be submitted alone', () => {
  const slots = [dart('0'), dart(), dart()];
  assert.equal(canSubmitRound(slots), true);
  assert.equal(serializeRound(slots), '0 1');
});

test('undo clears the last dart and selects its slot without changing earlier throws', () => {
  const slots = [dart('20', 3), dart(20, 2), dart(0)];
  const result = undoLastDart(slots);
  assert.deepEqual(result.slots, [dart('20', 3), dart(20, 2), dart()]);
  assert.equal(result.activeDart, 2);
  assert.equal(slots[2].value, 0);
  assert.equal(serializeRound(result.slots), '20 3 20 2');
});

test('repeated undo removes all entered darts, including misses', () => {
  let result = undoLastDart([dart('20', 3), dart('0'), dart()]);
  assert.equal(result.activeDart, 1);
  result = undoLastDart(result.slots);
  assert.equal(result.activeDart, 0);
  assert.deepEqual(result.slots, [dart(), dart(), dart()]);
  assert.equal(canSubmitRound(result.slots), true);
  assert.deepEqual(undoLastDart(result.slots), result);
});

test('undo restores the checkout suggestion after an incorrect bust or finish', () => {
  for (const multiplier of [2, 3]) {
    const slots = [dart(20, multiplier), dart(), dart()];
    assert.equal(previewCheckout(40, slots).route, null);
    const result = undoLastDart(slots);
    const preview = previewCheckout(40, result.slots);
    assert.equal(preview.dartsLeft, 3);
    assert.deepEqual(preview.route.map((hit) => hit.label), ['D20']);
  }
});
