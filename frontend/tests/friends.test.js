import assert from 'node:assert/strict';
import { test } from 'node:test';
import { friendNameError } from '../src/friends.js';

test('friends preview validates blank names and maximum length', () => {
  assert.ok(friendNameError('', 'alice', []));
  assert.ok(friendNameError('   ', 'alice', []));
  assert.ok(friendNameError('x'.repeat(256), 'alice', []));
  assert.equal(friendNameError('x'.repeat(255), 'alice', []), '');
});

test('friends preview rejects self and duplicate names after trimming', () => {
  assert.ok(friendNameError(' alice ', 'alice', []));
  assert.ok(friendNameError(' bob ', 'alice', ['bob']));
  assert.equal(friendNameError(' carol ', 'alice', ['bob']), '');
});
