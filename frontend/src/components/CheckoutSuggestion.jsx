import { previewCheckout } from '../checkout';

export default function CheckoutSuggestion({ points, slots }) {
  const preview = previewCheckout(points, slots);
  if (!preview?.route) return null;
  const route = preview.route.map((dart) =>
    `${dart.multiplier === 3 ? 't' : dart.multiplier === 2 ? 'd' : ''}${dart.number}`
  ).join(' → ');
  return (
    <span className="game-checkout-suggestion" role="status" aria-label={`Checkout: ${route}`}>
      {route}
    </span>
  );
}
