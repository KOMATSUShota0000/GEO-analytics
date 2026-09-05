#!/usr/bin/env bash
# GEO-analytics 用 PostgreSQL 17 コンテナの管理
# 使い方: bash ~/geo-analytics-db.sh {up|down|psql|logs|status}
#   ※ .env の FLYWAY_PASSWORD と POSTGRES_PASSWORD を一致させること
set -euo pipefail

NAME=geo-postgres
NET=geo-net
IMAGE=postgres:17-alpine
DB=geo_analytics
PORT=5432
ENV_FILE="${ENV_FILE:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)/.env}"
# Why: FLYWAY_PASSWORD はコンテナの postgres スーパーユーザーパスワードと一致させる必要がある
[ -f "$ENV_FILE" ] && set -a && . "$ENV_FILE" && set +a
PGPASS="${FLYWAY_PASSWORD:-postgres}"

case "${1:-up}" in
  up)
    if docker ps -a --format '{{.Names}}' | grep -qx "$NAME"; then
      docker start "$NAME" && echo "起動: $NAME"
    else
      docker network inspect "$NET" >/dev/null 2>&1 || docker network create "$NET"
      docker run -d --name "$NAME" --network "$NET" --restart unless-stopped \
        -e POSTGRES_PASSWORD="$PGPASS" \
        -e POSTGRES_DB="$DB" \
        -e TZ=Asia/Tokyo \
        -p 127.0.0.1:${PORT}:5432 \
        -v geo-pgdata:/var/lib/postgresql/data \
        "$IMAGE"
      echo "作成・起動: $NAME (db=$DB, port=$PORT, user=postgres, password=.env の FLYWAY_PASSWORD)"
    fi
    ;;
  down)   docker stop "$NAME" ;;
  psql)   docker exec -it "$NAME" psql -U postgres -d "$DB" ;;
  logs)   docker logs -f "$NAME" ;;
  status) docker ps -a --filter "name=$NAME" ;;
  *) echo "使い方: $0 {up|down|psql|logs|status}"; exit 1 ;;
esac
