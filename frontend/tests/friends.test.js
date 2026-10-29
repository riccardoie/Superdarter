import assert from 'node:assert/strict';
import { test } from 'node:test';
import { friendNameError, loadFriends } from '../src/friends.js';

test('friend usernames reject blank names and enforce maximum length', () => {
  assert.ok(friendNameError('', 'alice', []));
  assert.ok(friendNameError('   ', 'alice', []));
  assert.ok(friendNameError('x'.repeat(256), 'alice', []));
  assert.equal(friendNameError('x'.repeat(255), 'alice', []), '');
});

test('friend usernames reject self and duplicate names after trimming', () => {
  assert.ok(friendNameError(' alice ', 'alice', []));
  assert.ok(friendNameError(' bob ', 'alice', ['bob']));
  assert.equal(friendNameError(' carol ', 'alice', ['bob']), '');
});

test('pending requests in either direction cannot be sent again', () => {
  const requests = [
    { sender: 'bob', recipient: 'alice' },
    { sender: 'alice', recipient: 'carol' },
  ];
  assert.match(friendNameError('bob', 'alice', [], requests), /pending/);
  assert.match(friendNameError(' carol ', 'alice', [], requests), /pending/);
  assert.equal(friendNameError('dave', 'alice', [], requests), '');
});

test('loading separates accepted friends from incoming and outgoing requests', async () => {
  const friends = [{ username: 'bob', friendsSince: '2026-10-29T12:00:00Z' }];
  const incoming = [{ requestId: 1, sender: 'carol', recipient: 'alice' }];
  const outgoing = [{ requestId: 2, sender: 'alice', recipient: 'dave' }];
  const data = await loadFriends({
    getFriends: async () => friends,
    getFriendRequests: async () => ({ incoming, outgoing }),
  });
  assert.deepEqual(data, { friends, incoming, outgoing });
  assert.equal(
    data.friends.some((friend) => friend.username === 'dave'),
    false
  );
});

test('a failed load is not presented as an empty friends list', async () => {
  await assert.rejects(
    loadFriends({
      getFriends: async () => [],
      getFriendRequests: async () => {
        throw new Error('Connection failed');
      },
    }),
    /Connection failed/
  );
});
