export const PROFILE_STAT_FIELDS = [
  ['Games played', 'playedMatches'],
  ['Games won', 'wonMatches'],
  ['Legs played', 'playedLegs'],
  ['Legs won', 'wonLegs'],
  ['Darts thrown', 'dartsThrown'],
  ['Average points', 'avgPoints'],
  ['Highest leg average', 'highestLegAverage'],
  ['Highest game average', 'highestGameAverage'],
  ['Highest first 9 average', 'highestFirstNineAverage'],
  ['Highest score', 'highestScore'],
  ['Highest checkout', 'highestCheckout'],
];

export function profileStatValue(stats, field) {
  const value = stats?.[field];
  if (value == null) return 'Not available';
  return [
    'avgPoints',
    'highestLegAverage',
    'highestGameAverage',
    'highestFirstNineAverage',
  ].includes(field)
    ? value.toFixed(2)
    : value;
}

export function comparisonCandidates(owner, profile, friends) {
  const names = new Set([owner, ...friends.map((friend) => friend.username)]);
  names.delete(profile);
  return [...names].sort((a, b) => {
    if (a === owner) return -1;
    if (b === owner) return 1;
    return a.localeCompare(b);
  });
}
