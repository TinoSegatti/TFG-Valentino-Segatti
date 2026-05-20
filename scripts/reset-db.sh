#!/usr/bin/env bash
set -euo pipefail
echo "⚠️  Destructivo: reinicia volúmenes Docker y migraciones."
docker compose down -v
docker compose up -d postgres redis
sleep 5
docker compose up -d api-domain
sleep 15
bash "$(dirname "$0")/seed.sh"
