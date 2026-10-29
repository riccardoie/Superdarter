import { PROFILE_STAT_FIELDS, profileStatValue } from '../profileStats';

export default function ProfileComparison({ usernames, stats }) {
  const byName = new Map(stats.map((player) => [player.username, player]));
  return (
    <table
      className="profile-comparison"
      aria-label="Player statistics comparison"
    >
      <thead>
        <tr>
          <th scope="col">Statistic</th>
          {usernames.map((name) => (
            <th key={name} scope="col">
              {name}
              {!byName.has(name) && <small>No statistics yet</small>}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        {PROFILE_STAT_FIELDS.map(([label, field]) => (
          <tr key={field}>
            <th scope="row">{label}</th>
            {usernames.map((name) => (
              <td key={name}>{profileStatValue(byName.get(name), field)}</td>
            ))}
          </tr>
        ))}
      </tbody>
    </table>
  );
}
