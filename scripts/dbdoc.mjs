#!/usr/bin/env node
// Why: tbls をコンテナで動かし、DB コンテナと同じ Docker ネットワークに載せる。
//      こうすればホスト側のポート公開方法（127.0.0.1 バインド等）に依存せず、
//      コンテナ名の名前解決だけで到達できる。
import { spawnSync } from "node:child_process";
import { existsSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const projectRoot = join(dirname(fileURLToPath(import.meta.url)), "..");
const IMAGE = "ghcr.io/k1low/tbls";
const NETWORK = "geo-net";
const DB_HOST = "geo-postgres";

const envPath = join(projectRoot, ".env");
if (!existsSync(envPath)) {
  console.error(".env が見つかりません。.env.example からコピーして値を埋めてください。");
  process.exit(1);
}
const password = readFileSync(envPath, "utf8")
  .split("\n")
  .map((line) => line.match(/^FLYWAY_PASSWORD=(.*)$/))
  .find(Boolean)?.[1]
  ?.trim();
if (!password) {
  console.error(".env の FLYWAY_PASSWORD が未設定です。");
  process.exit(1);
}

// Why: コンテナが root で書くと生成物が root 所有になり、ホスト側で編集も削除もできなくなる。
const asUser = process.platform === "linux" ? ["--user", `${process.getuid()}:${process.getgid()}`] : [];

const result = spawnSync(
  "docker",
  [
    "run", "--rm", "--network", NETWORK, ...asUser,
    "-v", `${projectRoot}:/work`, "-w", "/work",
    "-e", `FLYWAY_PASSWORD=${password}`,
    "-e", `GEO_DB_HOST=${DB_HOST}`,
    IMAGE, ...(process.argv.length > 2 ? process.argv.slice(2) : ["doc", "--force"]),
  ],
  { stdio: "inherit" });

if (result.error) {
  console.error(`tbls を起動できません: ${result.error.message}`);
  process.exit(1);
}
process.exit(result.status ?? 1);
