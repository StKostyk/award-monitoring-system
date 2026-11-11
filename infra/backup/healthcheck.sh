#!/bin/sh
# Unhealthy when the last successful backup is older than 26 hours, or none succeeded in the 26 hours since start.
STATE="${BACKUP_STATE_DIR:-/var/lib/backup}"
LIMIT=$((26 * 3600))
now=$(date +%s)

if [ -f "$STATE/last-success" ]; then
  age=$((now - $(cat "$STATE/last-success")))
  [ "$age" -le "$LIMIT" ] || { echo "last backup ${age}s ago"; exit 1; }
  echo "last backup ${age}s ago"
  exit 0
fi
started=$(cat "$STATE/started" 2>/dev/null || echo "$now")
[ $((now - started)) -le "$LIMIT" ] || { echo "no backup since the start"; exit 1; }
echo "no backup yet"
