#!/usr/bin/env bash
set -euo pipefail

image="${1:-junto:local}"
postgis_image="postgis/postgis:16-3.4-alpine@sha256:681931a625df344215e9b8998bf34daf146b6a395ceacee4439eb9c85869239f"
suffix="$$"
network="junto-image-verify-${suffix}"
database="junto-image-verify-db-${suffix}"
application="junto-image-verify-app-${suffix}"
tmpdir="$(mktemp -d)"
container_id=""

cleanup() {
  if [ -n "$container_id" ]; then
    docker rm -f "$container_id" >/dev/null 2>&1 || true
  fi
  docker rm -f "$application" "$database" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$tmpdir"
}
trap cleanup EXIT

fail() {
  printf 'image verification failed: %s\n' "$*" >&2
  exit 1
}

command -v docker >/dev/null || fail "docker is required"
command -v unzip >/dev/null || fail "unzip is required"
docker image inspect "$image" >/dev/null 2>&1 || fail "image not found: $image"

user="$(docker image inspect --format '{{.Config.User}}' "$image")"
case "$user" in
  65532 | 65532:65532 | nonroot | nonroot:nonroot) ;;
  *) fail "runtime user is '$user', not the non-root distroless user" ;;
esac

entrypoint="$(docker image inspect --format '{{json .Config.Entrypoint}}' "$image")"
[ "$entrypoint" = '["java","-jar","/app/junto.jar"]' ] || \
  fail "unexpected entrypoint: $entrypoint"

environment="$(docker image inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$image")"
printf '%s\n' "$environment" | grep -qx 'SPRING_PROFILES_ACTIVE=prod' || \
  fail "production Spring profile is not the image default"
if printf '%s\n' "$environment" | grep -Eq '^(DATABASE_URL|RESEND_API_KEY|.*PASSWORD|.*SECRET|.*TOKEN)='; then
  fail "runtime secret or credential is embedded in image metadata"
fi

container_id="$(docker create "$image")"
docker cp "${container_id}:/app/junto.jar" "$tmpdir/junto.jar"
docker export "$container_id" >"$tmpdir/rootfs.tar"
docker rm "$container_id" >/dev/null
container_id=""
tar -tf "$tmpdir/rootfs.tar" >"$tmpdir/rootfs.list"
unzip -Z1 "$tmpdir/junto.jar" >"$tmpdir/jar.list"

if grep -Eq '(^|/)(node|nodejs|npm|npx|javac|javadoc|javap|jlink|jmod|jshell)/?$' "$tmpdir/rootfs.list"; then
  grep -E '(^|/)(node|nodejs|npm|npx|javac|javadoc|javap|jlink|jmod|jshell)/?$' "$tmpdir/rootfs.list" >&2
  fail "Node/npm or JDK tooling is present in the runtime filesystem"
fi
if grep -Eq '(^|/)\.git(/|$)|(^|/)\.env([^/]*$)|^app/(build|dist|node_modules|src|test-results)(/|$)' "$tmpdir/rootfs.list"; then
  fail "source-control metadata, local environment, build output, or test output is present"
fi

grep -qx 'BOOT-INF/classes/static/index.html' "$tmpdir/jar.list" || \
  fail "Vue index is not packaged in the Spring Boot artifact"
grep -q '^BOOT-INF/classes/ch/junto/JuntoApplication.class$' "$tmpdir/jar.list" || \
  fail "Spring Boot application classes are missing"
if grep -Eq '(^|/)\.git(/|$)|(^|/)(src/test|test-results|db/migration-dev)(/|$)|(^|/)application-(dev|openapi|test)\.yml$|(^|/)[^/]*(Test|Tests)\.class$|\.(java|kt|ts)$' "$tmpdir/jar.list"; then
  fail "source-control metadata, non-production profiles, source, or test artifacts are packaged in the application"
fi

printf 'verified image contents: non-root Java 21 runtime plus /app/junto.jar (Spring classes and Vue assets)\n'

# Start the real production profile against disposable PostGIS, then ask Docker
# to send SIGTERM. The direct Java entrypoint receives it and Spring completes
# its graceful shutdown hook without a shell swallowing the signal.
docker network create "$network" >/dev/null
docker run -d --name "$database" --network "$network" \
  -e POSTGRES_DB=junto -e POSTGRES_USER=junto -e POSTGRES_PASSWORD=verification \
  "$postgis_image" >/dev/null

for _ in $(seq 1 90); do
  if docker exec "$database" pg_isready -h 127.0.0.1 -U junto -d junto >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$database" pg_isready -h 127.0.0.1 -U junto -d junto >/dev/null 2>&1 || \
  fail "disposable PostGIS did not become ready"

docker run -d --name "$application" --network "$network" \
  -e DATABASE_HOST="junto-image-verify-db-${suffix}" \
  -e DATABASE_PORT=5432 \
  -e DATABASE_NAME=junto \
  -e DATABASE_USERNAME=junto \
  -e DATABASE_PASSWORD=verification \
  -e APP_BASE_URL=http://localhost:8080 \
  -e RESEND_API_KEY=image-verification-only \
  "$image" >/dev/null

started=false
for _ in $(seq 1 120); do
  if docker logs "$application" 2>&1 | grep -q 'Started JuntoApplication'; then
    started=true
    break
  fi
  if [ "$(docker inspect --format '{{.State.Running}}' "$application" 2>/dev/null || true)" != true ]; then
    docker logs "$application" >&2 || true
    fail "application exited before startup"
  fi
  sleep 1
done
if [ "$started" != true ]; then
  docker logs "$application" >&2 || true
  fail "production application did not become ready"
fi

docker stop --time 35 "$application" >/dev/null
exit_code="$(docker inspect --format '{{.State.ExitCode}}' "$application")"
case "$exit_code" in
  0 | 143) ;;
  *) docker logs "$application" >&2 || true; fail "application exited with code $exit_code after SIGTERM" ;;
esac

printf 'verified runtime: production profile started as UID %s and stopped cleanly on SIGTERM (exit %s)\n' "$user" "$exit_code"
