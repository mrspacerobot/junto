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
digest="$(platform_manifest_digest "$tmpdir/first.tar")"

# The reproducibility assertion doubles the cost of this job, so it runs where a
# regression must be caught: on master and on pull requests.
if [ "$assert_reproducible" = true ]; then
  build_oci_archive "$tmpdir/second.tar"
  second_digest="$(platform_manifest_digest "$tmpdir/second.tar")"
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
