import { Link } from 'react-router-dom';
import { useAuth } from '../useAuth';
import heroPlayer from '../assets/Best darts players in the world 2026 _ Radio Times_ jsname=_kn3ccd.png';
import supportingPlayer from '../assets/Red Dragon Players_ jsname=_kn3ccd.png';

export default function HomePage() {
  const { isAuthenticated } = useAuth();
  const primaryDestination = isAuthenticated ? '/games' : '/register';
  const primaryLabel = isAuthenticated ? 'View games' : 'Join Superdarter';

  return (
    <div className="home-page">
      <section className="home-hero">
        <div className="home-hero-copy">
          <p className="home-kicker">Lux darts club</p>
          <h1>
            Make every
            <span> throw count.</span>
          </h1>
          <p className="home-intro">
            Set up a game, settle the score, and keep the numbers that make
            every game worth remembering.
          </p>
          <div className="home-actions">
            <Link className="btn btn-primary" to={primaryDestination}>
              {primaryLabel}
            </Link>
            {!isAuthenticated && (
              <Link className="home-text-link" to="/login">
                I already have an account
              </Link>
            )}
          </div>
        </div>

        <div className="home-hero-visual" role="img" aria-label="Dart players in action">
          <div className="home-player-photo home-player-photo-main">
            <img src={heroPlayer} alt="Professional darts player celebrating a score" />
          </div>
          <div className="home-score-chip">
            <span>Checkout</span>
            <strong>170</strong>
          </div>
          <p className="home-visual-caption">Precision under pressure</p>
        </div>
      </section>

      <section className="home-feature-grid" aria-label="Lux features">
        <article className="home-feature home-feature-stat">
          <span className="home-feature-number">01</span>
          <h2>Start a game in seconds.</h2>
          <p>Bring your players together, choose 301 or 501, and get to the oche.</p>
        </article>
        <article className="home-feature home-feature-photo">
          <div className="home-player-photo home-player-photo-secondary">
            <img src={supportingPlayer} alt="Professional darts player" />
          </div>
          <p>Built for the games you talk about afterwards.</p>
        </article>
        <article className="home-feature home-feature-stat">
          <span className="home-feature-number">02</span>
          <h2>Keep the stats that matter.</h2>
          <p>Track scores, wins, averages, bullseyes, and those big 180 moments.</p>
        </article>
      </section>
    </div>
  );
}
