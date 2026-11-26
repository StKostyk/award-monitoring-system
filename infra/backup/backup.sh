#!/bin/sh
# backup.sh       runs crond with BACKUP_CRON (the container's command)
# backup.sh now   one backup at once: db-T.dump.age, documents-T.tar.age, then manifest-T.json to BACKUP_REMOTE
set -eu
set -o pipefail
. /usr/local/bin/common.sh

STATE="${BACKUP_STATE_DIR:-/var/lib/backup}"

schedule() {
  require BACKUP_CRON
  mkdir -p "$STATE" /etc/crontabs
  date +%s > "$STATE/started"
  echo "$BACKUP_CRON /usr/local/bin/backup.sh now > /proc/1/fd/1 2> /proc/1/fd/2" > /etc/crontabs/root
  echo "backup: scheduled at '$BACKUP_CRON' ($TZ)"
  exec crond -f -l 8 -c /etc/crontabs
}

upload() {
  rclone copyto --retries 3 --low-level-retries 3 "$WORK/$1" "$BACKUP_REMOTE/$1" \
    || fail "upload of $1 failed"
}

entry() {
  printf '{"name": "%s", "size": %s, "sha256": "%s"}' "$1" "$(wc -c < "$WORK/$1" | tr -d ' ')" \
    "$(sha256sum "$WORK/$1" | cut -d ' ' -f 1)"
}

prune() {
  [ "${BACKUP_KEEP_DAYS:-0}" -gt 0 ] || return 0
  cutoff=$(date -d "@$(( $(date +%s) - BACKUP_KEEP_DAYS * 86400 ))" +%Y%m%dT%H%M%S)
  rclone lsf "$BACKUP_REMOTE" | while read -r name; do
    stamp=$(echo "$name" | sed -n 's/^[a-z]*-\([0-9]\{8\}T[0-9]\{6\}\)\..*$/\1/p')
    if [ -n "$stamp" ] && [ "$stamp" \< "$cutoff" ]; then
      rclone deletefile "$BACKUP_REMOTE/$name" 2>/dev/null && echo "backup: expired $name" \
        || echo "backup: $name is older than $BACKUP_KEEP_DAYS days but the remote refused to delete it" >&2
    fi
  done
}

run() {
  require PGHOST PGDATABASE PGUSER PGPASSWORD MINIO_ENDPOINT MINIO_ACCESS_KEY MINIO_SECRET_KEY BACKUP_REMOTE \
    BACKUP_AGE_RECIPIENT
  mkdir -p "$STATE"
  exec 9> "$STATE/lock"
  flock -n 9 || fail "another backup is running"
  start_work
  stamp=$(date +%Y%m%dT%H%M%S)
  db="db-$stamp.dump.age"
  documents="documents-$stamp.tar.age"
  manifest="manifest-$stamp.json"

  users=$(count users)
  awards=$(count awards)
  pg_dump -Fc | age -r "$BACKUP_AGE_RECIPIENT" -o "$WORK/$db" || fail "database dump failed"
  upload "$db"

  mkdir "$WORK/objects"
  mcli mirror --quiet "store/$BUCKET" "$WORK/objects" >/dev/null || fail "reading $BUCKET failed"
  objects=$(find "$WORK/objects" -type f | wc -l | tr -d ' ')
  tar -C "$WORK/objects" -cf - . | age -r "$BACKUP_AGE_RECIPIENT" -o "$WORK/$documents" \
    || fail "document archive failed"
  rm -rf "$WORK/objects"
  upload "$documents"

  printf '{"time": "%s", "image": "%s", "files": [%s, %s], "users": %s, "awards": %s, "objects": %s}\n' \
    "$stamp" "${BACKUP_IMAGE_TAG:-unknown}" "$(entry "$db")" "$(entry "$documents")" "$users" "$awards" \
    "$objects" | jq . > "$WORK/$manifest"
  upload "$manifest"
  mkdir -p "$STATE"
  date +%s > "$STATE/last-success"
  echo "backup: $stamp written ($users users, $awards awards, $objects objects)"
  prune
}

case "${1:-}" in
  now) run ;;
  "") schedule ;;
  *) fail "usage: backup.sh [now]" ;;
esac
