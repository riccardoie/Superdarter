import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getGames, getPlayerStats, getUsername } from '../api';
import { gamesForView } from '../gameOrder';
import { profileStatValue } from '../profileStats';
import CreateGame from './CreateGame';
import GameCard from './GameCard';
import dashboardPhoto from '../assets/stephen-bunting-2-68db7e7d2794d.webp';

export function DashboardContent({
  username,
  games,
  stats,
  gamesError,
  statsError,
  onCreate,
  onRetry,
}) {
  const active = games === null ? [] : gamesForView(games, false);
  const recent =
    games === null
      ? []
      : gamesForView(games, true)
          .toSorted((a, b) => new Date(b.createdAt) - new Date(a.createdAt))
          .slice(0, 2);
  return (
    <div className="page dashboard-page">
      <section className="dashboard-welcome">
        <div>
          <p className="dashboard-eyebrow">Your darts club</p>
          <h1>
            Welcome back, <span>{username}.</span>
          </h1>
          <p>New game. Fresh start. Make your next throw count.</p>
          <div className="dashboard-actions">
            <button
              className="btn btn-primary"
              type="button"
              onClick={onCreate}
            >
              + New game
            </button>
            <Link className="dashboard-text-link" to="/friends">
              Find your friends
            </Link>
          </div>
        </div>
        <div className="dashboard-photo">
          <img src={dashboardPhoto} alt="Darts player celebrating" />
        </div>
      </section>

      <section
        className="dashboard-summary"
        aria-labelledby="dashboard-stats-title"
      >
        <div className="dashboard-section-heading">
          <h2 id="dashboard-stats-title">Your game, in numbers</h2>
          <Link to="/profile">View profile</Link>
        </div>
        {statsError ? (
          <div className="error-message" role="alert">
            Couldn’t load your statistics.{' '}
            <button type="button" className="btn" onClick={onRetry}>
              Retry
            </button>
          </div>
        ) : stats === undefined ? (
          <p className="state-message">Loading statistics...</p>
        ) : (
          <dl className="dashboard-stat-grid">
            {[
              ['Games played', 'playedMatches'],
              ['Games won', 'wonMatches'],
              ['Average points', 'avgPoints'],
              ['Best checkout', 'highestCheckout'],
            ].map(([label, field]) => (
              <div className="card dashboard-stat" key={field}>
                <dt>{label}</dt>
                <dd>{profileStatValue(stats, field)}</dd>
              </div>
            ))}
          </dl>
        )}
      </section>

      <div className="dashboard-columns">
        <section
          className="dashboard-active"
          aria-labelledby="dashboard-active-title"
        >
          <div className="dashboard-section-heading">
            <h2 id="dashboard-active-title">Back to the board</h2>
            <Link to="/games">All games</Link>
          </div>
          {gamesError ? (
            <div className="error-message" role="alert">
              Couldn’t load your games.{' '}
              <button type="button" className="btn" onClick={onRetry}>
                Retry
              </button>
            </div>
          ) : games === null ? (
            <p className="state-message">Loading games...</p>
          ) : active.length ? (
            <div className="dashboard-game-list">
              {active.slice(0, 3).map((game) => (
                <GameCard key={game.gameId} game={game} />
              ))}
            </div>
          ) : (
            <div className="card dashboard-empty">
              <span className="dashboard-eyebrow">The board is yours</span>
              <h3>Ready for your next game?</h3>
              <p>No active games right now. Go solo or bring your friends.</p>
              <button type="button" className="btn" onClick={onCreate}>
                Set up a game
              </button>
            </div>
          )}
        </section>
        <aside className="dashboard-shortcuts" aria-label="Explore your club">
          <Link className="card dashboard-shortcut" to="/profile">
            <span className="dashboard-eyebrow">Know your game</span>
            <h2>Find your hot spots</h2>
            <p>Explore your dartboard heat map and personal stats.</p>
          </Link>
          <Link className="card dashboard-shortcut" to="/friends">
            <span className="dashboard-eyebrow">Better together</span>
            <h2>Your next rival awaits</h2>
            <p>Connect with friends and compare your numbers.</p>
          </Link>
        </aside>
      </div>

      {!gamesError && games !== null && recent.length > 0 && (
        <section aria-labelledby="dashboard-history-title">
          <div className="dashboard-section-heading">
            <h2 id="dashboard-history-title">Last time at the oche</h2>
            <Link to="/completed-games">Game history</Link>
          </div>
          <div className="dashboard-history">
            {recent.map((game) => (
              <GameCard key={game.gameId} game={game} />
            ))}
          </div>
        </section>
      )}
    </div>
  );
}

export default function HomeDashboard() {
  const username = getUsername(localStorage.getItem('token'));
  const navigate = useNavigate();
  const [games, setGames] = useState(null);
  const [stats, setStats] = useState(undefined);
  const [gamesError, setGamesError] = useState(false);
  const [statsError, setStatsError] = useState(false);
  const [refresh, setRefresh] = useState(0);
  const [showCreate, setShowCreate] = useState(false);

  useEffect(() => {
    let cancelled = false;
    setGames(null);
    setStats(undefined);
    setGamesError(false);
    setStatsError(false);
    getGames()
      .then((data) => {
        if (!cancelled) setGames(data);
      })
      .catch(() => {
        if (!cancelled) setGamesError(true);
      });
    getPlayerStats({ username })
      .then((data) => {
        if (!cancelled)
          setStats(data.find((player) => player.username === username) ?? null);
      })
      .catch(() => {
        if (!cancelled) setStatsError(true);
      });
    return () => {
      cancelled = true;
    };
  }, [username, refresh]);

  return (
    <>
      <DashboardContent
        username={username}
        games={games}
        stats={stats}
        gamesError={gamesError}
        statsError={statsError}
        onCreate={() => setShowCreate(true)}
        onRetry={() => setRefresh((value) => value + 1)}
      />
      {showCreate && (
        <CreateGame
          onClose={() => setShowCreate(false)}
          onContinue={(draft) => {
            setShowCreate(false);
            navigate('/games/new', { state: { draft } });
          }}
        />
      )}
    </>
  );
}
