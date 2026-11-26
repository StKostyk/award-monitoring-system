#!/bin/sh
# Script test of the backup image and the production override (Feature 9.2 test plan, AC-2.2 to AC-2.5, AC-3.4,
# AC-3.5). Needs the development postgres and minio containers (docker compose up -d postgres minio); uses its own
# databases and buckets and removes them.
set -eu
cd "$(dirname "$0")"
export MSYS_NO_PATHCONV=1

IMAGE=award-backup:test
NET=$(docker inspect -f '{{range $name, $_ := .NetworkSettings.Networks}}{{$name}}{{end}}' award-postgres)
SOURCE_DB=award_backup_test_source
TARGET_DB=award_backup_test_target
SOURCE_BUCKET=award-backup-test-source
REMOTE_BUCKET=award-backup-test-remote
PG_USER=${POSTGRES_USER:-postgres}
PG_PASSWORD=${POSTGRES_PASSWORD:-popqaz}
MINIO_USER=${MINIO_ROOT_USER:-minioadmin}
MINIO_PASSWORD=${MINIO_ROOT_PASSWORD:-minioadmin}
passed=0

ok() {
  passed=$((passed + 1))
  echo "ok   $1"
}

no() {
  echo "FAIL $1"
  exit 1
}

sql() {
  docker exec -i award-postgres psql -U "$PG_USER" -tAq "$@"
}

store() {
  docker run --rm --network "$NET" -e MC_CONFIG_DIR=/tmp/mc "$IMAGE" sh -c \
    "mcli alias set s http://award-minio:9000 '$MINIO_USER' '$MINIO_PASSWORD' >/dev/null && $1"
}

# in_backup <database> <key> <command> [docker run options]
in_backup() {
  database=$1 key=$2 command=$3
  shift 3
  docker run --rm --network "$NET" \
    -e PGHOST=award-postgres -e PGDATABASE="$database" -e PGUSER="$PG_USER" -e PGPASSWORD="$PG_PASSWORD" \
    -e MINIO_ENDPOINT=http://award-minio:9000 -e MINIO_ACCESS_KEY="$MINIO_USER" -e MINIO_SECRET_KEY="$MINIO_PASSWORD" \
    -e BACKUP_DOCUMENTS_BUCKET="$SOURCE_BUCKET" -e BACKUP_REMOTE="s3:$REMOTE_BUCKET" \
    -e BACKUP_AGE_RECIPIENT="$RECIPIENT" -e BACKUP_IMAGE_TAG=test -e KEY="$key" \
    -e RCLONE_CONFIG_S3_TYPE=s3 -e RCLONE_CONFIG_S3_PROVIDER=Minio \
    -e RCLONE_CONFIG_S3_ENDPOINT=http://award-minio:9000 \
    -e RCLONE_CONFIG_S3_ACCESS_KEY_ID="$MINIO_USER" -e RCLONE_CONFIG_S3_SECRET_ACCESS_KEY="$MINIO_PASSWORD" \
    "$@" "$IMAGE" sh -c "printf '%s\n' \"\$KEY\" > /run/backup.key && $command"
}

latest() {
  store "mcli ls s/$REMOTE_BUCKET" | sed -n 's/.*manifest-\([0-9T]*\)\.json$/\1/p' | sort | tail -1
}

clean() {
  sql -c "drop database if exists $SOURCE_DB" -c "drop database if exists $TARGET_DB" >/dev/null 2>&1 || true
  store "mcli rb --force s/$SOURCE_BUCKET s/$REMOTE_BUCKET" >/dev/null 2>&1 || true
}

docker build -q -t "$IMAGE" . >/dev/null
KEY=$(docker run --rm "$IMAGE" age-keygen 2>/dev/null)
OTHER_KEY=$(docker run --rm "$IMAGE" age-keygen 2>/dev/null)
RECIPIENT=$(echo "$KEY" | sed -n 's/^# public key: //p')

# AC-3.4, AC-3.5: every service of the production override rotates its logs and has a memory limit; the limits
# leave at least 1.5 GB of an 8 GB host free
config=$(cd ../.. && env $(sed -n 's/.*\${\([A-Z0-9_]*\):?.*/\1=x/p' docker-compose.prod.yml | sort -u) \
  docker compose -f docker-compose.yaml -f docker-compose.prod.yml --profile local-mail \
  config --format json) || no "AC-3.4 production configuration"
echo "$config" | docker run --rm -i "$IMAGE" jq -e '[.services[] | .logging.driver == "json-file"
  and .logging.options["max-size"] == "10m" and .logging.options["max-file"] == "5"] | all' >/dev/null \
  || no "AC-3.4 a service without log rotation"
echo "$config" | docker run --rm -i "$IMAGE" jq -e '[.services[] | .mem_limit != null] | all' >/dev/null \
  || no "AC-3.5 a service without mem_limit"
echo "$config" | docker run --rm -i "$IMAGE" jq -e \
  '[.services[] | .mem_limit | tonumber] | add <= 6.5 * 1024 * 1024 * 1024' >/dev/null \
  || no "AC-3.5 the limits add up to more than 6.5 GB"
ok "AC-3.4, AC-3.5 every production service has log rotation and a memory limit, 6.5 GB at most in all"

clean
trap clean EXIT

sql -c "create database $SOURCE_DB" -c "create database $TARGET_DB" >/dev/null
sql -d "$SOURCE_DB" >/dev/null <<'SQL'
create table users (user_id bigint primary key, email_address text not null);
create table awards (award_id bigint primary key, user_id bigint references users);
create table erasure_requests (user_id bigint references users, status text, scheduled_for timestamptz);
create table audit_logs (id bigint, logged_on date, primary key (id, logged_on)) partition by range (logged_on);
create table audit_logs_default partition of audit_logs default;
insert into audit_logs values (1, current_date);
insert into users values (1, 'kept@chnu.edu.ua'), (2, 'due@chnu.edu.ua');
insert into awards values (10, 1), (11, 1), (12, 2);
insert into erasure_requests values (2, 'PENDING', now() + interval '2 seconds');
SQL
store "mcli mb s/$SOURCE_BUCKET s/$REMOTE_BUCKET >/dev/null \
  && echo one | mcli pipe s/$SOURCE_BUCKET/documents/a.pdf >/dev/null \
  && echo two | mcli pipe s/$SOURCE_BUCKET/documents/b.pdf >/dev/null"

# AC-2.2: one run writes the three files and leaves /tmp empty
in_backup "$SOURCE_DB" "" 'backup.sh now >/dev/null && test -z "$(ls -A /tmp)"' || no "AC-2.2 backup run"
T=$(latest)
files=$(store "mcli ls s/$REMOTE_BUCKET" | grep -c -e "db-$T.dump.age" -e "documents-$T.tar.age" -e "manifest-$T.json")
[ "$files" -eq 3 ] || no "AC-2.2 three files for $T, found $files"
ok "AC-2.2 a run writes db, documents and manifest for $T and leaves /tmp empty"

in_backup "$SOURCE_DB" "" "rclone copy s3:$REMOTE_BUCKET /tmp/c --include '*$T*' && cd /tmp/c \
  && jq -r '.files[] | .sha256 + \"  \" + .name' manifest-$T.json | sha256sum -c -s \
  && test \"\$(jq '[.users, .awards, .objects] | join(\",\")' -r manifest-$T.json)\" = 2,3,2" \
  || no "AC-2.2 manifest hashes and counts"
ok "AC-2.2 the manifest holds the SHA-256 of both files and the counts 2 users, 3 awards, 2 objects"

# AC-2.4: a remote file older than BACKUP_KEEP_DAYS is removed by the next run
OLD=$(docker run --rm "$IMAGE" sh -c 'date -d "@$(( $(date +%s) - 15 * 86400 ))" +%Y%m%dT%H%M%S')
store "echo old | mcli pipe s/$REMOTE_BUCKET/db-$OLD.dump.age >/dev/null"
sleep 1
in_backup "$SOURCE_DB" "" 'backup.sh now >/dev/null' || no "AC-2.4 second run"
store "mcli ls s/$REMOTE_BUCKET" | grep -q "db-$OLD" && no "AC-2.4 file of 15 days ago still there"
ok "AC-2.4 a file of 15 days ago is deleted by the next run"

# AC-2.2: a failing upload exits non-zero and writes no manifest
manifests=$(store "mcli ls s/$REMOTE_BUCKET" | grep -c manifest-)
sleep 1
if in_backup "$SOURCE_DB" "" 'backup.sh now' -e RCLONE_CONFIG_S3_SECRET_ACCESS_KEY=wrong >/dev/null 2>&1; then
  no "AC-2.2 run with wrong credentials succeeded"
fi
[ "$(store "mcli ls s/$REMOTE_BUCKET" | grep -c manifest-)" -eq "$manifests" ] || no "AC-2.2 manifest after failure"
ok "AC-2.2 a run with a failing upload exits non-zero and uploads no manifest"

# AC-2.3: the health check follows the time of the last success
in_backup "$SOURCE_DB" "" 'mkdir -p /var/lib/backup && date +%s > /var/lib/backup/last-success && healthcheck.sh \
  && echo $(( $(date +%s) - 27 * 3600 )) > /var/lib/backup/last-success && ! healthcheck.sh \
  && rm /var/lib/backup/last-success && echo $(( $(date +%s) - 27 * 3600 )) > /var/lib/backup/started \
  && ! healthcheck.sh' >/dev/null || no "AC-2.3 health check"
ok "AC-2.3 healthy after a recent run, unhealthy 27 hours after the last run or the start"

# AC-2.5: refusals before anything is written
out=$(in_backup "$TARGET_DB" "$OTHER_KEY" "restore.sh $T" 2>&1) && no "AC-2.5 wrong key accepted"
echo "$out" | grep -q "cannot decrypt: check the key file" || no "AC-2.5 wrong key message: $out"
[ "$(sql -d "$TARGET_DB" -c "select count(*) from pg_tables where schemaname = 'public'")" -eq 0 ] \
  || no "AC-2.5 wrong key wrote tables"
ok "AC-2.5 a wrong key is refused with 'cannot decrypt: check the key file' and nothing is written"

out=$(in_backup "$TARGET_DB" "$KEY" "restore.sh 19990101T000000" 2>&1) && no "AC-2.5 missing time accepted"
echo "$out" | grep -q "no manifest" || no "AC-2.5 missing manifest message: $out"
ok "AC-2.5 a time without a manifest is refused"

out=$(in_backup "$TARGET_DB" "$KEY" "restore.sh ../../etc/crontabs/root" 2>&1) && no "AC-2.5 path as time accepted"
echo "$out" | grep -q "usage: restore.sh" || no "AC-2.5 path as time message: $out"
ok "AC-2.5 a time that is not YYYYMMDDTHHMMSS is refused"

docker exec -d award-postgres sh -c \
  "PGAPPNAME='PostgreSQL JDBC Driver' psql -U $PG_USER -d $TARGET_DB -c 'select pg_sleep(15)'"
sleep 2
out=$(in_backup "$TARGET_DB" "$KEY" "restore.sh $T" 2>&1) && no "AC-2.5 restore next to a running app"
echo "$out" | grep -q "app is running" || no "AC-2.5 running app message: $out"
ok "AC-2.5 a restore while app holds database sessions is refused"
sleep 14

# AC-2.5: restore into an empty database; an object added after the backup is removed
store "echo late | mcli pipe s/$SOURCE_BUCKET/documents/late.pdf >/dev/null"
out=$(in_backup "$TARGET_DB" "$KEY" "restore.sh $T" 2>&1) || no "AC-2.5 restore: $out"
[ "$(sql -d "$TARGET_DB" -c 'select count(*) from awards')" -eq 3 ] || no "AC-2.5 restored awards"
store "mcli ls --recursive s/$SOURCE_BUCKET" | grep -q late.pdf && no "AC-2.5 late object kept"
echo "$out" | grep -q "due@chnu.edu.ua" || no "AC-2.5 due erasure not listed: $out"
ok "AC-2.5 a restore into an empty database brings back the rows and objects, removes later objects, lists due erasures"

out=$(in_backup "$TARGET_DB" "$KEY" "restore.sh $T" 2>&1) || no "AC-2.5 second restore: $out"
[ "$(sql -d "$TARGET_DB" -c 'select count(*) from users')" -eq 2 ] || no "AC-2.5 second restore duplicated rows"
ok "AC-2.5 a second restore of the same time gives the same counts"

store "echo changed | mcli pipe s/$REMOTE_BUCKET/db-$T.dump.age >/dev/null"
out=$(in_backup "$TARGET_DB" "$KEY" "restore.sh $T" 2>&1) && no "AC-2.5 changed file accepted"
echo "$out" | grep -q "does not match the manifest" || no "AC-2.5 changed file message: $out"
ok "AC-2.5 a changed file is refused before anything is written"

echo "backup test: $passed passed"
