import { useEffect, useState } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { getFinishedGames, getSessionById, getSessions, startGame } from '../api';
import GameTypeCard from '../components/GameTypeCard';
import FinishedGamesList from '../components/FinishedGamesList';
import AddGamesModal from '../components/AddGamesModal';
import ConfirmFinishModal from '../components/ConfirmFinishModal';
import ConfirmDeleteSession from '../components/ConfirmDeleteSession';
import ConfirmDeleteGame from '../components/ConfirmDeleteGame';
import { useAuth } from '../useAuth';

// &larr is an HTML entity for a left-pointing arrow (←)
const BackToSessions = (
  <Link className="btn session-detail-back" to="/sessions">
    &larr; Back to Sessions
  </Link>
);

export default function SessionDetailPage() {
  const { id } = useParams();
  const navigate = useNavigate();
  const { isAdmin } = useAuth();
  const [session, setSession] = useState(null);
  const [error, setError] = useState('');
  const [actionError, setActionError] = useState('');
  const [finished, setFinished] = useState({});
  const [finishedGames, setFinishedGames] = useState([]);
  const [addingGames, setAddingGames] = useState(false);
  const [finishingSession, setFinishingSession] = useState(false);
  const [deletingSession, setDeletingSession] = useState(false);
  // Holds the gamemode whose Remove was clicked, or null when no modal is open.
  const [deletingGame, setDeletingGame] = useState(null);
  // null until the ownership lookup resolves.
  const [isOwner, setIsOwner] = useState(null);

  useEffect(() => {
    let cancelled = false;
    getSessionById(id)
      .then((data) => {
        if (!cancelled) setSession(data);
      })
      .catch((err) => {
        if (!cancelled) setError(err);
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  useEffect(() => {
    let cancelled = false;
    getFinishedGames({ sessionId: id })
      .then((data) => {
        if (!cancelled) setFinishedGames(data);
      })
      .catch(() => {
        // Finished-games list is must not block the page.
      });
    return () => {
      cancelled = true;
    };
  }, [id]);

  // Adding games is owner-only server-side and SessionResponse carries no owner
  // field. getSessions() is owner-scoped, so checking that list
  useEffect(() => {
    let cancelled = false;
    getSessions()
      .then((data) => {
        if (!cancelled) {
          setIsOwner(data.some((s) => String(s.id) === String(id)));
        }
      })
      .catch((err) =>
        // A 404 means the user owns no sessions at all
        setIsOwner(err.status !== 404)
      );
    return () => {
      cancelled = true;
    };
  }, [id]);

  if (error) {
    if (error.status === 404) {
      return (
        <div className="page session-detail-page">
          <p className="error-message">Session not found</p>
          {BackToSessions}
        </div>
      );
    }
    return (
      <div className="page session-detail-page">
        <p className="error-message">
          Failed to load session: {error.message}
        </p>
        {BackToSessions}
      </div>
    );
  }

  if (session === null) {
    return (
      <div className="page session-detail-page">
        <p className="state-message">Loading session...</p>
      </div>
    );
  }

  const gamemodeCounts = session.gametypes.reduce((counts, gametype) => {
    counts[gametype.gamemode] = (counts[gametype.gamemode] || 0) + 1;
    return counts;
  }, {});

  const handleGamesAdded = (updated) => {
    setSession(updated);
    // Adding games can un-exhaust a gametype, so the stale "all finished"
    // is removed
    setFinished({});
  };

  // The PUT returns no body, so the new isActive value has to be refetched.
  const handleFinished = async () => {
    const fresh = await getSessionById(id);
    setSession(fresh);
  };

  // The session is gone, so there is nothing left to render: leave the page
  // rather than refetch, which would only 404.
  const handleDeleted = () => {
    navigate('/sessions');
  };

  // The DELETE removes one game without saying which, so the local gametype
  // counts are now wrong and have to be refetched.
  const handleGameDeleted = async () => {
    const fresh = await getSessionById(id);
    setSession(fresh);
    setDeletingGame(null);
  };

  const handleStart = async (gamemode) => {
    try {
      const res = await startGame({ sessionId: id, gameType: gamemode });
      navigate(`/session/${id}/game/${res.gameId}`);
    } catch (err) {
      if (err.status === 404) {
        setFinished((f) => ({
          ...f,
          [gamemode]: 'All games of this type are finished',
        }));
      } else {
        setActionError(`Failed to start game: ${err.message}`);
      }
    }
  };

  return (
    <div className="page session-detail-page">
      <header className="session-detail-hero">
        {BackToSessions}
        <div className="session-detail-heading">
          <div>
            <p className="session-detail-eyebrow">Session</p>
            <h1>
              <time dateTime={session.date}>
                {new Date(session.date).toLocaleDateString('en-GB', {
                  dateStyle: 'medium',
                })}
              </time>
            </h1>
          </div>
          <div className="session-detail-meta">
            {session.isActive ? (
              <span className="session-card-badge">Active</span>
            ) : (
              <span className="session-finished-badge">Finished</span>
            )}
          </div>
        </div>
        {(isAdmin || (isOwner && session.isActive)) && (
          <details className="session-manage">
            <summary>Manage session</summary>
            <div className="session-manage-options">
              {isOwner && session.isActive && (
                <button
                  type="button"
                  onClick={(event) => {
                    event.currentTarget.closest('details').removeAttribute('open');
                    setFinishingSession(true);
                  }}
                >
                  Finish session
                </button>
              )}
              <button
                type="button"
                className="session-manage-delete"
                onClick={(event) => {
                  event.currentTarget.closest('details').removeAttribute('open');
                  setDeletingSession(true);
                }}
              >
                Delete session
              </button>
            </div>
          </details>
        )}
      </header>
      {actionError && <p className="error-message">{actionError}</p>}
      <section className="session-detail-section">
        <div className="session-detail-games-header">
          <div>
            <p className="session-detail-eyebrow">Ready to play</p>
            <h2>Games in this session</h2>
          </div>
          {isOwner && session.isActive && (
            <button
              className="btn session-add-game"
              type="button"
              onClick={() => setAddingGames(true)}
            >
              + Add game
            </button>
          )}
        </div>
        {Object.keys(gamemodeCounts).length === 0 ? (
          <p className="state-message session-detail-empty">
            No games in this session
          </p>
        ) : (
          <div className="session-detail-games">
            {Object.entries(gamemodeCounts).map(([gamemode, count]) => (
              <GameTypeCard
                key={gamemode}
                gamemode={gamemode}
                count={count}
                onStart={() => handleStart(gamemode)}
                onDelete={() => setDeletingGame(gamemode)}
                deletable={Boolean(isOwner && session.isActive)}
                finishedMessage={finished[gamemode]}
                playable={session.isActive}
              />
            ))}
          </div>
        )}
      </section>
      <section className="session-detail-section session-detail-finished-section">
        <div className="session-detail-section-heading">
          <p className="session-detail-eyebrow">Results</p>
          <h2>Finished games</h2>
        </div>
        <FinishedGamesList games={finishedGames} sessionId={id} />
      </section>
      {addingGames && (
        <AddGamesModal
          sessionId={id}
          onClose={() => setAddingGames(false)}
          onAdded={handleGamesAdded}
        />
      )}
      {finishingSession && (
        <ConfirmFinishModal
          sessionId={id}
          onClose={() => setFinishingSession(false)}
          onFinished={handleFinished}
        />
      )}
      {deletingSession && (
        <ConfirmDeleteSession
          sessionId={id}
          onClose={() => setDeletingSession(false)}
          onDeleted={handleDeleted}
        />
      )}
      {deletingGame && (
        <ConfirmDeleteGame
          sessionId={id}
          gamemode={deletingGame}
          count={gamemodeCounts[deletingGame]}
          onClose={() => setDeletingGame(null)}
          onDeleted={handleGameDeleted}
        />
      )}
    </div>
  );
}
