# Super Darter

Super Darter is a web application for organising and playing dart-game sessions. Players can register, create sessions, select game modes, invite other players, record rounds, finish sessions, and review game and player statistics.

**Live application:** https://icy-desert-0c9410d03.4.azurestaticapps.net/

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
```

## Environment Variables

| Variable | Required | Purpose |
| --- | --- | --- |
| `POSTGRES_DB` | Yes | PostgreSQL database name. |
| `POSTGRES_USER` | Yes | PostgreSQL user and backend database username. |
| `POSTGRES_PASSWORD` | Yes | Password for the PostgreSQL user. |
| `JWT_SECRET` | Yes | Secret used to sign and validate JSON Web Tokens. Use a long, random value outside local development. |
| `CORS_ALLOWED_ORIGINS` | No | Allowed frontend origin for the API. Defaults to `http://localhost:5173`. |
| `VITE_API_URL` | No | Backend URL compiled into a production frontend build. The local Docker development frontend uses its API proxy instead. |

## Architecture

- **Frontend:** React 19 and Vite provide the single-page application in `frontend/`. React Router protects session, game, and profile routes; the client stores the JWT and calls the REST API.
- **Backend:** Java 25, Spring Boot, Spring Security, and JPA implement authentication plus session, game, game-mode, and player-statistics APIs in `src/main/java/`.
- **Database:** PostgreSQL 16 stores users, sessions, games, game modes, and statistics. Flyway migrations in `src/main/resources/db/migration/` create the schema and add the 301 and 501 game types on startup without demo data.
- **Local deployment:** Docker Compose starts PostgreSQL, the Spring Boot development server, and the Vite development server. The production profile (`docker compose --profile prod up --build`) instead serves the built frontend through Nginx.
- **Cloud deployment:** GitHub Actions deploys the frontend to Azure Static Web Apps and the backend JAR to Azure App Service. The production frontend is configured to call `https://backend-superdarter-d4d6d7cbefhccpd2.germanywestcentral-01.azurewebsites.net`.

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
src/main/resources/db/migration/  Flyway schema and game-type migrations
.github/workflows/                Azure deployment workflows
```
