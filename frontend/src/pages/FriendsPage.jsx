import { useEffect, useRef, useState } from 'react';
import {
  acceptFriendRequest,
  dismissFriendRequest,
  getFriends,
  getFriendRequests,
  getUsername,
  sendFriendRequest,
} from '../api';
import { friendNameError, loadFriends } from '../friends';
import FriendsLists from '../components/FriendsLists';

export default function FriendsPage() {
  const [overview, setOverview] = useState(null);
  const [loadError, setLoadError] = useState('');
  const [actionError, setActionError] = useState('');
  const [notice, setNotice] = useState('');
  const [busy, setBusy] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [adding, setAdding] = useState(false);
  const [username, setUsername] = useState('');
  const [error, setError] = useState('');
  const dialog = useRef(null);
  const working = useRef(false);
  const version = useRef(0);
  const mounted = useRef(false);
  const owner = getUsername(localStorage.getItem('token'));

  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  useEffect(() => {
    if (working.current) return;
    let cancelled = false;
    const current = ++version.current;
    loadFriends({ getFriends, getFriendRequests })
      .then((data) => {
        if (!cancelled && current === version.current) {
          setOverview(data);
          setLoadError('');
        }
      })
      .catch((err) => {
        if (!cancelled && current === version.current)
          setLoadError(err.message);
      });
    return () => {
      cancelled = true;
    };
  }, [refresh]);

  useEffect(() => {
    const refreshLists = () => {
      if (!working.current && !document.hidden)
        setRefresh((value) => value + 1);
    };
    window.addEventListener('focus', refreshLists);
    document.addEventListener('visibilitychange', refreshLists);
    const timer = window.setInterval(refreshLists, 15000);
    return () => {
      window.removeEventListener('focus', refreshLists);
      document.removeEventListener('visibilitychange', refreshLists);
      window.clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    if (adding) dialog.current.showModal();
  }, [adding]);

  function closeForm() {
    if (working.current) return;
    setAdding(false);
    setUsername('');
    setError('');
  }

  async function performAction(key, action, onSuccess, reportError) {
    if (working.current) return;
    working.current = true;
    version.current++;
    setBusy(key);
    setNotice('');
    setActionError('');
    try {
      const result = await action();
      if (mounted.current) onSuccess(result);
    } catch (err) {
      if (mounted.current) reportError(err.message);
    } finally {
      working.current = false;
      if (mounted.current) {
        setBusy('');
        setRefresh((value) => value + 1);
      }
    }
  }

  function addFriend(event) {
    event.preventDefault();
    if (working.current || !overview) return;
    const name = username.trim();
    const message = friendNameError(
      name,
      owner,
      overview.friends.map((friend) => friend.username),
      [...overview.incoming, ...overview.outgoing]
    );
    if (message) {
      setError(message);
      return;
    }
    setError('');
    performAction(
      'send',
      () => sendFriendRequest({ username: name }),
      (request) => {
        setOverview((data) => ({
          ...data,
          outgoing: [request, ...data.outgoing],
        }));
        setAdding(false);
        setUsername('');
        setNotice(`Friend request sent to ${request.recipient}.`);
      },
      setError
    );
  }

  function accept(request) {
    performAction(
      `accept-${request.requestId}`,
      () => acceptFriendRequest({ requestId: request.requestId }),
      (accepted) => {
        setOverview((data) => ({
          ...data,
          incoming: data.incoming.filter(
            (item) => item.requestId !== request.requestId
          ),
          friends: [
            ...data.friends,
            { username: accepted.sender, friendsSince: accepted.acceptedAt },
          ].sort((a, b) => a.username.localeCompare(b.username)),
        }));
        setNotice(`You and ${accepted.sender} are now friends.`);
      },
      setActionError
    );
  }

  function dismiss(request, action) {
    performAction(
      `${action}-${request.requestId}`,
      () => dismissFriendRequest({ requestId: request.requestId }),
      () => {
        setOverview((data) => ({
          ...data,
          incoming: data.incoming.filter(
            (item) => item.requestId !== request.requestId
          ),
          outgoing: data.outgoing.filter(
            (item) => item.requestId !== request.requestId
          ),
        }));
        setNotice(
          action === 'decline'
            ? 'Friend request declined.'
            : 'Friend request withdrawn.'
        );
      },
      setActionError
    );
  }

  return (
    <div className="page friends-page">
      <div className="games-header">
        <h1>Friends</h1>
        <button
          type="button"
          className="btn btn-primary"
          disabled={Boolean(busy) || !overview}
          onClick={() => setAdding(true)}
          aria-expanded={adding}
          aria-haspopup="dialog"
        >
          Add friend
        </button>
      </div>
      {notice && (
        <p className="friends-notice" role="status">
          {notice}
        </p>
      )}
      {actionError && (
        <p className="error-message" role="alert">
          {actionError}
        </p>
      )}
      {loadError && (
        <div className="friends-load-error" role="alert">
          <p className="error-message">
            Unable to refresh friends: {loadError}
          </p>
          <button
            type="button"
            className="btn"
            disabled={Boolean(busy)}
            onClick={() => setRefresh((value) => value + 1)}
          >
            Retry
          </button>
        </div>
      )}
      {adding && (
        <dialog
          ref={dialog}
          className="modal friends-dialog"
          aria-labelledby="add-friend-title"
          onCancel={(event) => {
            if (working.current) event.preventDefault();
            else closeForm();
          }}
          onClick={(event) => {
            if (event.target !== event.currentTarget) return;
            const bounds = event.currentTarget.getBoundingClientRect();
            if (
              event.clientX < bounds.left ||
              event.clientX > bounds.right ||
              event.clientY < bounds.top ||
              event.clientY > bounds.bottom
            )
              closeForm();
          }}
        >
          <h2 id="add-friend-title">Add friend</h2>
          <form className="friends-add-form" onSubmit={addFriend}>
            <label htmlFor="friend-username">Friend’s username</label>
            <div className="friends-add-row">
              <input
                id="friend-username"
                className="player-input"
                type="text"
                value={username}
                disabled={Boolean(busy)}
                maxLength={255}
                autoComplete="off"
                autoCapitalize="none"
                spellCheck={false}
                onChange={(event) => {
                  setUsername(event.target.value);
                  setError('');
                }}
                aria-invalid={Boolean(error)}
                aria-describedby={error ? 'friend-name-error' : undefined}
              />
            </div>
            {error && (
              <p id="friend-name-error" className="error-message" role="alert">
                {error}
              </p>
            )}
            <div className="modal-actions">
              <button
                type="submit"
                className="btn btn-primary"
                disabled={Boolean(busy)}
              >
                {busy === 'send' ? 'Sending…' : 'Add'}
              </button>
              <button
                type="button"
                className="btn"
                onClick={closeForm}
                disabled={Boolean(busy)}
              >
                Cancel
              </button>
            </div>
          </form>
        </dialog>
      )}
      {!overview && !loadError && (
        <p className="state-message" role="status">
          Loading friends…
        </p>
      )}
      {overview && (
        <FriendsLists
          {...overview}
          busy={busy}
          onAccept={accept}
          onDismiss={dismiss}
        />
      )}
    </div>
  );
}
