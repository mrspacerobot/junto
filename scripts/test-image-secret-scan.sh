#!/usr/bin/env bash
set -euo pipefail

script_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd "$script_dir/.." && pwd)"
suffix="$$"
nested_image="junto:secret-canary-nested-${suffix}"
deleted_image="junto:secret-canary-deleted-${suffix}"
history_image="junto:credential-canary-history-${suffix}"
tmpdir="$(mktemp -d)"

cleanup() {
  docker image rm "$nested_image" "$deleted_image" "$history_image" >/dev/null 2>&1 || true
  rm -rf "$tmpdir"
}
trap cleanup EXIT

fail() {
  printf 'image secret scan regression failed: %s\n' "$*" >&2
  exit 1
}

for command in docker openssl python3; do
  command -v "$command" >/dev/null || fail "$command is required"
done
docker_command="$(command -v docker)"

mkdir -p "$tmpdir/nested" "$tmpdir/deleted" "$tmpdir/history"
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 \
  -out "$tmpdir/private-key.pem" >/dev/null 2>&1
python3 - "$tmpdir/private-key.pem" "$tmpdir/nested/junto.jar" "$tmpdir/deleted/junto.jar" <<'PY'
import sys
import zipfile

with zipfile.ZipFile(sys.argv[2], "w") as archive:
    archive.write(sys.argv[1], "BOOT-INF/classes/audit-private-key.pem")
with zipfile.ZipFile(sys.argv[3], "w") as archive:
    archive.writestr("BOOT-INF/classes/audit-clean.txt", "no credentials here\n")
PY
cp "$tmpdir/private-key.pem" "$tmpdir/deleted/audit-private-key.pem"

cat >"$tmpdir/nested/Dockerfile" <<'EOF'
# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e
FROM scratch
COPY junto.jar /app/junto.jar
ENTRYPOINT ["/not-run"]
EOF
cat >"$tmpdir/deleted/Dockerfile" <<'EOF'
# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e
FROM node:22.22.0-alpine3.23@sha256:e4bf2a82ad0a4037d28035ae71529873c069b13eb0455466ae0bc13363826e34
COPY audit-private-key.pem /audit-private-key.pem
RUN rm /audit-private-key.pem
COPY junto.jar /app/junto.jar
EOF
cat >"$tmpdir/history/Dockerfile" <<'EOF'
# syntax=docker/dockerfile:1.7@sha256:a57df69d0ea827fb7266491f2813635de6f17269be881f696fbfdf2d83dda33e
FROM node:22.22.0-alpine3.23@sha256:e4bf2a82ad0a4037d28035ae71529873c069b13eb0455466ae0bc13363826e34
ARG AUDIT_PASSWORD
RUN true
EOF

docker build --quiet --tag "$nested_image" "$tmpdir/nested" >/dev/null
docker build --quiet --tag "$deleted_image" "$tmpdir/deleted" >/dev/null
docker build --quiet \
  --build-arg AUDIT_PASSWORD=credential-metadata-canary \
  --tag "$history_image" "$tmpdir/history" >/dev/null

if "$repository_root/scripts/scan-image-secrets.sh" "$nested_image" >"$tmpdir/result.log" 2>&1; then
  fail "nested private-key canary was accepted"
fi
grep -q 'audit-private-key.pem' "$tmpdir/result.log" || {
  cat "$tmpdir/result.log" >&2
  fail "nested archive failure did not identify the private-key canary"
}

if "$repository_root/scripts/scan-image-secrets.sh" "$deleted_image" >"$tmpdir/result.log" 2>&1; then
  fail "deleted-layer private-key canary was accepted"
fi
grep -q '/audit-private-key.pem' "$tmpdir/result.log" || {
  cat "$tmpdir/result.log" >&2
  fail "layer scan failure did not identify the deleted private-key canary"
}

if "$repository_root/scripts/scan-image-secrets.sh" "$history_image" >"$tmpdir/result.log" 2>&1; then
  fail "credential-bearing ARG history canary was accepted"
fi
grep -q 'credential-shaped ARG/ENV assignment is present in image history' "$tmpdir/result.log" || {
  cat "$tmpdir/result.log" >&2
  fail "history inspection did not identify the credential-shaped ARG"
}

if (
  docker() {
    if [ "${1:-}" = history ]; then
      return 42
    fi
    "$docker_command" "$@"
  }
  export -f docker
  export docker_command
  "$repository_root/scripts/scan-image-secrets.sh" "$nested_image"
) >"$tmpdir/result.log" 2>&1; then
  fail "failed image history inspection was accepted"
fi
grep -q 'could not inspect image history' "$tmpdir/result.log" || {
  cat "$tmpdir/result.log" >&2
  fail "history inspection failure did not stop the secret scan"
}

printf 'verified secret scan coverage: nested archive, deleted layer, ARG history, and history failures were rejected\n'
