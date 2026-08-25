#!/usr/bin/env bash
set -euo pipefail

# Catalogues and scans everything a release candidate ships: the image's
# operating-system packages and the application's Java dependencies, which the
# distroless image carries inside /app/junto.jar. The published SBOM is exactly
# the document that was scanned, and the severity summary it writes is what the
# release record reports to whoever approves the production promotion.
#
#   scan-release-dependencies.sh <image> <output-directory>
#
# The scan reports; it does not block. Junto's current dependency set already
# carries fixable high and critical advisories, so a blocking threshold here
# would only ever prevent releases. Issue #32 owns turning this into a policy
# gate once Renovate keeps dependencies current; a repository-root .trivyignore
# is honoured already and is the intended seam.

image="${1:-}"
output_directory="${2:-}"
repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

# Pinned by digest for the same reason the image secret scan pins its scanner:
# a release must be reproducible from its inputs.
scanner_image="aquasec/trivy:0.69.3@sha256:bcc376de8d77cfe086a917230e818dc9f8528e3c852f7b1aff648949b6258d1c"

workdir="$(mktemp -d)"
trap 'rm -rf "$workdir"' EXIT

fail() {
  printf 'release dependency scan failed: %s\n' "$*" >&2
  exit 1
}

[ -n "$image" ] && [ -n "$output_directory" ] || fail "usage: scan-release-dependencies.sh <image> <output-directory>"
command -v docker >/dev/null || fail "docker is required"
command -v jq >/dev/null || fail "jq is required"
docker image inspect "$image" >/dev/null 2>&1 || fail "image not found: $image"

mkdir -p "$output_directory"
output_directory="$(cd "$output_directory" && pwd)"
mkdir -p "$workdir/cache" "$workdir/out"

ignore_mount=()
ignore_arguments=()
if [ -f "$repository_root/.trivyignore" ]; then
  ignore_mount=(--volume "$repository_root/.trivyignore:/scan/.trivyignore:ro")
  ignore_arguments=(--ignorefile /scan/.trivyignore)
fi

trivy() {
  docker run --rm \
    --volume "$workdir:/scan" \
    "${ignore_mount[@]}" \
    "$scanner_image" \
    --cache-dir /scan/cache \
    --skip-version-check \
    --no-progress \
    "$@"
}

# --- Catalogue ---------------------------------------------------------------
docker image save --output "$workdir/image.tar" "$image"
trivy image \
  --input /scan/image.tar \
  --format cyclonedx \
  --output /scan/out/sbom.cdx.json \
  || fail "could not catalogue the release candidate"

sbom="$workdir/out/sbom.cdx.json"

# Both dependency classes must actually appear. Trivy silently produces an empty
# catalogue when it cannot recognise a target, and an empty SBOM would otherwise
# scan clean and look like a passing release.
count_components() {
  jq --arg prefix "$1" '[.components[]? | select((.purl // "") | startswith($prefix))] | length' "$sbom"
}
operating_system_components=$(( $(count_components 'pkg:deb') + $(count_components 'pkg:apk') + $(count_components 'pkg:rpm') ))
application_components="$(count_components 'pkg:maven')"
[ "$operating_system_components" -gt 0 ] || fail "the SBOM catalogues no image operating-system packages"
[ "$application_components" -gt 0 ] || fail "the SBOM catalogues no application Java dependencies"

# --- Scan --------------------------------------------------------------------
trivy sbom "/scan/out/sbom.cdx.json" \
  --scanners vuln \
  --ignore-unfixed \
  --format json \
  --output /scan/out/vulnerabilities.json \
  "${ignore_arguments[@]}" \
  || fail "could not scan the release candidate's dependencies"

jq '{
  fixable: ([.Results[]?.Vulnerabilities[]?.Severity] | group_by(.) | map({key: .[0], value: length}) | from_entries)
} | {
  critical: (.fixable.CRITICAL // 0),
  high: (.fixable.HIGH // 0),
  medium: (.fixable.MEDIUM // 0),
  low: (.fixable.LOW // 0)
}' "$workdir/out/vulnerabilities.json" > "$workdir/out/vulnerability-summary.json"

trivy sbom "/scan/out/sbom.cdx.json" \
  --scanners vuln \
  --ignore-unfixed \
  --severity HIGH,CRITICAL \
  "${ignore_arguments[@]}" \
  || true

cp "$workdir/out/sbom.cdx.json" \
   "$workdir/out/vulnerabilities.json" \
   "$workdir/out/vulnerability-summary.json" \
   "$output_directory/"

printf '\nscanned release dependencies: %s operating-system and %s application components catalogued; fixable advisories %s\n' \
  "$operating_system_components" \
  "$application_components" \
  "$(jq -c . "$workdir/out/vulnerability-summary.json")"
