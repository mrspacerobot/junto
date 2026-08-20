#!/usr/bin/env bash
set -euo pipefail

image="${1:-junto:local}"
credential_name='(DATABASE_URL|RESEND_API_KEY|AWS_ACCESS_KEY_ID|AWS_SECRET_ACCESS_KEY|[[:alnum:]_]*(PASSWORD|PASSWD|SECRET|TOKEN|PRIVATE_KEY|ACCESS_KEY)[[:alnum:]_]*)'
scanner_image="aquasec/trivy:0.69.3@sha256:bcc376de8d77cfe086a917230e818dc9f8528e3c852f7b1aff648949b6258d1c"
tmpdir="$(mktemp -d)"
container_id=""

cleanup() {
  if [ -n "$container_id" ]; then
    docker rm "$container_id" >/dev/null 2>&1 || true
  fi
  rm -rf "$tmpdir"
}
trap cleanup EXIT

fail() {
  printf 'image secret scan failed: %s\n' "$*" >&2
  exit 1
}

command -v docker >/dev/null || fail "docker is required"
command -v unzip >/dev/null || fail "unzip is required"
docker image inspect "$image" >/dev/null 2>&1 || fail "image not found: $image"

# Metadata first: Trivy scans layer content, not the credential-shaped variable
# names and ARG/ENV assignments that Docker records in config and history.
environment="$(docker image inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$image")"
if printf '%s\n' "$environment" | grep -Eiq "^${credential_name}="; then
  fail "credential-shaped environment metadata is present"
fi
if docker history --no-trunc --format '{{.CreatedBy}}' "$image" |
  grep -Eiq "(^|[[:space:]|])${credential_name}=[^[:space:]]"; then
  fail "credential-shaped ARG/ENV assignment is present in image history"
fi

docker image save --output "$tmpdir/image.tar" "$image"
docker run --rm \
  --volume "$tmpdir:/scan:ro" \
  "$scanner_image" image \
  --input /scan/image.tar \
  --scanners secret \
  --image-config-scanners secret \
  --skip-db-update \
  --skip-java-db-update \
  --skip-version-check \
  --no-progress \
  --exit-code 1

# Trivy's image scanner walks every image layer, including files hidden by a
# later whiteout, but it does not expand Spring Boot JARs. Scan the extracted
# application archive explicitly so packaged credentials receive the same
# pinned secret rules.
container_id="$(docker create "$image")"
docker cp "${container_id}:/app/junto.jar" "$tmpdir/junto.jar" >/dev/null 2>&1 || \
  fail "/app/junto.jar is missing from the image"
mkdir "$tmpdir/application"
unzip -qq "$tmpdir/junto.jar" -d "$tmpdir/application"
docker run --rm \
  --volume "$tmpdir/application:/scan/application:ro" \
  "$scanner_image" filesystem \
  --scanners secret \
  --skip-db-update \
  --skip-java-db-update \
  --skip-version-check \
  --no-progress \
  --exit-code 1 \
  /scan/application

docker rm "$container_id" >/dev/null
container_id=""
printf 'verified image secrets: no credential-shaped metadata; pinned layer scan and nested /app/junto.jar scan found no secret material\n'
