import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getPlayerStats } from '../api';

export default function ProfilePage() {
  const { username } = useParams();
  const [stats, setStats] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setStats(null);

    getPlayerStats({ username })
      .then((data) => {
        if (!cancelled) setStats(data[0] ?? null);
      })
      .catch((err) => {
        if (!cancelled) setError(err);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [username]);

  const statItems = stats
    ? [
        ['Games played', stats.playedMatches],
        ['Games won', stats.wonMatches],
        ['Legs played', stats.playedLegs],
        ['Legs won', stats.wonLegs],
        ['Darts thrown', stats.dartsThrown ?? 'Not available'],
        ['Average points', stats.avgPoints.toFixed(2)],
        ['Highest score', stats.highestScore],
        ['Highest checkout', stats.highestCheckout],
        ['Triple 20s', stats.triple20s],
        ['Bullseyes', stats.bullseyes],
      ]
    : [];

  return (
    <div className="page profile-page">
      <div className="profile-header">
        {username && (
          <Link className="btn profile-back-link" to="/friends">
            &larr; Back to Friends
          </Link>
        )}
        <h1>{username ? `${username}’s profile` : 'Profile'}</h1>
      </div>
      {loading ? (
        <p className="state-message">Loading profile...</p>
      ) : error ? (
        <p className="error-message" role="alert">
          Failed to load profile: {error.message}
        </p>
      ) : stats === null ? (
        <>
          <p className="state-message">
            No player statistics are available yet.
          </p>
        </>
      ) : (
        <>
          {!username && <h2 className="profile-identity">{stats.username}</h2>}
          <dl className="profile-stats">
            {statItems.map(([label, value]) => (
              <div key={label} className="profile-stat card">
                <dt className="profile-stat-label">{label}</dt>
                <dd className="profile-stat-value">{value}</dd>
              </div>
            ))}
          </dl>
        </>
      )}
    </div>
  );
}
