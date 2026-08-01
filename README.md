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
