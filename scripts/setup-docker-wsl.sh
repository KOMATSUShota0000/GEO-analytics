#!/usr/bin/env bash
# GEO-analytics: WSL2(Ubuntu) に Docker Engine を導入する
#
# Why: 統合テスト（PostgresTestBase / PostgresSuperuserTestBase の派生 18 件）は
#      Testcontainers 経由で PostgreSQL 17 を起動するため Docker が必須。
#      Docker Desktop ではなくネイティブの Docker Engine を使う理由は、
#      WSL2 で systemd が有効なため追加のブリッジが不要で、
#      Docker Desktop の商用ライセンス条件も回避できるため。
#
# 使い方: sudo bash scripts/setup-docker-wsl.sh
set -euo pipefail

if [ "$(id -u)" -ne 0 ]; then
  echo "エラー: sudo で実行してください → sudo bash $0" >&2
  exit 1
fi

TARGET_USER="${SUDO_USER:-$(logname 2>/dev/null || echo root)}"

echo "==> 1/4 docker.io を導入"
apt-get update -qq
DEBIAN_FRONTEND=noninteractive apt-get install -y -qq docker.io

echo "==> 2/4 docker サービスを有効化"
systemctl enable --now docker

echo "==> 3/4 ${TARGET_USER} を docker グループへ追加"
usermod -aG docker "$TARGET_USER"

echo "==> 4/4 動作確認"
docker version --format '  Server: {{.Server.Version}}' || true

cat <<MSG

完了しました。

【重要】グループ変更を反映するため、WSL を一度終了してください。
  Windows 側の PowerShell で:  wsl --shutdown
  そのあと WSL を開き直す

反映後の確認:
  docker run --rm hello-world
  bash ~/geo-analytics-db.sh up     # 開発用 PostgreSQL 17 を起動
  ./mvnw clean test                 # 230 件すべて通るはず
MSG
