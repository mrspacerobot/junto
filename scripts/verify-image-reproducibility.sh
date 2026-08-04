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
    --output "type=docker,dest=$destination" \
    "$repository_root"
}

platform_manifest() {
  local archive="$1"
  python3 - "$archive" "$platform_os" "$platform_arch" "${platform_variant:-}" <<'PY'
import hashlib
import json
import sys
import tarfile

archive, wanted_os, wanted_arch, wanted_variant = sys.argv[1:]
with tarfile.open(archive) as image:
    manifest_json = json.load(image.extractfile("manifest.json"))
    if len(manifest_json) != 1:
        raise SystemExit(
            f"expected one image entry in docker archive, found {len(manifest_json)}"
        )
    config_path = manifest_json[0]["Config"]
    config_bytes = image.extractfile(config_path).read()
config = json.loads(config_bytes)
if config.get("os") != wanted_os or config.get("architecture") != wanted_arch:
    raise SystemExit(
        f"image platform {config.get('os')}/{config.get('architecture')} "
        f"does not match requested {wanted_os}/{wanted_arch}"
    )
if wanted_variant and config.get("variant", "") != wanted_variant:
    raise SystemExit(
        f"image variant {config.get('variant', '')} does not match requested {wanted_variant}"
    )
digest = "sha256:" + hashlib.sha256(config_bytes).hexdigest()
print(digest)
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
