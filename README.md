# Super Darter

Super Darter is a web application for playing 301 and 501 darts. Players create games directly, add up to nine other registered players, score double-out legs, and review results and player statistics. Solo play is supported.

Deployment is configured for Render via `render.yaml` to https://superdarter-frontend.onrender.com/.

## Run Locally

### Prerequisites

- Docker Desktop with Docker Compose

### Configuration

Create a `.env` file in the repository root. It is deliberately ignored by Git.

```dotenv
POSTGRES_DB=
POSTGRES_USER=
POSTGRES_PASSWORD=
JWT_SECRET=
```

### Start the Stack

Run the complete development stack with hot reload:

```bash
docker compose --profile dev up --build
```

Open http://localhost:5173. The frontend proxies API calls to the backend at http://localhost:8080. Stop the stack with `Ctrl+C`; add `-v` to `docker compose down` if you also want to remove the local database volume.

### Reset the Local Database

To permanently delete local database data and recreate the schema:

```bash
docker compose --profile dev down -v
docker compose --profile dev up --build
```

This also removes the Maven cache volume. Flyway applies V1–V5: schema, 301/501
game types, roles, the new match/leg model, and first-to leg targets. Registration works immediately;
no demo users are inserted. V4 refuses to replace existing session history.
Do not rewrite already-applied migrations or use Flyway repair to bypass the guard.

## Tests

Run the backend unit tests from the repository root:

```bash
mvn test
```

Run the frontend tests and static checks from `frontend/`:

```bash
npm ci
npm run lint
npm test
npm run build
```

Run the full backend checks (requires Docker for Testcontainers):

```bash
mvn -B clean verify -Pintegration
```

## CI and Deployment

GitHub Actions runs **Backend CI** and **Frontend CI** on pull requests targeting
`main` or `dev`, pushes to either branch, and manual dispatches. Backend CI builds
the JAR, runs unit and integration tests, checks Checkstyle, and smoke-tests the
production Docker image with the Render profile. Frontend CI runs
ESLint, runs frontend tests, and builds the production assets. Require both job checks in the `main`
branch ruleset. Neither workflow needs production secrets or a shared database;
integration tests use a disposable PostgreSQL container.

### Render Setup

1. Push this configuration and connect the repository in Render using **New → Blueprint**.
2. Review `render.yaml` before applying. It defines a Docker backend, a frontend
   static site, and a private-network-only PostgreSQL 16 database in Frankfurt.
   The backend and database use free plans for evaluation: the backend can spin
   down and free databases expire. Choose paid plans for persistent production use.
3. Supply `VITE_API_URL` as the backend's public HTTPS URL, and
   `CORS_ALLOWED_ORIGINS` as the frontend's public HTTPS origin without a trailing
   slash. Use the actual URLs assigned by Render, not assumed service-name URLs.
   If the URLs are not known during setup, use temporary values and update them
   after service creation. Rebuild the frontend and redeploy the backend afterward.
4. Render wires database credentials automatically and generates a Base64 JWT
   secret. The `render` Spring profile configures the JDBC URL and Render's `PORT`.
   Flyway applies the repository's migrations on backend startup.
    V2 adds game types and V3 adds roles; no demo accounts are installed. V4
    replaces the session schema only when no legacy session history exists.
5. Both services deploy from `main` using **After CI Checks Pass**
   (`autoDeployTrigger: checksPass`). Confirm this setting and GitHub check access
   in Render. Initial Blueprint/manual deploys are not a substitute for the PR CI gate.
6. Verify the backend's `/actuator/health` endpoint and test registration, login,
   and refreshing a frontend route. The Blueprint includes the SPA rewrite.

The Azure deployment workflows have been removed. Delete unused Azure secrets in
GitHub and retire Azure resources separately when ready; changing this repository
does not delete cloud resources. Do not rewrite migrations already applied to an
existing production database.

## Environment Variables

| Variable | Required | Purpose |
| --- | --- | --- |
| `POSTGRES_DB` | Yes | PostgreSQL database name. |
| `POSTGRES_USER` | Yes | PostgreSQL user and backend database username. |
| `POSTGRES_PASSWORD` | Yes | Password for the PostgreSQL user. |
| `JWT_SECRET` | Yes | Secret used to sign and validate JSON Web Tokens. Use a long, random value outside local development. |
| `CORS_ALLOWED_ORIGINS` | No | Allowed frontend origin for the API. Defaults to `http://localhost:5173`. |
| `VITE_API_URL` | No | Backend URL compiled into a production frontend build. The local Docker development frontend uses its API proxy instead. |
| `SPRING_PROFILES_ACTIVE` | Render | Set to `render` by the Blueprint to enable Render-specific database and port settings. |
| `POSTGRES_HOST` | Render | Internal database hostname, supplied by the Blueprint. |
| `POSTGRES_PORT` | No | Database port for the Render profile; defaults to `5432`. |
| `PORT` | No | HTTP port in the Render profile; supplied by Render, defaults to `8080`. |

## Architecture

- **Frontend:** React 19 and Vite provide the single-page application in `frontend/`. Protected routes are `/games`, `/games/:gameId`, and `/profile`. Reloads retrieve complete game state from the backend, without a session or cached response.
- **Backend:** Java 25, Spring Boot, Spring Security, and JPA implement authentication plus game, game-mode, and player-statistics APIs. Player-facing games are `Match` entities containing participants and `Leg` entities. Scoring rules are independent of persistence.
- **Database:** PostgreSQL 16 stores users, matches, match participants, legs, leg-player statistics, game modes, and player totals. Applied Flyway migrations are retained unchanged; V4 introduces the new model.
- **Local deployment:** Docker Compose starts PostgreSQL, the Spring Boot development server, and the Vite development server. The production profile (`docker compose --profile prod up --build`) instead serves the built frontend through Nginx.
- **Cloud deployment:** Render builds and deploys the Docker backend and frontend static site after GitHub CI checks pass on `main`. Render PostgreSQL stores application data. `render.yaml` defines the services and database.

## Team

- Sander Sandvik Nessa
- Riccardo Ieva

### Domain Mapping

The team mapped the five required placeholders to the application domain as follows:

| Placeholder | Super Darter domain |
| --- | --- |
| Primary | Match (player-facing game) |
| Child | Leg |
| User | User |
| Tag | Game mode |
| Interaction | Playing games with other players |

## Project Layout

```text
frontend/                         React and Vite application
src/main/java/lux/dartgame/       Spring Boot API
src/main/resources/db/migration/  Flyway schema and reference-data migrations
docs/                            Game API and Render cutover checklist
.github/workflows/                Backend, frontend, and commit checks
render.yaml                       Render service and database Blueprint
```

## Game lifecycle and API

See [the game API](docs/games-api.md) for request/response examples and permissions.
Choose a single leg or first to 2–99 leg wins when creating a game. Each game uses
one game type throughout. The next leg starts automatically, with the starting
player rotating through the participant order. Sets remain follow-up work.
Completed and abandoned games are retained. Only pending games may be deleted.
Shared-device scoring remains supported. A blank round counts as all misses;
partial rounds accept one to three darts. Checkouts require a double (including bull).
Profile totals distinguish matches from legs; averages remain points per turn.
Completed legs count immediately, even if the match is later abandoned; match
participation and wins count only when somebody reaches the target. V5 upgrades
existing match databases without a reset and defaults their target to one leg.
