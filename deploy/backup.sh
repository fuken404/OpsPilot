#!/bin/sh
# Copia diaria de las bases y los adjuntos. Guarda 7 días en el servidor y, si hay credenciales,
# sube cada copia a Oracle Object Storage (API compatible con S3) para sobrevivir a la máquina.
set -u
apk add --no-cache rclone >/dev/null 2>&1 || echo "Sin rclone: las copias solo quedan en el servidor"
sleep 600
while true; do
  stamp=$(date -u +%Y%m%d-%H%M)
  pg_dumpall -h postgres -U opspilot | gzip > "/backups/bases-$stamp.sql.gz" \
    && tar -czf "/backups/adjuntos-$stamp.tar.gz" -C /attachments . \
    && echo "Copia $stamp lista"
  find /backups -type f -mtime +7 -delete
  if [ -n "$S3_BUCKET" ] && command -v rclone >/dev/null; then
    rclone copy /backups ":s3,provider=Other,endpoint=$S3_ENDPOINT,access_key_id=$S3_ACCESS_KEY,secret_access_key=$S3_SECRET_KEY:$S3_BUCKET" \
      --max-age 25h && echo "Copia $stamp subida"
  fi
  sleep 86400
done
