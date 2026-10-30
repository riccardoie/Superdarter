import { useId, useState } from 'react';
import { boardHitStats, heatColor } from '../boardHits';
import { SEGMENTS, point, arcPath } from '../dartboardGeometry';

export function BoardHitNumbers({ rows }) {
  return (
    <div className="board-hit-table-wrap">
      <table className="board-hit-table" aria-label="Board hits by number">
        <thead>
          <tr>
            {['Number', 'Single', 'Double', 'Triple', 'Total'].map((label) => (
              <th scope="col" key={label}>
                {label}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((row) => (
            <tr key={row.number}>
              <th scope="row">
                {row.number === 25 ? '25 (Bull)' : row.number}
              </th>
              <td>{row.singles}</td>
              <td>{row.doubles}</td>
              <td>{row.number === 25 ? '—' : row.triples}</td>
              <td>{row.total}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

export function BoardHitMap({ rows, max }) {
  const byNumber = new Map(rows.map((row) => [row.number, row]));
  const bull = byNumber.get(25);
  return (
    <div className="board-hit-map">
      <svg
        viewBox="10 10 200 200"
        role="img"
        aria-label="Dartboard hit heat map. Use Numbers for exact counts."
      >
        {SEGMENTS.map((number, index) => {
          const start = -99 + index * 18;
          const label = point(91, start + 9);
          const row = byNumber.get(number);
          return (
            <g key={number}>
              {[
                [10, 31, 'singles', 'Single'],
                [39, 72, 'singles', 'Single'],
                [72, 80, 'doubles', 'Double'],
                [31, 39, 'triples', 'Triple'],
              ].map(([inner, outer, field, name]) => (
                <path
                  key={inner}
                  d={arcPath(inner, outer, start, start + 18)}
                  fill={heatColor(row[field], max)}
                >
                  <title>{`${name} ${number}: ${row[field]} hits`}</title>
                </path>
              ))}
              <text x={label.x} y={label.y}>
                {number}
              </text>
            </g>
          );
        })}
        <circle cx="110" cy="110" r="10" fill={heatColor(bull.singles, max)}>
          <title>{`Single 25: ${bull.singles} hits`}</title>
        </circle>
        <circle cx="110" cy="110" r="4" fill={heatColor(bull.doubles, max)}>
          <title>{`Double 25: ${bull.doubles} hits`}</title>
        </circle>
      </svg>
      <div className="board-hit-legend">
        <span>No hits</span>
        <i />
        <span>Most hits{max > 0 ? ` (${max})` : ''}</span>
      </div>
    </div>
  );
}

export default function ProfileBoardHits({ boardHits, username }) {
  const [view, setView] = useState('dartboard');
  const id = useId();
  const { rows, total, max } = boardHitStats(boardHits);
  return (
    <section className="profile-board-hits card" aria-labelledby={id}>
      <div className="board-hit-heading">
        <h2 id={id}>{username ? `${username}’s board hits` : 'Board hits'}</h2>
        <div
          className="board-hit-switch"
          role="group"
          aria-label="Board hits view"
        >
          {['dartboard', 'numbers'].map((option) => (
            <button
              type="button"
              key={option}
              className="btn"
              aria-pressed={view === option}
              onClick={() => setView(option)}
            >
              {option === 'dartboard' ? 'Dartboard' : 'Numbers'}
            </button>
          ))}
        </div>
      </div>
      <p className="board-hit-total">
        <strong>{total}</strong> darts hit the board
      </p>
      {view === 'dartboard' ? (
        <BoardHitMap rows={rows} max={max} />
      ) : (
        <BoardHitNumbers rows={rows} />
      )}
    </section>
  );
}
