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

## Production runtime behind the provider proxy

Render terminates TLS and forwards plain HTTP to the container, so the runtime
contract is declared in `backend/src/main/resources/application.yml` and
`application-prod.yml` rather than in code.

| Setting | Value | Why |
| --- | --- | --- |
| `server.port` | `${PORT:8080}` | Render injects `PORT`; 8080 is the local default |
| `server.address` | `0.0.0.0` | The provider health check and router reach the container over its network interface |
| `server.shutdown` | `graceful` | Drain in-flight requests on SIGTERM |
| `spring.lifecycle.timeout-per-shutdown-phase` | `20s` (prod) | Render sends SIGKILL 30s after SIGTERM; finish first |
| `server.forward-headers-strategy` | `native` (prod only) | Trust `X-Forwarded-*` at the provider boundary and nowhere else |
| `server.servlet.session.cookie.name` | `SESSION` | Spring Session's cookie; this app never issues `JSESSIONID` |
| `server.servlet.session.cookie.http-only` / `same-site` | `true` / `lax` | Applied in every profile |
| `server.servlet.session.cookie.secure` | `true` (prod only) | Unconditional, not inferred from the request scheme |

Forwarded headers are user-supplied input everywhere except behind a proxy that
rewrites them, so only the `prod` profile trusts them. `native` installs Tomcat's
`RemoteIpValve`, which resolves the client address by walking `X-Forwarded-For`
from the right and discarding trusted proxies. A client-supplied value therefore
only ever becomes a left-hand prefix that the valve ignores; `framework`
(`ForwardedHeaderFilter`) would take the leftmost entry instead and is spoofable.
Auth source rate limits in `AuthRateLimiter` read that resolved address, so the
distinction is a security boundary, not a formatting detail.

The trusted set is Tomcat's default private-range `internal-proxies`, which covers
Render's internal network. If a provider fronts the container from a public
address, override `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` with that address
instead of widening the trust to everything.

`ProductionRuntimeConfigurationTest` asserts the declared values, and
`TrustedProxyForwardingIntegrationTest` / `UntrustedForwardedHeaderIntegrationTest`
assert the behavior on both sides of the boundary against a real servlet container.
`MockMvc` cannot cover this: forwarded-header handling is an engine valve.

## Formatting

Java uses Google Java Style through Spotless with a pinned `google-java-format`;
Vue, TypeScript, JavaScript, CSS, JSON, and Markdown in the frontend use Prettier.
CI fails on unformatted code.

```bash
# write
cd backend && ./gradlew spotlessApply
cd frontend && npm run format

# verify
cd backend && ./gradlew spotlessCheck
cd frontend && npm run format:check
```

## Production container

Build the same pinned, multi-stage artifact used by CI from the repository root:

```bash
docker build --pull --tag junto:local .
./scripts/verify-image.sh junto:local
```

The image serves the compiled Vue application and Spring Boot API together on
port 8080 and activates the `prod` profile by default. It needs an external
PostgreSQL/PostGIS database, the public application URL, and a Resend API key.
For a local production-profile run using the Compose database:

```bash
docker compose up -d --wait postgres
: "${RESEND_API_KEY:?set RESEND_API_KEY first}"
docker run --detach --name junto --publish 8080:8080 \
  --add-host host.docker.internal:host-gateway \
  --env DATABASE_URL='jdbc:postgresql://host.docker.internal:5432/junto?user=junto&password=junto' \
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
- Authentication emails carry the one-time magic-link token in
  `/auth/verify#token=...`. The fragment is absent from the HTTP request; the SPA
  consumes it and replaces the current URL and history entry before exchanging it
  through `POST /api/auth/verify-token`. Legacy query-string tokens are scrubbed
  from browser history but are not accepted.
- Spring Security sends `Referrer-Policy: no-referrer`, frame denial, MIME-sniffing
  protection, and the restrictive CSP declared in `BrowserSecurityHeaders`. The
  CSP permits only the same-origin application plus its required Google Fonts,
  CARTO tile, and Unsplash image origins. One-year HSTS with subdomains is emitted
  only for requests recognized as secure; behind Render, that recognition belongs
  to the trusted production proxy runtime configuration.

## Onda migration

Do not reuse, delete, or reinitialize an existing Onda Docker volume. Follow the
backup, logical-copy, session-expiration, verification, and rollback procedure in
[`docs/operations/onda-to-junto.md`](docs/operations/onda-to-junto.md).
