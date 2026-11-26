#!/bin/sh
# Shared settings of backup.sh and restore.sh: the work directory, the MinIO alias and the failure helper.
# Needs PGHOST, PGDATABASE, PGUSER, PGPASSWORD, MINIO_ENDPOINT, MINIO_ACCESS_KEY, MINIO_SECRET_KEY, BACKUP_REMOTE.

WORK="${WORK:-/tmp/work}"
export MC_CONFIG_DIR="$WORK/mc"
BUCKET="${BACKUP_DOCUMENTS_BUCKET:-award-documents}"

fail() {
  echo "backup: $*" >&2
  exit 1
}

require() {
  for name in "$@"; do
    eval "value=\${$name:-}"
    [ -n "$value" ] || fail "$name is not set"
  done
}

start_work() {
  rm -rf "$WORK"
  mkdir -p "$WORK"
  chmod 700 "$WORK"
  trap 'rm -rf "$WORK"' EXIT
  trap 'exit 130' INT TERM
  mcli alias set store "$MINIO_ENDPOINT" "$MINIO_ACCESS_KEY" "$MINIO_SECRET_KEY" >/dev/null \
    || fail "cannot reach MinIO at $MINIO_ENDPOINT"
}

count() {
  psql -tAq -c "select count(*) from $1"
}

object_count() {
  mcli ls --recursive "store/$BUCKET" | wc -l | tr -d ' '
}
