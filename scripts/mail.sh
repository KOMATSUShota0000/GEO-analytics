#!/usr/bin/env bash
# GEO-analytics 用の開発メール受信箱 Mailpit コンテナの管理
# 使い方: bash scripts/mail.sh {up|down|logs|status}
#   アプリが送ったメールを外に出さずに受け止め、ブラウザで読める（受信箱: http://localhost:8025）
#   .env に MAIL_HOST 等が無ければ、開発プロファイルはここ（localhost:1025）へ送る
set -euo pipefail

NAME=geo-mailpit
IMAGE=axllent/mailpit:v1.31.2
SMTP_PORT=1025
WEB_PORT=8025

case "${1:-up}" in
  up)
    if docker ps -a --format '{{.Names}}' | grep -qx "$NAME"; then
      docker start "$NAME" >/dev/null && echo "起動: $NAME"
    else
      docker run -d --name "$NAME" --restart unless-stopped \
        -e TZ=Asia/Tokyo \
        -p 127.0.0.1:${SMTP_PORT}:1025 \
        -p 127.0.0.1:${WEB_PORT}:8025 \
        "$IMAGE" >/dev/null
      echo "作成・起動: $NAME"
    fi
    echo "受信箱: http://localhost:${WEB_PORT}（SMTP: localhost:${SMTP_PORT}）"
    ;;
  down)   docker stop "$NAME" ;;
  logs)   docker logs -f "$NAME" ;;
  status) docker ps -a --filter "name=$NAME" ;;
  *) echo "使い方: $0 {up|down|logs|status}"; exit 1 ;;
esac
