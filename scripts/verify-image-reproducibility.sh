#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "$script_dir/.." && pwd)"
image="${1:-junto:reproducible}"
platform="${2:-linux/amd64}"
source_date_epoch="${3:-$(git -C "$repository_root" show -s --format=%ct HEAD)}"
tmpdir="$(mktemp -d)"

cleanup() {
  rm -rf "$tmpdir"
}
trap cleanup EXIT

fail() {
  printf 'image reproducibility verification failed: %s\n' "$*" >&2
  exit 1
}

command -v docker >/dev/null || fail "docker is required"
command -v python3 >/dev/null || fail "python3 is required"
[[ "$source_date_epoch" =~ ^[0-9]+$ ]] || fail "source epoch must be Unix seconds"
IFS=/ read -r platform_os platform_arch platform_variant platform_extra <<<"$platform"
[ -n "$platform_os" ] && [ -n "$platform_arch" ] && [ -z "${platform_extra:-}" ] || \
  fail "platform must have os/architecture[/variant] form"

build_oci_archive() {
  local destination="$1"
  docker buildx build \
    --pull \
    --no-cache \
    --platform "$platform" \
    --provenance=false \
    --build-arg "SOURCE_DATE_EPOCH=$source_date_epoch" \
    --tag "$image" \
    --output "type=oci,dest=$destination" \
    "$repository_root"
}

platform_manifest_digest() {
  local archive="$1"
  python3 - "$archive" "$platform_os" "$platform_arch" "${platform_variant:-}" <<'PY'
import json
import sys
import tarfile

archive, wanted_os, wanted_arch, wanted_variant = sys.argv[1:]
with tarfile.open(archive) as tf:
    index = json.load(tf.extractfile("index.json"))
    candidates = []
    for m in index.get("manifests", []):
        p = m.get("platform", {})
        if p.get("os") == wanted_os and p.get("architecture") == wanted_arch:
            if not wanted_variant or p.get("variant", "") == wanted_variant:
                candidates.append(m["digest"])
    if len(candidates) != 1:
        raise SystemExit(
            f"expected exactly one {wanted_os}/{wanted_arch} manifest in "
            f"index.json, found {len(candidates)}"
        )
    print(candidates[0])
PY
}

build_oci_archive "$tmpdir/first.tar"
build_oci_archive "$tmpdir/second.tar"
first_digest="$(platform_manifest_digest "$tmpdir/first.tar")"
second_digest="$(platform_manifest_digest "$tmpdir/second.tar")"
[ "$first_digest" = "$second_digest" ] || \
  fail "same-commit platform manifests differ: $first_digest != $second_digest"

# Load the production image for runtime checks via a separate cache-enabled
# build. The OCI archives above are the reproducibility contract; this build
# just makes the image available to docker for inspection and smoke tests.
docker buildx build \
  --platform "$platform" \
  --provenance=false \
  --build-arg "SOURCE_DATE_EPOCH=$source_date_epoch" \
  --tag "$image" \
  --load \
  "$repository_root"
docker image inspect "$image" >/dev/null 2>&1 || fail "production image was not loaded as $image"
printf 'verified image reproducibility: two clean %s builds at epoch %s produced platform manifest %s\n' \
  "$platform" "$source_date_epoch" "$first_digest"
