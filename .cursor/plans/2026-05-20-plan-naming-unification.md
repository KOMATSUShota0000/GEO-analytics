# 仕様書: Plan 命名統一（PricingPlan → RateLimitPlan）

- **作成**: CPO室 / 2026-05-20
- **対象スプリント**: Sprint7（dev-phase-log フェーズ7）
- **関連課題**: dev-status.md 懸念点#8「SubscriptionPlan と PricingPlan の2系統・命名が紛らわしい」

## 1. 背景・スコープ判断（What / Why）

懸念の本質は「レート制限専用なのに `PricingPlan`（＝価格表/課金を連想）」という誤名。

| 対象 | 出現 | 判断 |
|------|------|------|
| `SubscriptionPlan` | 193箇所/54ファイル（JSON契約・フロント・DB永続化・AIプロンプト） | **現状維持**。`Subscription`=課金で意味は既に正しい。54ファイル churn は退行リスクのみでMVP価値ゼロ |
| `PricingPlan` | 7箇所/3ファイル（レート制限のみ・非永続・非JSON） | **`RateLimitPlan` へリネーム**。混乱の元凶を最小リスクで解消 |

→ `PricingPlan` のみ改名すれば「課金=SubscriptionPlan / レート制限=RateLimitPlan」と用途が名前で自明になり、混乱は完全解消する。

## 2. 設計（How）

- `domain.enums.PricingPlan` → `domain.enums.RateLimitPlan` にリネーム
  - enum 値（FREE/STANDARD/PRO/ENTERPRISE）・メソッド（`burstCapacity()`/`sustainedCapacity()`）・容量値は不変
- 参照2ファイルの import と型参照を更新：
  - `infrastructure.ratelimit.RateLimitFilter`
  - `application.service.RateLimiterService`
- 旧 `PricingPlan.java` を削除

## 3. 非機能・退行防止

- 非永続（DB列なし）・非シリアライズ（JSON契約なし）・フロント参照なし → 振る舞い完全不変
- enum 値文字列は変更しない（万一の `valueOf` 経路も安全）

## 4. 受け入れ基準（オーディター監査項目）

1. `./mvnw clean test` 全件 PASS（既存退行なし）
2. `PricingPlan` がコードベースから消滅（grep 検出ゼロ）
3. `RateLimitPlan` の enum 値・容量・メソッドシグネチャが旧 `PricingPlan` と同一
4. `SubscriptionPlan` は無変更（差分に登場しないこと）
5. ADR 作成（命名統一のスコープ判断＝SubscriptionPlanを敢えて触らない理由を記録）

## 5. スコープ外（別タスク／非実施）

- `SubscriptionPlan` のリネーム（敢えて実施しない。理由は ADR に明記）
