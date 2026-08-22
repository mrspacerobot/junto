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

# Representative untracked artefacts that a real working tree carries and that
# must stay unavailable even if a future build uses a broad COPY.
mkdir -p \
  "$context/frontend/node_modules/audit-dependency" \
  "$context/frontend/dist" \
  "$context/frontend/src/api/generated" \
  "$context/backend/build/classes" \
  "$context/backend/.gradle/caches" \
  "$context/.git"
printf 'forbidden\n' >"$context/frontend/node_modules/audit-dependency/index.js"
printf 'forbidden\n' >"$context/frontend/dist/audit-bundle.js"
printf 'forbidden\n' >"$context/frontend/src/api/generated/sdk.gen.ts"
printf 'forbidden\n' >"$context/frontend/.env.production.local"
printf 'forbidden\n' >"$context/backend/build/classes/Audit.class"
printf 'forbidden\n' >"$context/backend/.gradle/caches/audit.bin"
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

(
  cd "$output/context"
  find . -type f -print | sed 's#^./##' | LC_ALL=C sort
) >"$tmpdir/actual.list"
git -C "$repository_root" ls-files | LC_ALL=C sort >"$tmpdir/tracked.list"

# Losing a production input fails closed: the real build stops at its COPY. Only
# the fail-open direction needs a test.

# 1. Nothing untracked may enter. One comparison covers every dependency, build
#    output, tooling cache, VCS directory, and local dotenv credential.
untracked="$(LC_ALL=C comm -23 "$tmpdir/actual.list" "$tmpdir/tracked.list")"
[ -z "$untracked" ] || {
  printf '%s\n' "$untracked" >&2
  fail "untracked paths entered the context"
}

# 2. Tracked paths must be local inputs copied by Dockerfile.
production_inputs='^Dockerfile$|^docs/openapi\.json$|^frontend/(package(-lock)?\.json|index\.html|openapi-ts\.config\.ts|postcss\.config\.js|tailwind\.config\.js|vite\.config\.ts|tsconfig(\.app|\.node)?\.json|public/.+|src/.+)$|^backend/(build\.gradle|settings\.gradle|gradlew|gradle/.+|src/main/.+)$'
outside_production_inputs="$(grep -Ev "$production_inputs" "$tmpdir/actual.list" || true)"
[ -z "$outside_production_inputs" ] || {
  printf '%s\n' "$outside_production_inputs" >&2
  fail "tracked paths outside Dockerfile inputs entered the context"
}

# 3. Non-production paths inside copied source trees must stay out too.
non_production='(^|/)(src/test|e2e)/|^frontend/src/api/generated/|\.(spec|test)\.[^/]+$|(^|/)application-(dev|openapi|test)\.yml$|(^|/)db/migration-dev/|(^|/)\.env(\..*)?$'
if grep -Eq "$non_production" "$tmpdir/actual.list"; then
  grep -E "$non_production" "$tmpdir/actual.list" >&2
  fail "generated code, tests, local secrets, or non-production resources entered the context"
fi

printf 'verified Docker context: %s tracked production files; dependencies, build output, tests, and local secrets excluded\n' \
  "$(wc -l <"$tmpdir/actual.list" | tr -d ' ')"
