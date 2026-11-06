#!/usr/bin/env bash
# Lo ejecuta GitHub Actions en el servidor. También sirve a mano: ./deploy.sh [etiqueta]
set -euo pipefail
cd "$(dirname "$0")"
set -a; . ./.env; set +a
export TAG="${1:-latest}"
envsubst '${APP_URL}' < realm.template.json > realm.json
docker compose -f compose.prod.yaml pull --quiet
docker compose -f compose.prod.yaml up -d --remove-orphans
docker image prune -f > /dev/null
echo "Desplegada la versión $TAG"
