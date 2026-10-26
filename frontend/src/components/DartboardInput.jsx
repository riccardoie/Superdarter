const SEGMENTS = [
  20, 1, 18, 4, 13, 6, 10, 15, 2, 17, 3, 19, 7, 16, 8, 11, 14, 9, 12, 5,
];

function point(radius, angle) {
  const radians = (angle * Math.PI) / 180;
  return {
    x: 110 + radius * Math.cos(radians),
    y: 110 + radius * Math.sin(radians),
  };
}

function arcPath(innerRadius, outerRadius, startAngle, endAngle) {
  const outerStart = point(outerRadius, startAngle);
  const outerEnd = point(outerRadius, endAngle);
  const innerEnd = point(innerRadius, endAngle);
  const innerStart = point(innerRadius, startAngle);
  return [
    `M ${outerStart.x} ${outerStart.y}`,
    `A ${outerRadius} ${outerRadius} 0 0 1 ${outerEnd.x} ${outerEnd.y}`,
    `L ${innerEnd.x} ${innerEnd.y}`,
    `A ${innerRadius} ${innerRadius} 0 0 0 ${innerStart.x} ${innerStart.y}`,
    'Z',
  ].join(' ');
}

function Segment({ number, index, onHit }) {
  const startAngle = -99 + index * 18;
  const endAngle = startAngle + 18;
  const label = point(90, startAngle + 9);
  const dark = index % 2 === 0;
  const hit = (multiplier) => onHit(number, multiplier);

  return (
    <g
      className="dartboard-segment"
      role="button"
      tabIndex="0"
      aria-label={`Single ${number}, double ${number}, or triple ${number}`}
      onClick={() => hit(1)}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault();
          hit(1);
        }
      }}
    >
      <path
        className={dark ? 'dartboard-single-dark' : 'dartboard-single-light'}
        d={arcPath(8, 31, startAngle, endAngle)}
      />
      <path
        className={dark ? 'dartboard-single-dark' : 'dartboard-single-light'}
        d={arcPath(39, 72, startAngle, endAngle)}
      />
      <path
        className={dark ? 'dartboard-double-dark' : 'dartboard-double-light'}
        d={arcPath(72, 80, startAngle, endAngle)}
        onClick={(event) => {
          event.stopPropagation();
          hit(2);
        }}
      />
      <path
        className={dark ? 'dartboard-triple-dark' : 'dartboard-triple-light'}
        d={arcPath(31, 39, startAngle, endAngle)}
        onClick={(event) => {
          event.stopPropagation();
          hit(3);
        }}
      />
      <text x={label.x} y={label.y} className="dartboard-number">
        {number}
      </text>
    </g>
  );
}

export default function DartboardInput({ onHit, disabled }) {
  return (
    <div
      className={`dartboard-input${disabled ? ' dartboard-input-disabled' : ''}`}
    >
      <svg viewBox="0 0 220 220" aria-label="Clickable dartboard">
        <circle cx="110" cy="110" r="81" className="dartboard-wire" />
        {SEGMENTS.map((number, index) => (
          <Segment key={number} number={number} index={index} onHit={onHit} />
        ))}
        <circle
          cx="110"
          cy="110"
          r="8"
          className="dartboard-outer-bull"
          onClick={() => onHit(25, 1)}
        />
        <circle
          cx="110"
          cy="110"
          r="4"
          className="dartboard-inner-bull"
          onClick={() => onHit(25, 2)}
        />
      </svg>
    </div>
  );
}
