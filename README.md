# Junto

Find people to do activities with.

## Requirements

- JDK 21
- Node.js 22 and npm
- Docker with Compose

## Development

```bash
docker compose up -d --wait
cd backend && ./gradlew bootRun --args='--spring.profiles.active=dev'
# in another terminal
cd frontend && npm ci && npm run dev
```

The app is at <http://localhost:5173> and Mailpit is at
<http://localhost:8025>. The `dev` profile provides a development-only login
endpoint and seed data; it is not active in production.

## Production database configuration

The `prod` profile composes its JDBC URL declaratively from five
provider-neutral environment variables:

| Variable | Value |
| --- | --- |
| `DATABASE_HOST` | PostgreSQL host |
| `DATABASE_PORT` | PostgreSQL port |
| `DATABASE_NAME` | Database name |
| `DATABASE_USERNAME` | Login username |
| `DATABASE_PASSWORD` | Login password |

Credentials are supplied through Spring's separate datasource username and
password properties; they never form part of the JDBC URL. Missing variables
stop startup through Spring's normal unresolved-placeholder failure without
echoing credential values.

Render Blueprints expose the corresponding managed Postgres properties as
`host`, `port`, `database`, `user`, and `password`. Map them explicitly to the
provider-neutral names (replace `junto-db` with the Blueprint database resource
name):

```yaml
envVars:
  - key: DATABASE_HOST
    fromDatabase: { name: junto-db, property: host }
  - key: DATABASE_PORT
    fromDatabase: { name: junto-db, property: port }
  - key: DATABASE_NAME
    fromDatabase: { name: junto-db, property: database }
  - key: DATABASE_USERNAME
    fromDatabase: { name: junto-db, property: user }
  - key: DATABASE_PASSWORD
    fromDatabase: { name: junto-db, property: password }
```

These property names come from Render's authoritative
[Blueprint reference](https://render.com/docs/blueprint-spec#referencing-service-properties)
and [`render.yaml` schema](https://render.com/schema/render.yaml.json). The
application previously accepted a credential-bearing Render `DATABASE_URL` and
parsed it in Java; that adapter is intentionally removed. `DATABASE_URL` is no
longer read by the production profile.

## Verification

```bash
cd backend && ./gradlew test
cd frontend && npm ci && npm run typecheck && npm run test:unit && npm run build
# with disposable Docker services available
cd frontend && npm run test:e2e
```

OpenAPI bindings are generated from `docs/openapi.json`. Regenerate that contract
only from a schema-valid PostgreSQL/PostGIS application:

```bash
cd backend
OPENAPI_DATABASE_URL=jdbc:postgresql://localhost:5432/junto \
OPENAPI_DATABASE_USER=junto OPENAPI_DATABASE_PASSWORD=junto \
./gradlew generateOpenApiDocs
cd ../frontend && npm run generate-api
```

## Onda migration

Do not reuse, delete, or reinitialize an existing Onda Docker volume. Follow the
backup, logical-copy, session-expiration, verification, and rollback procedure in
[`docs/operations/onda-to-junto.md`](docs/operations/onda-to-junto.md).
