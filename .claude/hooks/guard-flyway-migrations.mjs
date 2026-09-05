#!/usr/bin/env node
// Why: 適用済み Flyway マイグレーションを編集するとチェックサムが壊れ、アプリが起動不能になる
//      （ADR 2026-08-30 が「不可逆な事故カテゴリ」と分類）。ところが permissions.deny の
//      グロブでは「既存ファイル」と「新規ファイル」を区別できず、ディレクトリ全体を塞ぐと
//      同 ADR が明示的に許可した新規マイグレーション追加まで巻き添えで止まる。
//      存在チェックを挟むことで「既存は編集禁止／新規は許可」という本来の設計を再現する。
import { existsSync, readFileSync } from "node:fs";

const MIGRATION_DIR = "src/main/resources/db/migration/";
const ALLOW = 0;

function allow() {
  process.exit(ALLOW);
}

let payload;
try {
  payload = JSON.parse(readFileSync(0, "utf8"));
} catch (err) {
  // Why: ペイロード解析の失敗はマイグレーションとは無関係の基盤障害。ここで拒否すると
  //      リポジトリ全体の Write/Edit が止まり被害が過大になるため、警告のみでフェイルオープンする。
  process.stderr.write(`guard-flyway-migrations: ペイロードを解析できませんでした: ${err.message}\n`);
  allow();
}

const filePath = payload?.tool_input?.file_path ?? "";
if (typeof filePath !== "string" || filePath === "") {
  allow();
}

// Why: Windows 由来のパス区切りでも同一判定にするため正規化してから照合する。
const normalized = filePath.replace(/\\/g, "/");
if (!normalized.includes(MIGRATION_DIR) || !existsSync(filePath)) {
  allow();
}

process.stdout.write(
  JSON.stringify({
    hookSpecificOutput: {
      hookEventName: "PreToolUse",
      permissionDecision: "deny",
      permissionDecisionReason:
        `適用済み Flyway マイグレーションは編集禁止です（チェックサム破壊でアプリが起動不能になる）:\n` +
        `  ${normalized}\n` +
        `スキーマ変更は新しい V{次番号}__*.sql を追加してください（新規ファイルの作成は許可されています）。`,
    },
  }),
);
