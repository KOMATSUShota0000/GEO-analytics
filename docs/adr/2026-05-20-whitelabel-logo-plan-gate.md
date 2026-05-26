# ADR-007: 白ラベルロゴ Pro+ ゲートを発生源（BrandingService）で実装

## 日付

2026-05-20

## 状況

オーナー指示「ホワイトロゴは Pro 以上、Standard では使えない」を実装するに当たり、コード調査の結果、白ラベルロゴのプラン制限は **どこにも実装されていなかった**（既存ギャップ）。

- `BrandingService.getBranding()` は `LOGO_URL` を **無条件返却**
- `BrandingService.loadLogoResource()` も **プラン判定なし**
- フロント `WorkspaceBrandingProvider`・PDF 出力・素材DL（Sprint11）はすべて
  これらの API を経由するため、UI 側に band-aid を貼っても URL 直叩きで突破される

## 決定

**発生源（`BrandingService`）でプランゲートを実装する。**

- `getBranding()`：非 Pro 時は `logoUrl` を空文字 `""` で返す（toolName・brandColor は維持）
- `loadLogoResource()`：非 Pro 時は `FileNotFoundException("logo")` を投げる（多層防御。`getBranding()` 側のゲートを迂回する URL 直アクセスを遮断）
- プラン解決は既存 `WorkspacePlanResolver.resolvePlan(tenantId)` を再利用、`SubscriptionPlan.usesProTierFeatures()` で PRO/EXPERT のみ true
- `TenantContextHolder.getTenantId()` 不在時は STANDARD 扱い（安全側＝ロゴなし）

## 理由

- **発生源ゲートはアーキ的に一貫**：UI／PDF／素材DL（Sprint11）すべてが「ロゴURL空＝表示しない」既存挙動を自動継承し、新しい呼び出し経路が増えても band-aid を貼り増す必要がない
- **JSON契約・DBスキーマ不変**：`WorkspaceBrandingResponse` のフィールド構成は変えず、`logoUrl` を空文字にするだけでフロントは追加実装ゼロで対応
- **band-aid 案（UI 側で `plan === 'STANDARD'` 判定）を却下した理由**：
  - URL を直接叩けば取得可能（セキュリティ的に破綻）
  - PDF・素材DL ごとに同じ判定を実装する必要があり、退行ポイントが増える
  - 「画面ではロゴ消えるが PDF には残る」ような不整合が必ず生じる

## 結果

- `BrandingService` に `WorkspacePlanResolver` を DI、`logoEntitled()` 共通ヘルパーで判定一元化
- 既存 `validateAndResolveRelativeLogoPath` のパストラバーサル防御は不変
- `BrandingServicePlanGateTest`（7ケース）追加：STANDARD/PRO/EXPERT/テナント不在 × `getBranding`/`loadLogoResource` をカバー
- 全 152 ユニットテスト PASS、退行ゼロ確認済み

## スコープ外

- ロゴアップロードUI 側のプラン制限（アップロードは別経路・別チケット）
- 素材DL機能本体（Sprint11 で実装。本ゲートを前提とする）
