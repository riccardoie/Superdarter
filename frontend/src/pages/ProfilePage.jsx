import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { getFriends, getPlayerStats, getUsername } from '../api';
import ProfileComparison from '../components/ProfileComparison';
import ProfileComparisonPicker from '../components/ProfileComparisonPicker';
import ProfileBoardHits from '../components/ProfileBoardHits';
import {
  comparisonCandidates,
  PROFILE_STAT_FIELDS,
  profileStatValue,
} from '../profileStats';

export default function ProfilePage() {
  const { username } = useParams();
  const owner = getUsername(localStorage.getItem('token'));
  const profile = username ?? owner;
  const [stats, setStats] = useState(null);
  const [players, setPlayers] = useState([]);
  const [friends, setFriends] = useState([]);
  const [friendsLoading, setFriendsLoading] = useState(true);
  const [friendsError, setFriendsError] = useState('');
  const [friendsRefresh, setFriendsRefresh] = useState(0);
  const [comparison, setComparison] = useState({ profile: null, username: '' });
  const compareWith = comparison.profile === profile ? comparison.username : '';
  const candidates = comparisonCandidates(owner, profile, friends);
  const [error, setError] = useState(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    setStats(null);

    getPlayerStats({ username: profile, compareWith })
      .then((data) => {
        if (!cancelled) {
          setPlayers(data);
          setStats(data.find((player) => player.username === profile) ?? null);
        }
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
  }, [profile, compareWith]);

  useEffect(() => {
    let cancelled = false;
    setFriendsLoading(true);
    setFriendsError('');
    getFriends()
      .then((data) => {
        if (!cancelled) setFriends(data);
      })
      .catch((err) => {
        if (!cancelled) setFriendsError(err.message);
      })
      .finally(() => {
        if (!cancelled) setFriendsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [owner, friendsRefresh]);

  const statItems = PROFILE_STAT_FIELDS.map(([label, field]) => [
    label,
    profileStatValue(stats, field),
  ]);

  return (
    <div className="page profile-page">
      <div className="profile-header">
        {username && (
          <Link className="btn profile-back-link" to="/friends">
            &larr; Back to Friends
          </Link>
        )}
        <div className="profile-heading-row">
          <h1>{username ? `${username}’s profile` : 'Profile'}</h1>
          <ProfileComparisonPicker
            key={profile}
            candidates={candidates}
            owner={owner}
            selected={compareWith}
            loading={friendsLoading}
            error={friendsError}
            onChange={(name) => setComparison({ profile, username: name })}
            onRetry={() => setFriendsRefresh((value) => value + 1)}
          />
        </div>
      </div>
      {loading ? (
        <p className="state-message">Loading profile...</p>
      ) : error ? (
        <p className="error-message" role="alert">
          Failed to load profile: {error.message}
        </p>
      ) : compareWith ? (
        <>
          <ProfileComparison
            usernames={[profile, compareWith]}
            stats={players}
          />
          {[profile, compareWith].map((name) => (
            <ProfileBoardHits
              key={name}
              username={name}
              boardHits={
                players.find((player) => player.username === name)?.boardHits
              }
            />
          ))}
        </>
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
          <ProfileBoardHits key={profile} boardHits={stats.boardHits} />
        </>
      )}
    </div>
  );
}
