# Render cutover: sessions to standalone games

This is a coordinated, breaking frontend/backend release. **Do not simply enable
automatic deployment against the old database.** Downtime is expected and acceptable.
The implementation does not change, reset, or provision any deployed resource.

## Before deployment

1. Pause automatic deploys for **both** `superdarter-backend` and
   `superdarter-frontend` in Render, including automatic Blueprint sync if enabled.
2. Record the current frontend/backend commit IDs and database configuration.
   Take a database backup where your plan permits it. Keep the old database intact.
3. Confirm Backend CI and Frontend CI pass for the **final combined release**,
   not just the intermediate backend commit. Backend CI uses PostgreSQL 16 and
   tests fresh migration, scoring, permissions, concurrency, and the history guard.
4. Prepare a **new empty PostgreSQL 16 database**, in the backend's Render region.
   Free database availability/quotas vary; use a suitable plan or separate new
   database rather than destroying the old database as a shortcut.

## Wire the new database

The existing `render.yaml` references `superdarter-db` by name. Changing credentials
only in the dashboard can be overwritten by a future Blueprint sync.

- If managing this cutover with a Blueprint, add the new database under a new
  resource name and update **all five** backend `fromDatabase` references:
  `POSTGRES_HOST`, `POSTGRES_PORT`, `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD`.
  Review the sync preview; do not approve deletion of the old database.
- If managing manually, set those five variables to the new database's internal
  connection values and keep Blueprint auto-sync disabled until its references
  are reconciled. Ensure no stale `SPRING_DATASOURCE_URL` override points at the old DB.

Keep `SPRING_PROFILES_ACTIVE=render`, public service URLs, and CORS configuration
stable. **Rotate `JWT_SECRET` at cutover** to a fresh random Base64-encoded key
(at least 32 random bytes, e.g. `openssl rand -base64 32`). Tokens identify users
by username, so old tokens must not become valid for newly registered accounts
with reused names. Retain the old secret securely for an isolated rollback.
`application-render.properties` already uses the internal
host/port/database with `sslmode=require` and listens on Render's `PORT`.
Never copy test JWT secrets or smoke-test SSL overrides into Render.

## Deploy and verify

1. Keep the old frontend unavailable to users during the cutover (maintenance
   page or otherwise communicate downtime); it cannot talk to the new API.
2. Deploy the final release backend against the new empty database. Flyway should
   apply V1–V4 and Hibernate should validate successfully. USER/ADMIN roles and
   301/501 types are seeded automatically; there are no demo users.
3. Check the public `https://<backend>/actuator/health` returns `UP`.
4. Deploy the matching frontend. Keep `VITE_API_URL` set to the public backend URL;
   rebuild if it changes. Keep `CORS_ALLOWED_ORIGINS` set to the frontend origin.
   Preserve the `/* → /index.html` SPA rewrite from `render.yaml`.
5. Sign out/clear old browser tokens. Existing users must **register again**.
   Confirm that pre-cutover tokens are rejected after the secret rotation.
6. Register two test accounts and check:
   - Create a solo game and a game including the other account.
   - Both accounts see their participating games; unrelated accounts cannot read them.
   - Pending start/delete, in-progress resume/abandon, and retained completed results.
   - Score a triple, register an empty round, undo before submission, double-out.
   - Refresh `/games/<id>` directly and verify score, type, next player, and permissions.
   - Review profile leg/match counters after completion; abandonment awards no win.
   - Check mobile layout, dartboard/select input, and checkout suggestions.
7. Restore access, reconcile Blueprint configuration, then re-enable automatic deploys.
   Retain the old database and release IDs until the new release is accepted.

## Safety and rollback

V4 deliberately fails if legacy sessions/games or played-game totals are present.
That failure is protective: PostgreSQL rolls back the migration. **Do not disable
the guard, run Flyway clean/repair, or drop production tables to get around it.**
Applied V1–V3 files are unchanged. A deployed database with a different historical
Flyway baseline also requires the new empty database; do not force checksum repair.

To roll back, stop access and deploy the old backend **with its old database
connection and appropriate JWT secret**, plus the matching old frontend. The new schema is not compatible
with old binaries. Any data created after cutover stays only in the new database;
rollback does not merge it into old history.

## Local production-image smoke check

```powershell
docker build -t superdarter-match-refactor:test .
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/smoke-render-image.ps1
```

This uses disposable, uniquely named containers, not `.env` or your local/deployed
database. It exercises the production image with the `render` profile and a
nondefault `PORT`. SSL is disabled **only** for the disposable local PostgreSQL;
Render's actual TLS endpoint and public routing still require the deployed checks above.
