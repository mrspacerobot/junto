# Onda-to-Junto database and deployment runbook

This rename is a release operation, not a Docker-volume rename. Junto uses the new
`junto_pgdata` volume so an existing Onda volume is never initialized, rewritten,
or deleted by `docker compose`.

## What the release changes

- Released Flyway migrations `V001`, `V002`, `V003`, `V004`, `V005`, `V014`, and
  `V016` remain byte-identical.
- Existing databases advance only through `V017`–`V019`.
- `V017` intentionally deletes all Spring Session rows. Serialized Onda Java class
  names are not compatible with the minimal Junto session principal, so every user
  must sign in again after deployment.
- Domain rows are retained. `V018` adds missing columns, tables, indexes, trigger,
  constraints, and the activity version column without dropping domain data.
- The application requires PostgreSQL with PostGIS and runs Hibernate with
  `ddl-auto=validate`; Hibernate must not repair a migration failure.

## Before deployment

1. Stop application writers, but do **not** delete or rename the Onda volume.
2. Record the deployed commit, database host/name/owner, and the output of:
   `SELECT installed_rank, version, description, checksum, success FROM flyway_schema_history ORDER BY installed_rank;`
3. Take both an infrastructure snapshot and a logical backup. For example:

   ```bash
   pg_dump --format=custom --no-owner --no-acl \
     --dbname="$ONDA_DATABASE_URL" --file="onda-before-junto.dump"
   sha256sum onda-before-junto.dump > onda-before-junto.dump.sha256
   pg_restore --list onda-before-junto.dump >/dev/null
   ```

4. Test that backup by restoring it into a disposable PostgreSQL/PostGIS instance.
5. Confirm the restored Flyway checksums validate with the old Onda release before
   starting Junto.

## Local Docker transition

`POSTGRES_DB`, `POSTGRES_USER`, and `POSTGRES_PASSWORD` are only honored when a
PostgreSQL data directory is first initialized. Changing those values does not add
a `junto` database or role to an existing Onda volume.

The safest local transition is logical copy, keeping the old volume untouched:

1. Start the old Onda database against its existing volume and produce a
   `--no-owner --no-acl` dump as above.
2. Stop that container without `-v`.
3. Start Junto's distinct volume: `docker compose up -d postgres`.
4. Restore into Junto:

   ```bash
   pg_restore --clean --if-exists --no-owner --no-acl \
     --dbname="postgresql://junto:junto@localhost:5432/junto" onda-before-junto.dump
   ```

   Replace the example credentials for real environments. If copying the command,
   type the password from `docker-compose.yml`; do not copy secrets into shell
   history.

5. Start the backend. Flyway must validate the released history and apply only
   `V017`–`V019`.
6. Never run `docker compose down -v` as a migration step.

## Verification

Before enabling traffic, verify:

```sql
SELECT version, description, success
FROM flyway_schema_history ORDER BY installed_rank;

SELECT count(*) FROM users;
SELECT count(*) FROM activities;
SELECT count(*) FROM activity_participants;
SELECT count(*) FROM chat_messages;
SELECT count(*) FROM spring_session; -- expected: 0 immediately after V017
```

Then prove login/session reload, activity listing, activity detail, join/leave,
message history, and realtime chat with a disposable account. Monitor Flyway,
authentication failures, WebSocket disconnects, and database constraint errors.

## Rollback

Do not point old Onda code at a database after Junto migrations without a rehearsed
compatibility check. The safe rollback is to stop Junto, retain the failed database
for investigation, restore the pre-release dump/snapshot into a separate database,
and point the old deployment back to that restored database. The original Onda
Docker volume remains an additional recovery source. Sessions are intentionally not
preserved; users must authenticate again after either direction of the transition.
