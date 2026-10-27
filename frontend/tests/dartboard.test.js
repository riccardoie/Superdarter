import assert from 'node:assert/strict';
import { after, before, test } from 'node:test';
import { createServer } from 'vite';

let server;
let DartboardInput;

before(async () => {
  server = await createServer({ server: { middlewareMode: true } });
  DartboardInput = (
    await server.ssrLoadModule('/src/components/DartboardInput.jsx')
  ).default;
});

after(async () => {
  await server?.close();
});

test('miss ring records one zero-point dart by click or keyboard', () => {
  const hits = [];
  const board = DartboardInput({ onHit: (...dart) => hits.push(dart) });
  const svg = board.props.children;
  const ring = svg.props.children[0];

  ring.props.onClick();
  let prevented = false;
  ring.props.onKeyDown({
    key: ' ',
    preventDefault: () => {
      prevented = true;
    },
  });

  assert.deepEqual(hits, [
    [0, 1],
    [0, 1],
  ]);
  assert.equal(prevented, true);
  assert.equal(ring.props['aria-label'], 'Miss (0 points)');
});

test('disabled miss ring cannot record darts', () => {
  const hits = [];
  const board = DartboardInput({
    disabled: true,
    onHit: (...dart) => hits.push(dart),
  });
  const svg = board.props.children;
  const ring = svg.props.children[0];

  ring.props.onClick();

  assert.deepEqual(hits, []);
  assert.equal(ring.props.tabIndex, -1);
});
