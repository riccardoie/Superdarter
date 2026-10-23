# Super Darter

Super Darter is a web application for organising and playing dart-game sessions. Players can register, create sessions, select game modes, invite other players, record rounds, finish sessions, and review game and player statistics.

Deployment is configured for Render via `render.yaml`.

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

This also removes the Maven cache volume. Flyway creates the schema from
`V1__init_schema.sql`, then adds the 301 and 501 game types via
`V2__add_301_and_501_gametypes.sql`. No demo data is inserted. The `USER` role must
be added before registration works.

This consolidated baseline replaces the previous V1–V11 migrations. Existing
databases must be reset or migrated using a separately planned baseline strategy;
do not deploy it unchanged against an existing database's Flyway history.

## Tests

Run the backend unit tests from the repository root:

```bash
mvn test
```

The frontend has no automated test script. Run its static checks from `frontend/`:

```bash
npm ci
npm run lint
npm run build
```

Run the full backend checks (requires Docker for Testcontainers):

```bash
mvn -B clean verify -Pintegration
```

## CI and Deployment

GitHub Actions runs **Backend CI** and **Frontend CI** on pull requests targeting
`main` or `dev`, pushes to either branch, and manual dispatches. Backend CI builds
the JAR, runs unit and integration tests, and checks Checkstyle. Frontend CI runs
ESLint and builds the production assets. Require both job checks in the `main`
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
   This checkout includes `V2__seed_data.sql` with demo users and games: review
   those migrations before the first production deployment. This CI/hosting
   configuration does not remove or rewrite seed data.
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

- **Frontend:** React 19 and Vite provide the single-page application in `frontend/`. React Router protects session, game, and profile routes; the client stores the JWT and calls the REST API.
- **Backend:** Java 25, Spring Boot, Spring Security, and JPA implement authentication plus session, game, game-mode, and player-statistics APIs in `src/main/java/`.
- **Database:** PostgreSQL 16 stores users, sessions, games, game modes, and statistics. Flyway migrations in `src/main/resources/db/migration/` create the schema and add the 301 and 501 game types on startup without demo data.
- **Local deployment:** Docker Compose starts PostgreSQL, the Spring Boot development server, and the Vite development server. The production profile (`docker compose --profile prod up --build`) instead serves the built frontend through Nginx.
- **Cloud deployment:** Render builds and deploys the Docker backend and frontend static site after GitHub CI checks pass on `main`. Render PostgreSQL stores application data. `render.yaml` defines the services and database.

## Team

- Sander Sandvik Nessa
- Riccardo Ieva

### Domain Mapping

The team mapped the five required placeholders to the application domain as follows:

| Placeholder | Super Darter domain |
| --- | --- |
| Primary | Session |
| Child | Game |
| User | User |
| Tag | Game mode |
| Interaction | Playing games with other players |

## Project Layout

```text
frontend/                         React and Vite application
src/main/java/lux/dartgame/       Spring Boot API
src/main/resources/db/migration/  Flyway schema and seed migrations
.github/workflows/                Backend, frontend, and commit checks
render.yaml                       Render service and database Blueprint
```
