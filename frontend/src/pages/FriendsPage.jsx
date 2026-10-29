import { useEffect, useRef, useState } from 'react';
import { getUsername } from '../api';
import { friendNameError } from '../friends';

export default function FriendsPage() {
  const [friends, setFriends] = useState([]);
  const [adding, setAdding] = useState(false);
  const [username, setUsername] = useState('');
  const [error, setError] = useState('');
  const dialog = useRef(null);
  const owner = getUsername(localStorage.getItem('token'));

  useEffect(() => {
    if (adding) dialog.current.showModal();
  }, [adding]);

  function closeForm() {
    setAdding(false);
    setUsername('');
    setError('');
  }

  function addFriend(event) {
    event.preventDefault();
    const message = friendNameError(username, owner, friends);
    if (message) {
      setError(message);
      return;
    }
    setFriends([...friends, username.trim()]);
    closeForm();
  }

  return (
    <div className="page friends-page">
      <div className="games-header">
        <h1>Friends</h1>
        <button
          type="button"
          className="btn btn-primary"
          onClick={() => setAdding(true)}
          aria-expanded={adding}
          aria-haspopup="dialog"
        >
          Add friend
        </button>
      </div>
      {adding && (
        <dialog
          ref={dialog}
          className="modal friends-dialog"
          aria-labelledby="add-friend-title"
          onCancel={closeForm}
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
              <button type="submit" className="btn btn-primary">
                Add
              </button>
              <button type="button" className="btn" onClick={closeForm}>
                Cancel
              </button>
            </div>
          </form>
        </dialog>
      )}
      {friends.length === 0 ? (
        <p className="state-message" role="status">
          No friends yet. Add a friend to get started.
        </p>
      ) : (
        <ul className="friends-list" aria-label="Friends">
          {friends.map((name) => (
            <li key={name} className="card">
              <span className="friend-avatar" aria-hidden="true">
                {name[0].toUpperCase()}
              </span>
              <span>{name}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
