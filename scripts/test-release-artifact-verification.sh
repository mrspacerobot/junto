#!/usr/bin/env bash
set -euo pipefail

# Proves that verify-release-artifact.sh actually rejects a release that carries
# development material or a mismatched identity, rather than passing vacuously.
#
#   test-release-artifact-verification.sh <jar> <release-metadata.json>

jar="${1:-}"
metadata="${2:-}"
repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
verifier="$repository_root/scripts/verify-release-artifact.sh"
workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

fail() {
  printf 'release artifact verification coverage failed: %s\n' "$*" >&2
  exit 1
}

[ -n "$jar" ] && [ -n "$metadata" ] || fail "usage: test-release-artifact-verification.sh <jar> <release-metadata.json>"
command -v zip >/dev/null || fail "zip is required"
[ -f "$jar" ] || fail "archive not found: $jar"

"$verifier" "$jar" "$metadata" >/dev/null || fail "the unmodified release artifact must pass verification"

expect_rejection() {
  local description="$1" candidate="$2" candidate_metadata="$3"
  if "$verifier" "$candidate" "$candidate_metadata" >/dev/null 2>&1; then
    fail "verification accepted $description"
  fi
  printf '  rejected %s\n' "$description"
}

# Each canary reintroduces one thing a release must never carry.
add_entry_canary() {
  local description="$1" entry="$2" content="$3"
  local candidate="$workdir/$(printf '%s' "$description" | tr -c 'a-z0-9' '-').jar"
  cp "$jar" "$candidate"
  mkdir -p "$workdir/staging/$(dirname "$entry")"
  printf '%s\n' "$content" > "$workdir/staging/$entry"
  (cd "$workdir/staging" && zip -q "$candidate" "$entry")
  expect_rejection "$description" "$candidate" "$metadata"
}

add_entry_canary 'a development profile configuration' \
  'BOOT-INF/classes/application-dev.yml' 'app: {}'
add_entry_canary 'a development seed migration' \
  'BOOT-INF/classes/db/migration-dev/V999__seed_dev_data.sql' 'select 1;'
add_entry_canary 'a bypass login class' \
  'BOOT-INF/classes/ch/junto/auth/DevAuthController.class' 'not-a-real-class'
add_entry_canary 'a bypass login inner class' \
  'BOOT-INF/classes/ch/junto/auth/DevAuthController$Request.class' 'not-a-real-class'

jq '.version = "9.9.9-not-this-build"' "$metadata" > "$workdir/mismatched.json"
expect_rejection 'an artifact whose identity disagrees with the release metadata' "$jar" "$workdir/mismatched.json"

stripped="$workdir/stripped.jar"
cp "$jar" "$stripped"
zip -q -d "$stripped" 'META-INF/build-info.properties'
expect_rejection 'an artifact with no recorded release identity' "$stripped" "$metadata"

timestamped="$workdir/timestamped.jar"
cp "$jar" "$timestamped"
mkdir -p "$workdir/timestamped-staging/META-INF"
{ unzip -p "$jar" META-INF/build-info.properties; printf 'build.time=2026-01-01T00:00:00Z\n'; } \
  > "$workdir/timestamped-staging/META-INF/build-info.properties"
(cd "$workdir/timestamped-staging" && zip -q "$timestamped" 'META-INF/build-info.properties')
expect_rejection 'an artifact stamped with a build time' "$timestamped" "$metadata"

printf 'verified release artifact checks: development profile, seed, bypass login, identity, and reproducibility canaries were all rejected\n'
