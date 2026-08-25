#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "$script_dir/.." && pwd)"
image="${1:-junto:local}"
platform="${2:-linux/amd64}"
source_date_epoch="${3:-$(git -C "$repository_root" show -s --format=%ct HEAD)}"
assert_reproducible=false
tmpdir="$(mktemp -d)"

cleanup() {
  rm -rf "$tmpdir"
}
trap cleanup EXIT

fail() {
  printf 'production image build failed: %s\n' "$*" >&2
  exit 1
}

for option in "${@:4}"; do
  case "$option" in
    --assert-reproducible) assert_reproducible=true ;;
    *) fail "unknown option: $option" ;;
  esac
done

command -v docker >/dev/null || fail "docker is required"
command -v python3 >/dev/null || fail "python3 is required"
[[ "$source_date_epoch" =~ ^[0-9]+$ ]] || fail "source epoch must be Unix seconds"
IFS=/ read -r platform_os platform_arch platform_variant platform_extra <<<"$platform"
[ -n "$platform_os" ] && [ -n "$platform_arch" ] && [ -z "${platform_extra:-}" ] || \
  fail "platform must have os/architecture[/variant] form"

build_image_archive() {
  local destination="$1"
  local metadata="$2"
  docker buildx build \
    --pull \
    --no-cache \
    --platform "$platform" \
    --provenance=false \
    --build-arg "SOURCE_DATE_EPOCH=$source_date_epoch" \
    --tag "$image" \
    --metadata-file "$metadata" \
    --output "type=docker,dest=$destination" \
    "$repository_root"
}

platform_manifest_digest() {
  local metadata_file="$1"
  python3 - "$metadata_file" <<'PY'
import json
import re
import sys

with open(sys.argv[1], encoding="utf-8") as source:
    metadata = json.load(source)

digest = metadata.get("containerimage.digest")
descriptor = metadata.get("containerimage.descriptor", {})
manifest_media_types = {
    "application/vnd.docker.distribution.manifest.v2+json",
    "application/vnd.oci.image.manifest.v1+json",
}
if not isinstance(digest, str) or not re.fullmatch(r"sha256:[0-9a-f]{64}", digest):
    raise SystemExit("build metadata did not contain a sha256 image digest")
if descriptor.get("digest") != digest:
    raise SystemExit("build metadata descriptor and image digests differ")
if descriptor.get("mediaType") not in manifest_media_types:
    raise SystemExit("build metadata does not describe a single-platform manifest")
print(digest)
PY
}

build_image_archive "$tmpdir/first.tar" "$tmpdir/first.json"
digest="$(platform_manifest_digest "$tmpdir/first.json")"

# The reproducibility assertion doubles the cost of this job, so it runs where a
# regression must be caught: on master and on pull requests.
if [ "$assert_reproducible" = true ]; then
  build_image_archive "$tmpdir/second.tar" "$tmpdir/second.json"
  second_digest="$(platform_manifest_digest "$tmpdir/second.json")"
  [ "$digest" = "$second_digest" ] || \
    fail "same-commit platform manifests differ: $digest != $second_digest"
fi

# Hand the built artifact itself to the later inspection and smoke steps, rather
# than rebuilding. When the assertion ran, the image they check is one of the two
# builds whose digests were just compared.
docker load --input "$tmpdir/first.tar" >/dev/null
docker image inspect "$image" >/dev/null 2>&1 || fail "production image was not loaded as $image"

if [ "$assert_reproducible" = true ]; then
  printf 'built and verified reproducible: two clean %s builds at epoch %s produced platform manifest %s\n' \
    "$platform" "$source_date_epoch" "$digest"
else
  printf 'built production image: one clean %s build at epoch %s produced platform manifest %s\n' \
    "$platform" "$source_date_epoch" "$digest"
fi
