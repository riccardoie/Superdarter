import assert from 'node:assert/strict';
import { test } from 'node:test';
import {
  comparisonCandidates,
  PROFILE_STAT_FIELDS,
  profileStatValue,
} from '../src/profileStats.js';

test('comparison choices include yourself and other friends, but not the current profile', () => {
  const friends = [
    { username: 'carol' },
    { username: 'bob' },
    { username: 'dave' },
  ];
  assert.deepEqual(comparisonCandidates('alice', 'bob', friends), [
    'alice',
    'carol',
    'dave',
  ]);
  assert.deepEqual(comparisonCandidates('alice', 'alice', friends), [
    'bob',
    'carol',
    'dave',
  ]);
});

test('comparison choices handle no friends and remove duplicate usernames', () => {
  assert.deepEqual(comparisonCandidates('alice', 'alice', []), []);
  assert.deepEqual(comparisonCandidates('alice', 'bob', []), ['alice']);
  assert.deepEqual(
    comparisonCandidates('alice', 'bob', [
      { username: 'alice' },
      { username: 'carol' },
      { username: 'carol' },
    ]),
    ['alice', 'carol']
  );
});

test('comparison values retain zeroes, format averages and distinguish unavailable data', () => {
  assert.equal(PROFILE_STAT_FIELDS.length, 11);
  assert.ok(
    !PROFILE_STAT_FIELDS.some(([, field]) =>
      ['triple20s', 'bullseyes'].includes(field)
    )
  );
  assert.equal(profileStatValue({ avgPoints: 45.567 }, 'avgPoints'), '45.57');
  assert.equal(profileStatValue({ avgPoints: 0 }, 'avgPoints'), '0.00');
  assert.equal(profileStatValue({ playedMatches: 0 }, 'playedMatches'), 0);
  assert.equal(
    profileStatValue({ dartsThrown: null }, 'dartsThrown'),
    'Not available'
  );
  assert.equal(profileStatValue(null, 'highestScore'), 'Not available');
  for (const field of [
    'highestLegAverage',
    'highestGameAverage',
    'highestFirstNineAverage',
  ]) {
    assert.equal(profileStatValue({ [field]: 90.125 }, field), '90.13');
    assert.equal(profileStatValue({ [field]: 0 }, field), '0.00');
    assert.equal(profileStatValue({ [field]: null }, field), 'Not available');
  }
});
