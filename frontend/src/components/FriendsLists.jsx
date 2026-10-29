export default function FriendsLists({
  friends,
  incoming,
  outgoing,
  busy,
  onAccept,
  onDismiss,
}) {
  return (
    <div className="friends-sections">
      {incoming.length > 0 && (
        <section aria-labelledby="incoming-friends-title">
          <h2 id="incoming-friends-title">
            Friend requests ({incoming.length})
          </h2>
          <ul className="friends-list" aria-label="Incoming friend requests">
            {incoming.map((request) => (
              <li key={request.requestId} className="card friend-request-row">
                <span className="friend-name">{request.sender}</span>
                <div className="friend-request-actions">
                  <button
                    type="button"
                    className="btn btn-primary"
                    disabled={Boolean(busy)}
                    aria-label={`Accept friend request from ${request.sender}`}
                    onClick={() => onAccept(request)}
                  >
                    {busy === `accept-${request.requestId}`
                      ? 'Accepting…'
                      : 'Accept'}
                  </button>
                  <button
                    type="button"
                    className="btn"
                    disabled={Boolean(busy)}
                    aria-label={`Decline friend request from ${request.sender}`}
                    onClick={() => onDismiss(request, 'decline')}
                  >
                    {busy === `decline-${request.requestId}`
                      ? 'Declining…'
                      : 'Decline'}
                  </button>
                </div>
              </li>
            ))}
          </ul>
        </section>
      )}
      <section aria-labelledby="friends-list-title">
        <h2 id="friends-list-title">Your friends ({friends.length})</h2>
        {friends.length === 0 ? (
          <p className="state-message">
            No friends yet. Send or accept a friend request to get started.
          </p>
        ) : (
          <ul className="friends-list" aria-label="Friends">
            {friends.map((friend) => (
              <li key={friend.username} className="card friend-profile-row">
                <Link
                  className="friend-profile-link"
                  to={`/profile/${encodeURIComponent(friend.username)}`}
                  aria-label={`View ${friend.username}’s profile`}
                >
                  <span className="friend-avatar" aria-hidden="true">
                    {friend.username[0].toUpperCase()}
                  </span>
                  <span className="friend-name">{friend.username}</span>
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
      {outgoing.length > 0 && (
        <section aria-labelledby="outgoing-friends-title">
          <h2 id="outgoing-friends-title">Sent requests ({outgoing.length})</h2>
          <ul className="friends-list" aria-label="Outgoing friend requests">
            {outgoing.map((request) => (
              <li key={request.requestId} className="card friend-request-row">
                <span className="friend-name">{request.recipient}</span>
                <span className="friend-request-status">Pending</span>
                <button
                  type="button"
                  className="btn"
                  disabled={Boolean(busy)}
                  aria-label={`Withdraw friend request to ${request.recipient}`}
                  onClick={() => onDismiss(request, 'withdraw')}
                >
                  {busy === `withdraw-${request.requestId}`
                    ? 'Withdrawing…'
                    : 'Withdraw'}
                </button>
              </li>
            ))}
          </ul>
        </section>
      )}
    </div>
  );
}
import { Link } from 'react-router-dom';
