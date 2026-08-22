#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "$script_dir/.." && pwd)"
tmpdir="$(mktemp -d)"
context="$tmpdir/context"
output="$tmpdir/output"

cleanup() {
  rm -rf "$tmpdir"
}
trap cleanup EXIT

fail() {
  printf 'Docker context regression failed: %s\n' "$*" >&2
  exit 1
}

command -v docker >/dev/null || fail "docker is required"
command -v git >/dev/null || fail "git is required"

mkdir -p "$context"
git -C "$repository_root" archive HEAD | tar -xf - -C "$context"
# Exercise working-tree policy changes too, so this test is useful before they
# are committed as well as in CI.
cp "$repository_root/.dockerignore" "$context/.dockerignore"
cp "$repository_root/Dockerfile" "$context/Dockerfile"

# Representative files that must remain unavailable even if a future build
# uses a broad COPY or context mount.
mkdir -p \
  "$context/frontend/node_modules/audit-dependency" \
  "$context/frontend/dist" \
  "$context/frontend/src/components" \
  "$context/backend/build/classes" \
  "$context/backend/.gradle/caches" \
  "$context/backend/src/test/java/audit" \
  "$context/backend/src/main/resources" \
  "$context/.git"
printf 'forbidden\n' >"$context/frontend/node_modules/audit-dependency/index.js"
printf 'forbidden\n' >"$context/frontend/dist/audit-bundle.js"
printf 'forbidden\n' >"$context/frontend/src/components/Audit.spec.ts"
printf 'forbidden\n' >"$context/frontend/.env.production.local"
printf 'forbidden\n' >"$context/backend/build/classes/Audit.class"
printf 'forbidden\n' >"$context/backend/.gradle/caches/audit.bin"
printf 'forbidden\n' >"$context/backend/src/test/java/audit/AuditTest.java"
printf 'forbidden\n' >"$context/backend/src/main/resources/.env.production"
printf 'forbidden\n' >"$context/.git/config"

# Export what COPY . can actually see after Docker applies .dockerignore.
docker buildx build \
  --file - \
  --output "type=local,dest=$output" \
  "$context" >/dev/null <<'EOF'
# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e
FROM scratch
COPY . /context
EOF

for forbidden in \
  frontend/node_modules/audit-dependency/index.js \
  frontend/dist/audit-bundle.js \
  frontend/src/components/Audit.spec.ts \
  frontend/.env.production.local \
  backend/build/classes/Audit.class \
  backend/.gradle/caches/audit.bin \
  backend/src/test/java/audit/AuditTest.java \
  backend/src/main/resources/.env.production \
  docs/recovery-provenance.md \
  .git/config; do
  [ ! -e "$output/context/$forbidden" ] || fail "forbidden path entered context: $forbidden"
done

# Build the exhaustive expected file list from the explicit production inputs
# consumed by Dockerfile. An exact diff catches both missing inputs and any
# newly reopened subtree.
{
  printf '%s\n' \
    Dockerfile \
    docs/openapi.json \
    frontend/package.json \
    frontend/package-lock.json \
    frontend/openapi-ts.config.ts \
    frontend/index.html \
    frontend/postcss.config.js \
    frontend/tailwind.config.js \
    frontend/tsconfig.json \
    frontend/tsconfig.app.json \
    frontend/tsconfig.node.json \
    frontend/vite.config.ts \
    backend/build.gradle \
    backend/settings.gradle \
    backend/gradlew
  find "$context/frontend/public" -type f -print
  find "$context/frontend/src" -type f \
    ! -name '*.spec.*' ! -name '*.test.*' \
    ! -name '.env' ! -name '.env.*' -print
  find "$context/backend/gradle" -type f -print
  find "$context/backend/src/main" -type f \
    ! -path '*/resources/application-dev.yml' \
    ! -path '*/resources/application-openapi.yml' \
    ! -path '*/resources/application-test.yml' \
    ! -path '*/resources/db/migration-dev/*' \
    ! -name '.env' ! -name '.env.*' -print
} | sed "s#^${context}/##" | LC_ALL=C sort -u >"$tmpdir/expected.list"
(
  cd "$output/context"
  find . -type f -print | sed 's#^./##' | LC_ALL=C sort
) >"$tmpdir/actual.list"

if ! diff -u "$tmpdir/expected.list" "$tmpdir/actual.list"; then
  fail "effective context differs from the narrow production allowlist"
fi

printf 'verified Docker context: every production input available; dependencies, build output, tests, and local secrets excluded\n'
