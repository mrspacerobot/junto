#!/usr/bin/env bash
set -euo pipefail

# Renders the release record that accompanies a candidate or a production
# promotion, and refuses to render an incomplete one. The record is the
# reviewable statement of what a release is and how to leave it: commit, image
# identity, schema version, configuration version, backup, and rollback target.
#
#   render-release-record.sh candidate|promotion|validate-backup
#
# Values arrive through the environment so the workflows stay declarative; every
# name below is required and must be non-empty.

mode="${1:-}"

fail() {
  printf 'release record rendering failed: %s\n' "$*" >&2
  exit 1
}

validate_backup_reference() {
  local normalized reference
  reference="${1:-}"
  case "$reference" in
    *$'\n'* | *$'\r'* | *'|'*) fail "BACKUP_REFERENCE must be a single safe release-record value" ;;
  esac
  normalized="$(printf '%s' "$reference" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//' | tr '[:upper:]' '[:lower:]')"
  [ -n "$normalized" ] || fail "BACKUP_REFERENCE must name a verified backup"
  case "$normalized" in
    none | n/a | na | tbd | pending | todo | -) fail "BACKUP_REFERENCE must name a verified backup, not '$reference'" ;;
  esac
}

if [ "$mode" = validate-backup ]; then
  validate_backup_reference "${BACKUP_REFERENCE:-}"
  exit 0
fi

common_fields=(
  RELEASE_TAG
  COMMIT
  IMAGE_REFERENCE
  IMAGE_DIGEST
  APP_VERSION
  SCHEMA_VERSION
  CONFIG_VERSION
  ROLLBACK_TARGET
  VULNERABILITY_SUMMARY
  WORKFLOW_RUN_URL
)
promotion_fields=(
  BACKUP_REFERENCE
  PROMOTED_BY
  HEALTH_CHECK_RESULT
  PREVIOUS_PRODUCTION_DIGEST
)

case "$mode" in
  candidate) required_fields=("${common_fields[@]}") ;;
  promotion) required_fields=("${common_fields[@]}" "${promotion_fields[@]}") ;;
  *) fail "usage: render-release-record.sh candidate|promotion|validate-backup" ;;
esac

for field in "${required_fields[@]}"; do
  [ -n "${!field:-}" ] || fail "$field is required for a $mode record but is empty"
done

# A promotion must name the backup it can be recovered to. Refusing the obvious
# placeholders keeps "we will take one later" out of a signed release record.
if [ "$mode" = promotion ]; then
  validate_backup_reference "$BACKUP_REFERENCE"
fi

cat <<RECORD
## Release record

| Field | Value |
| --- | --- |
| Release | \`$RELEASE_TAG\` |
| Commit | \`$COMMIT\` |
| Image | \`$IMAGE_REFERENCE\` |
| Image digest | \`$IMAGE_DIGEST\` |
| Application version | \`$APP_VERSION\` |
| Schema version (Flyway) | \`$SCHEMA_VERSION\` |
| Configuration version | \`$CONFIG_VERSION\` |
| Backup | ${BACKUP_REFERENCE:-**not captured: required before promotion**} |
| Rollback target | \`$ROLLBACK_TARGET\` |
| Fixable advisories | \`$VULNERABILITY_SUMMARY\` |
| Workflow run | $WORKFLOW_RUN_URL |

The image is addressed by digest, so \`$IMAGE_DIGEST\` is the release. The tags
that point at it are conveniences and may move; the digest never does.
RECORD

if [ "$mode" = candidate ]; then
  cat <<'RECORD'

### Status: candidate, not promoted

This image passed the backend, frontend, end-to-end, and production-image
checks before it was published, and it carries no development profile, seed
data, or bypass login. It is **not** in production. Promotion is manual and
explicitly approved: run the `Promote to production` workflow, which requires a
verified backup reference and a reviewer on the `production` environment.
RECORD
else
  cat <<RECORD

### Status: promoted to production

| Field | Value |
| --- | --- |
| Promoted by | @$PROMOTED_BY |
| Post-promotion health | $HEALTH_CHECK_RESULT |
| Previous production digest | \`$PREVIOUS_PRODUCTION_DIGEST\` |

To roll the application back, promote the candidate commit associated with
\`$PREVIOUS_PRODUCTION_DIGEST\` through the same workflow. That reverses
application code only: schema version
\`$SCHEMA_VERSION\` is forward-only, so a rollback across a migration boundary
needs the database recovery procedure in
\`docs/operations/release-and-promotion.md\`.
RECORD
fi
