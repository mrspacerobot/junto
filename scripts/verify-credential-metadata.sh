#!/usr/bin/env bash
set -euo pipefail

image="${1:-junto:local}"
credential_name='(DATABASE_URL|RESEND_API_KEY|AWS_ACCESS_KEY_ID|AWS_SECRET_ACCESS_KEY|[[:alnum:]_]*(PASSWORD|PASSWD|SECRET|TOKEN|PRIVATE_KEY|ACCESS_KEY)[[:alnum:]_]*)'

fail() {
  printf 'credential metadata verification failed: %s\n' "$*" >&2
  exit 1
}

command -v docker >/dev/null || fail "docker is required"
docker image inspect "$image" >/dev/null 2>&1 || fail "image not found: $image"

environment="$(docker image inspect --format '{{range .Config.Env}}{{println .}}{{end}}' "$image")"
if printf '%s\n' "$environment" | grep -Eiq "^${credential_name}="; then
  fail "credential-shaped environment metadata is present"
fi

history="$(docker history --no-trunc --format '{{.CreatedBy}}' "$image")"
if printf '%s\n' "$history" | grep -Eiq "(^|[[:space:]|])${credential_name}=[^[:space:]]"; then
  fail "credential-shaped ARG/ENV assignment is present in image history"
fi

printf 'verified credential metadata: no credential-shaped environment variables or ARG/ENV history assignments\n'
