export function friendNameError(name, owner, friends) {
  const username = name.trim();
  if (!username) return 'Enter a username.';
  if (username.length > 255) return 'Usernames must be at most 255 characters.';
  if (username === owner) return 'You cannot add yourself.';
  if (friends.includes(username)) return 'That friend is already on your list.';
  return '';
}
