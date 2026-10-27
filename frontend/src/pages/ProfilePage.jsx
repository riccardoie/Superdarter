import { useEffect, useState } from 'react';
import { getPlayerStats } from '../api';

export default function ProfilePage() {
  const [stats, setStats] = useState(null);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;

    getPlayerStats()
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
  }, []);

  if (loading) {
    return (
      <div className="page profile-page">
        <p className="state-message">Loading profile...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="page profile-page">
        <p className="error-message">Failed to load profile: {error.message}</p>
      </div>
    );
  }

  if (stats === null) {
    return (
      <div className="page profile-page">
        <h1>Profile</h1>
        <p className="state-message">No player statistics are available yet.</p>
      </div>
    );
  }

  const statItems = [
    ['Games played', stats.playedMatches],
    ['Games won', stats.wonMatches],
    ['Legs played', stats.playedLegs],
    ['Legs won', stats.wonLegs],
    ['Average points', stats.avgPoints.toFixed(2)],
    ['Highest score', stats.highestScore],
    ['Highest checkout', stats.highestCheckout],
    ['Triple 20s', stats.triple20s],
    ['Bullseyes', stats.bullseyes],
  ];

  return (
    <div className="page profile-page">
      <h1>Profile</h1>
      <h2 className="profile-identity">{stats.username}</h2>
      <dl className="profile-stats">
        {statItems.map(([label, value]) => (
          <div key={label} className="profile-stat card">
            <dt className="profile-stat-label">{label}</dt>
            <dd className="profile-stat-value">{value}</dd>
          </div>
        ))}
      </dl>
    </div>
  );
}
