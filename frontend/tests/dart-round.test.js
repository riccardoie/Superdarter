import assert from 'node:assert/strict';
import { test } from 'node:test';
import { canSubmitRound, serializeRound } from '../src/dartRound.js';

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

test('empty rounds and gaps between throws cannot be submitted', () => {
  assert.equal(canSubmitRound([dart(), dart(), dart()]), false);
  assert.equal(canSubmitRound([dart(), dart(20), dart()]), false);
  assert.equal(canSubmitRound([dart(20), dart(), dart(20)]), false);
});

test('an explicitly thrown miss can be submitted alone', () => {
  const slots = [dart('0'), dart(), dart()];
  assert.equal(canSubmitRound(slots), true);
  assert.equal(serializeRound(slots), '0 1');
});
