export const PROFILE_STAT_FIELDS = [
  ['Games played', 'playedMatches'],
  ['Games won', 'wonMatches'],
  ['Legs played', 'playedLegs'],
  ['Legs won', 'wonLegs'],
  ['Darts thrown', 'dartsThrown'],
  ['Average points', 'avgPoints'],
  ['Highest score', 'highestScore'],
  ['Highest checkout', 'highestCheckout'],
  ['Triple 20s', 'triple20s'],
  ['Bullseyes', 'bullseyes'],
];

export function profileStatValue(stats, field) {
  const value = stats?.[field];
  if (value == null) return 'Not available';
  return field === 'avgPoints' ? value.toFixed(2) : value;
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
