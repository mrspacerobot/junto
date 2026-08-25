#!/usr/bin/env bash
set -euo pipefail

# Proves that a packaged application archive is a releasable production
# artifact: it carries the deterministic release identity CI recorded on the
# image, and it contains no development profile, seed data, or bypass login.
#
#   verify-release-artifact.sh <jar> <release-metadata.json>
#
# The metadata file is the output of `./gradlew -q releaseMetadata`.

jar="${1:-}"
metadata="${2:-}"
repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

fail() {
  printf 'release artifact verification failed: %s\n' "$*" >&2
  exit 1
}

[ -n "$jar" ] && [ -n "$metadata" ] || fail "usage: verify-release-artifact.sh <jar> <release-metadata.json>"
command -v unzip >/dev/null || fail "unzip is required"
command -v jq >/dev/null || fail "jq is required"
[ -f "$jar" ] || fail "archive not found: $jar"
[ -f "$metadata" ] || fail "release metadata not found: $metadata"

entries="$(unzip -Z1 "$jar")"

contains_entry() {
  printf '%s\n' "$entries" | grep -qx "$1"
}

matches_entry() {
  printf '%s\n' "$entries" | grep -qE "$1"
}

# --- Release identity --------------------------------------------------------
# Spring Boot writes the identity to the archive root, which the executable
# launcher exposes on the classpath as META-INF/build-info.properties.
contains_entry 'META-INF/build-info.properties' || fail "META-INF/build-info.properties is missing; the release identity is not recorded"
build_info="$(unzip -p "$jar" META-INF/build-info.properties)"

build_info_value() {
  printf '%s\n' "$build_info" | sed -n "s/^$1=//p" | head -1
}

assert_identity() {
  local property="$1" expected="$2" actual
  actual="$(build_info_value "$property")"
  [ -n "$actual" ] || fail "$property is absent from the recorded release identity"
  [ "$actual" = "$expected" ] || fail "$property is '$actual' but the release metadata declares '$expected'"
}

assert_identity 'build\.version' "$(jq -r '.version' "$metadata")"
assert_identity 'build\.schema\.version' "$(jq -r '.schemaVersion' "$metadata")"
assert_identity 'build\.config\.version' "$(jq -r '.configVersion' "$metadata")"

# A build timestamp would make two builds of the same commit differ and break
# the reproducible image assertion the production-image job relies on.
[ -z "$(build_info_value 'build\.time')" ] || fail "build.time is recorded; the artifact is not reproducible from its commit alone"

# --- Production inputs are present -------------------------------------------
for required in \
  'BOOT-INF/classes/application.yml' \
  'BOOT-INF/classes/application-prod.yml' \
  'BOOT-INF/classes/static/index.html'
do
  contains_entry "$required" || fail "required production input is missing: $required"
done
matches_entry '^BOOT-INF/classes/db/migration/V[0-9]+__.*\.sql$' || fail "no Flyway production migrations are packaged"

# --- No development profile, seed, or bypass login ---------------------------
for forbidden in application-dev.yml application-openapi.yml application-test.yml; do
  ! matches_entry "^BOOT-INF/classes/${forbidden}$" || fail "development profile configuration entered the release: $forbidden"
done
! matches_entry '^BOOT-INF/classes/db/migration-dev/' || fail "development seed migrations entered the release"

# Bypass endpoints are declared by their Spring profile, so derive the list from
# the sources rather than from a hand-maintained copy that a rename would stale.
while IFS= read -r source; do
  [ -n "$source" ] || continue
  class_path="${source#"$repository_root"/backend/src/main/java/}"
  class_path="${class_path%.java}"
  ! matches_entry "^BOOT-INF/classes/${class_path}(\\\$[^/]*)?\.class$" \
    || fail "development-profile class entered the release: ${class_path}.class"
done < <(grep -rl '@Profile("dev")' "$repository_root/backend/src/main/java" || true)

printf 'verified release artifact %s: identity %s/schema %s/config %s recorded, no development profile, seed, or bypass login packaged\n' \
  "$(basename "$jar")" \
  "$(jq -r '.version' "$metadata")" \
  "$(jq -r '.schemaVersion' "$metadata")" \
  "$(jq -r '.configVersion' "$metadata")"
