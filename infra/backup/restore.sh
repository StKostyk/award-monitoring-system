#!/bin/sh
# restore.sh T    restores the database and the document objects of manifest-T.json, with the private key mounted
# read-only at /run/backup.key and app stopped. Every check runs before anything is written. The schema is dropped
# and the dump replayed in one transaction (pg_restore --clean cannot drop the inherited keys of the audit partitions).
set -eu
set -o pipefail
WORK=/tmp/restore
. /usr/local/bin/common.sh

KEY=/run/backup.key
STATE="${BACKUP_STATE_DIR:-/var/lib/backup}"
stamp="${1:-}"
echo "$stamp" | grep -qx '[0-9]\{8\}T[0-9]\{6\}' \
  || fail "usage: restore.sh <time of a manifest, e.g. 20261110T043000>"
db="db-$stamp.dump.age"
documents="documents-$stamp.tar.age"
require PGHOST PGDATABASE PGUSER PGPASSWORD MINIO_ENDPOINT MINIO_ACCESS_KEY MINIO_SECRET_KEY BACKUP_REMOTE
[ -r "$KEY" ] || fail "no key file: mount it with -v ./backup.key:$KEY:ro"
mkdir -p "$STATE"
exec 9> "$STATE/lock"
flock -n 9 || fail "a backup is running in this container: wait for it to finish"

sessions=$(psql -tAq -c "select count(*) from pg_stat_activity where datname = current_database() \
  and pid <> pg_backend_pid() and application_name like 'PostgreSQL JDBC%'")
[ "$sessions" -eq 0 ] || fail "app is running ($sessions database sessions): stop it first"

start_work
rclone copyto "$BACKUP_REMOTE/manifest-$stamp.json" "$WORK/manifest.json" 2>/dev/null || true
[ -s "$WORK/manifest.json" ] || fail "no manifest for $stamp"
[ "$(jq -r '[.files[].name] | join(" ")' "$WORK/manifest.json")" = "$db $documents" ] \
  || fail "the manifest of $stamp names other files than $db and $documents"
for index in 0 1; do
  name=$(jq -r ".files[$index].name" "$WORK/manifest.json")
  rclone copyto "$BACKUP_REMOTE/$name" "$WORK/$name" || fail "cannot download $name"
  expected=$(jq -r ".files[$index].sha256" "$WORK/manifest.json")
  actual=$(sha256sum "$WORK/$name" | cut -d ' ' -f 1)
  [ "$expected" = "$actual" ] || fail "$name does not match the manifest (changed or damaged file)"
  age -d -i "$KEY" -o /dev/null "$WORK/$name" 2>/dev/null || fail "cannot decrypt: check the key file"
done
echo "restore: hashes and key checked"

{
  echo "drop schema public cascade; create schema public;"
  age -d -i "$KEY" "$WORK/$db" | pg_restore --no-owner -f -
} | psql -q -v ON_ERROR_STOP=1 --single-transaction -o /dev/null \
  || fail "database restore failed; the database is unchanged"
rm -f "$WORK/$db"

mkdir "$WORK/objects"
age -d -i "$KEY" "$WORK/$documents" | tar -C "$WORK/objects" -xf - || fail "cannot unpack the document archive"
rm -f "$WORK/$documents"
mcli mirror --quiet --overwrite --remove "$WORK/objects" "store/$BUCKET" >/dev/null \
  || fail "writing $BUCKET failed; the database is already restored, run restore.sh again"
rm -rf "$WORK/objects"

printf 'restore: %-8s %8s %8s\n' "" restored manifest
printf 'restore: %-8s %8s %8s\n' users "$(count users)" "$(jq .users "$WORK/manifest.json")"
printf 'restore: %-8s %8s %8s\n' awards "$(count awards)" "$(jq .awards "$WORK/manifest.json")"
printf 'restore: %-8s %8s %8s\n' objects "$(object_count)" "$(jq .objects "$WORK/manifest.json")"
echo "restore: erasure requests due now (carried out by the catch-up after app starts):"
psql -tAq -F ' ' -c "select r.user_id, u.email_address, r.scheduled_for from erasure_requests r \
  join users u using (user_id) where r.status = 'PENDING' and r.scheduled_for <= now() order by r.scheduled_for" \
  | sed 's/^/  /'
echo "restore: $stamp restored"
