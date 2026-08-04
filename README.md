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

## Production container

Build the same pinned, multi-stage artifact used by CI from the repository root:

```bash
source_date_epoch="$(git show -s --format=%ct HEAD)"
docker build --pull \
  --build-arg SOURCE_DATE_EPOCH="$source_date_epoch" \
  --tag junto:local .
./scripts/verify-image.sh junto:local
./scripts/scan-image-secrets.sh junto:local
```

CI additionally builds twice without cache for one fixed platform and compares
the runtime platform manifests. Provenance is disabled only for that equality
check because per-build attestations can legitimately change the outer image
index.

The image serves the compiled Vue application and Spring Boot API together on
port 8080 and activates the `prod` profile by default. It needs an external
PostgreSQL/PostGIS database, the public application URL, and a Resend API key.
For a local production-profile run using the Compose database:

```bash
docker compose up -d --wait postgres
: "${RESEND_API_KEY:?set RESEND_API_KEY first}"
docker run --detach --name junto --publish 8080:8080 \
  --add-host host.docker.internal:host-gateway \
  --env DATABASE_HOST=host.docker.internal \
  --env DATABASE_PORT=5432 \
  --env DATABASE_NAME=junto \
  --env DATABASE_USERNAME=junto \
  --env DATABASE_PASSWORD=junto \
  --env APP_BASE_URL='http://localhost:8080' \
  --env RESEND_API_KEY \
  junto:local
```

Open <http://localhost:8080>. Stop with `docker stop junto`; Docker sends
`SIGTERM` directly to Java so Spring can finish its graceful shutdown, then
remove the stopped container with `docker rm junto`.

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

## Production SPA packaging and routing

`./gradlew bootJar` installs and builds the Vue frontend, then packages its `dist`
contents under Spring Boot's `static/` classpath. A build stage that already produced
the frontend can avoid requiring Node in the Java stage by supplying its location:

```bash
cd backend
./gradlew bootJar -PfrontendDistDir=/absolute/path/to/frontend/dist
```

Production request behavior changes from a backend-only authenticated catch-all to
same-origin routing with explicit boundaries:

- Before, anonymous requests for `/`, `/auth/login`, frontend assets, and direct
  deep links received `401`; after packaging they receive the public SPA shell or
  asset. Vue still performs its client-side authentication redirects.
- Before, Spring had no history fallback; after packaging, only routes declared in
  `SpaController` serve uncached `index.html`.
- `/api/**` remains behind Spring Security except for the explicit login endpoints.
  Unknown APIs return `401` anonymously or a JSON `404` when authenticated; they
  never return SPA HTML.
- `/ws` remains the STOMP WebSocket handshake endpoint and is excluded from SPA
  fallback.
- Vite's hashed `/assets/**` responses are public and immutable for one year.
  `index.html`, the favicon, and authentication responses are not stored.

## Onda migration

Do not reuse, delete, or reinitialize an existing Onda Docker volume. Follow the
backup, logical-copy, session-expiration, verification, and rollback procedure in
[`docs/operations/onda-to-junto.md`](docs/operations/onda-to-junto.md).
