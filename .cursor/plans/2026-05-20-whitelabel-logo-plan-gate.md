# 仕様書: 白ラベルロゴ Pro+ ゲート（発生源）

- **作成**: CPO室 / 2026-05-20
- **対象スプリント**: Sprint10（dev-phase-log フェーズ10）
- **関連**: 素材DL要件 B-4／オーナー指示「ホワイトロゴはPro以上、Standard不可」

## 1. 背景・目的（What / Why）

`BrandingService.getBranding()` は `LOGO_URL` を**無条件返却**、`loadLogoResource()` も
プラン判定なし。白ラベルロゴの Pro+ 制限は**現状どこにも未実装**（既存ギャップ）。
発生源でゲートすれば UI・PDF・素材DL すべてが一貫してロゴ制限を継承する。

## 2. 設計（How）

### 2.1 プラン解決
`WorkspacePlanResolver.resolvePlan(workspaceId)`（既存）を再利用。
`workspaceId` は `TenantContextHolder.getTenantId()`。空時は STANDARD 扱い（安全側＝ロゴなし）。
判定: `plan.usesProTierFeatures()`（PRO/EXPERT のみ true）。

### 2.2 `BrandingService.getBranding()`
- Pro+ → 従来どおり `WorkspaceBrandingResponse(toolName, brandColor, LOGO_URL)`
- Standard → `logoUrl` を空文字 `""` で返す（toolName・brandColor は維持）
  - フロント `WorkspaceBrandingProvider` は `logoUrl` 空なら logo を取得しない既存挙動 → 自動で非表示

### 2.3 `loadLogoResource()`（多層防御）
- 非 Pro はロゴURLを直接叩いても取得不可にする。非 Pro かつロゴ要求 →
  `FileNotFoundException("logo")`（既存の 404 ハンドリング経路に乗る）
- ゲートを `getBranding()` だけに置かない（URL直アクセス回避）

### 2.4 共通ヘルパー
`private boolean logoEntitled()`：tenantId 解決 → plan 解決 → `usesProTierFeatures()`。
両メソッドで使用。DI に `WorkspacePlanResolver` を追加。

## 3. 非機能・退行防止

- toolName / brandColor は全プラン不変（白ラベルの色・名称は維持、ロゴのみ Pro+）
- 既存テナント・RLS・パストラバーサル防御（`validateAndResolveRelativeLogoPath`）は不変
- `WorkspaceBrandingResponse` のスキーマ不変（`logoUrl` を空文字にするだけ。JSON契約不変）

## 4. 受け入れ基準（オーディター監査項目）

1. `./mvnw clean test` 全件 PASS（既存退行なし）
2. `BrandingServicePlanGateTest`：
   - STANDARD → `getBranding().logoUrl()` が空、`loadLogoResource()` が FileNotFoundException
   - PRO / EXPERT → `logoUrl` が従来値、`loadLogoResource()` は従来どおり解決
   - tenantId 不在 → STANDARD 扱い（ロゴなし）
3. toolName・brandColor は全プランで不変であることをテストで確認
4. アーキ規約遵守（@Transactional(readOnly=true) 維持・ThreadLocal/synchronized/parallel 不使用・テナント解決は TenantContextHolder/WorkspacePlanResolver 経由）
5. ADR 作成（発生源ゲート採用と band-aid を選ばない理由）

## 5. スコープ外

- 素材DL機能本体（Sprint11）。本スプリントは「ロゴ提供のPro+制限」の発生源実装のみ
- ロゴアップロードUI側のプラン制限（アップロード自体は別経路・別チケット）
