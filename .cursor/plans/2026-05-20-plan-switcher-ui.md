# 仕様書: 管理者向け簡易プラン切替UI（Sprint10.5）

- **作成**: CPO室 / 2026-05-20
- **対象**: Sprint10.5（Sprint11 の素材DL検証前提として先行）
- **関連**: Sprint10（白ラベルロゴ Pro+ ゲート）／前回検証「サブスク変更UI 真に未完成」

## 1. 背景・目的（What / Why）

- バックエンド `PATCH /api/v1/workspaces/{workspaceId}/subscription` と `SubscriptionManagementService.changePlan()` は実装済み
- ところがフロントエンドに呼び出し UI が存在せず、Pro 限定機能（白ラベルロゴ・競合シェアグラフ・素材DL等）の検証は **curl 直叩きか DB 直 UPDATE 一択**
- Sprint11 の素材DL検証で都度 curl は非生産的＝**最小限のプラン切替プルダウン**を追加する
- 課金フロー（Stripe / 請求書発行）は意図的にスコープ外。**限界利益率86%を維持**するため、課金連動の重い実装は将来 Sprint へ

## 2. 設計（How）

### 2.1 設置場所
`PricingPage`（`/pricing`）に「現在のプラン」セクションを追加。
既存のプラン比較カード3枚の **上部** に、現在プラン表示＋切替プルダウン＋「切替」ボタンの薄いバナーを置く。

### 2.2 UI 仕様
- 「現在のプラン」 ラベル＋現在プランバッジ（`fetchWorkspacePlan()` から取得）
- `<select>` で STANDARD / PRO / EXPERT を選択
- 「切替」ボタン押下 → `PATCH /api/v1/workspaces/{DEFAULT_WORKSPACE_TENANT_ID}/subscription` を呼ぶ
- 成功時：トースト不要。`fetchWorkspacePlan()` を再取得して表示更新
- 失敗時：赤字メッセージ表示
- 注意書き：「開発・検証用です。本番では Stripe 連携予定」

### 2.3 API クライアント
`workspace-api.ts` に `changeWorkspacePlan(plan)` を追加：
```ts
export async function changeWorkspacePlan(plan: WorkspaceSubscriptionPlan): Promise<boolean>
```
- `PATCH` + `Content-Type: application/json` + body `{"plan": "PRO"}`
- 既存の `apiFetch` を再利用（認証ヘッダ自動付与）

### 2.4 ついで修正（必須）
`PricingPage` の `PLANS` 定数で `STANDARD.whiteLabel = true` となっているが、Sprint10 で
ロゴは Pro+ 限定になった。**整合性のため `STANDARD.whiteLabel = false` に修正**。
（カラー・ツール名カスタマイズは引き続き全プランで可。"whiteLabel" の意味を「ロゴ表示」に揃える）

## 3. 非機能・退行防止

- バックエンドAPI・DBスキーマ不変（既存エンドポイントを使うだけ）
- `WorkspaceBrandingProvider` のキャッシュは現状維持。プラン切替後にロゴが消えるまで最大1リロードかかってよい（dev向けのため厳密リアルタイム不要）
- 既存ナビゲーション・他ページのレイアウトに影響なし

## 4. 受け入れ基準（オーディター監査項目）

1. `cd frontend && npm run build` が成功すること（型エラー・lintエラーなし）
2. `PricingPage` 上部にプラン切替セクションが描画されること（コードレベルで確認）
3. `changeWorkspacePlan()` API クライアント関数が `PATCH` メソッドで呼び出すこと
4. `STANDARD.whiteLabel` が `false` に修正されていること
5. ADR 不要（既存パターンの UI 化のみ・アーキ判断なし）
6. バックエンド側のテスト退行なし（無関係のため対象外）

## 5. スコープ外

- Stripe 連携・請求書発行・チケット消費連動
- 権限制御（現状 ADMIN 前提。将来 Sprint で RBAC 化）
- 切替履歴の表示
- トースト通知ライブラリ導入
