# ADR-022: Stripe セルフサーブ自動決済（案B・Checkout＋Webhook）

## 日付
2026-06-04（決定は 2026-06-03・公開LP/料金確定と同時）

## 状況
公開マーケLPからのプラン購入を、人手の問い合わせ（案A）ではなく **Stripe によるセルフサーブ自動決済（案B）** で受ける（オーナー承認・「じっくり実装」）。料金は Standard ¥9,800 / Pro ¥29,800 / Expert ¥59,800（月額・税抜・月額のみ）。

## 決定

### 統合方式
- **Stripe Checkout（リダイレクト方式）** を採用。サーバーで Checkout Session を生成しStripeホストのページへリダイレクトすることでPCI範囲を最小化。`埋め込みコンポーネント`（Elements）は作り込み過多のため不採用、`Payment Links` はログインユーザーとの紐付けが弱いため不採用。
- 決済管理は **self-handle**（Managed Payments の +3.5% は限界利益率86%を圧迫し、国内B2BではグローバルVAT肩代わりの便益が薄いため）。

### テナント解決とRLS（核心）
- Webhook はユーザーセッション外のため、Checkout 作成時に **session と subscription の双方の metadata に `workspaceId`・`plan` を埋め込む**。
- Webhook 受信時は metadata の `workspaceId` を取り出し、`TenantPlanScope.executeWithTenant(workspaceId, …)` でテナント文脈を確立。RLS インターセプタが workspaceId から `organization_id` を解決し `app.current_org_id` を設定するため、**テナント隔離（RLS）を破らない**。
- 既存の cross-tenant ルックアップ（GlobalAccess での subscription_id 逆引き）は使わず、metadata 経由でテナントを確定する設計とした（RLS 緩和の必要がなく安全）。

### 冪等性
- `processed_stripe_events`（event_id ユニーク・RLS 適用）を冪等台帳とし、重複配信時の二重プラン変更を防止。

### セキュリティ
- Webhook は `/api/public/billing/webhook` に配置。`/api/public/**` は既存設定で認証・CSRF・JWT・テナントヘッダがすべて免除されるため、セキュリティ設定を改変せず実装。正当性は **Stripe 署名検証（`Webhook.constructEvent`）** で担保し、検証失敗は 400。
- 秘密情報（`STRIPE_SECRET_KEY`・`STRIPE_WEBHOOK_SECRET`）は環境変数注入（application.yml にハードコード禁止）。価格IDは非機密のため既定値を持たせる。

### 扱うイベント
- `checkout.session.completed` → プラン昇格（主経路）
- `customer.subscription.updated` → 現在の価格からプランを再判定し反映
- `customer.subscription.deleted` → STANDARD へダウングレード

## 影響
- 新規: `V130__stripe_billing.sql`（workspaces に stripe_customer_id/stripe_subscription_id、processed_stripe_events）。
- 新規Javaパッケージ `application.billing`（Catalog/Checkout/Webhook/Sync）＋ 設定 `StripeProperties` ＋ コントローラ2本＋DTO。
- フロント: 公開 `/plans`・`/demo`（フェーズ1）＋ 認証内 `PricingPage` のCTAを Checkout 起動に結線。

## 残課題
- 実鍵（テストモード）での E2E 検証はオーナーの環境変数設定（`STRIPE_SECRET_KEY`・`STRIPE_WEBHOOK_SECRET`）＋ `stripe listen` 後に実施。
- Billing Portal（解約・支払い方法変更のセルフサービス）は次イテレーション。
- Standard 付与チケットの PRD(50) vs 実装(10) 不整合は別途整合が必要（本ADRのスコープ外）。
