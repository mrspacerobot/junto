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

build_archive() {
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

platform_manifest() {
  local archive="$1"
  python3 - "$archive" "$platform_os" "$platform_arch" "${platform_variant:-}" <<'PY'
import json
import sys
import tarfile

archive, wanted_os, wanted_arch, wanted_variant = sys.argv[1:]
with tarfile.open(archive) as image:
    index = json.load(image.extractfile("index.json"))
matches = []
for manifest in index.get("manifests", []):
    platform = manifest.get("platform", {})
    if platform.get("os") != wanted_os or platform.get("architecture") != wanted_arch:
        continue
    if wanted_variant and platform.get("variant", "") != wanted_variant:
        continue
    matches.append(manifest["digest"])
if len(matches) != 1:
    raise SystemExit(
        f"expected one {wanted_os}/{wanted_arch} platform manifest, found {len(matches)}"
    )
print(matches[0])
PY
}

build_archive "$tmpdir/first.tar"
build_archive "$tmpdir/second.tar"
first_manifest="$(platform_manifest "$tmpdir/first.tar")"
second_manifest="$(platform_manifest "$tmpdir/second.tar")"
[ "$first_manifest" = "$second_manifest" ] || \
  fail "same-commit platform manifests differ: $first_manifest != $second_manifest"

# Load the exact first artifact for the remaining image/runtime checks. The
# equality build disables provenance only because per-build attestations are
# expected to change an outer index; the runtime platform manifest is the
# reproducibility contract.
docker load --input "$tmpdir/first.tar" >/dev/null
docker image inspect "$image" >/dev/null 2>&1 || fail "reproducible image was not loaded as $image"
printf 'verified image reproducibility: two clean %s builds at epoch %s produced platform manifest %s\n' \
  "$platform" "$source_date_epoch" "$first_manifest"
