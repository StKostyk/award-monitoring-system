#!/bin/sh
# Creates the encrypted award-documents bucket and the backend's MinIO account, limited to the objects of that
# bucket (no bucket settings, policies or admin rights).
set -eu
export MC_CONFIG_DIR=/tmp/mc

mc alias set local http://minio:9000 "$MINIO_ROOT_USER" "$MINIO_ROOT_PASSWORD" >/dev/null
mc mb --ignore-existing local/award-documents >/dev/null
mc encrypt set sse-s3 local/award-documents >/dev/null
mc admin policy create local award-documents /init/award-documents-policy.json >/dev/null
mc admin user add local "$MINIO_APP_USER" "$MINIO_APP_PASSWORD" >/dev/null
case "$(mc admin user info local "$MINIO_APP_USER")" in
  *award-documents*) ;;
  *) mc admin policy attach local award-documents --user "$MINIO_APP_USER" >/dev/null ;;
esac
echo "MinIO account $MINIO_APP_USER ready"
