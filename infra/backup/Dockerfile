# Nightly encrypted backups of the database and the document objects (see docs/deployment/DEMO_DEPLOYMENT.md §4)
FROM postgres:17-alpine

RUN apk add --no-cache age rclone minio-client jq tzdata

ENV TZ=Europe/Kyiv \
    BACKUP_CRON="30 4 * * *" \
    BACKUP_KEEP_DAYS=14 \
    BACKUP_DOCUMENTS_BUCKET=award-documents \
    BACKUP_STATE_DIR=/var/lib/backup \
    RCLONE_S3_NO_CHECK_BUCKET=true \
    RCLONE_CONFIG=/dev/null

COPY --chmod=755 backup.sh restore.sh healthcheck.sh common.sh /usr/local/bin/

HEALTHCHECK --interval=5m --timeout=10s --start-period=1m CMD ["healthcheck.sh"]

ENTRYPOINT []
CMD ["backup.sh"]
