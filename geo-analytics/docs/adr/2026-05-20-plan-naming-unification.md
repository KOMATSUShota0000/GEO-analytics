# ADR-005: Plan 命名統一（PricingPlan → RateLimitPlan / SubscriptionPlan は不変）

## 日付

2026-05-20

## 状況

`SubscriptionPlan`（課金）と `PricingPlan`（レート制限）の2系統が併存し、名前が紛らわしいという技術的負債（dev-status 懸念点#8）。特に `PricingPlan` は「価格表/課金」を連想させるが、実体は Bucket4j のレート制限容量（burst/sustained）専用で課金とは無関係だった。

## 決定

- `PricingPlan` を `RateLimitPlan` にリネーム（`domain.enums`）。enum 値・容量・メソッドシグネチャは不変
- 参照2ファイル（`RateLimitFilter`・`RateLimiterService`）の型参照を更新、旧 `PricingPlan.java` を削除
- **`SubscriptionPlan` は敢えてリネームしない**

## 理由

- **混乱の根本原因は `PricingPlan` の誤名**であり、これを `RateLimitPlan` に正せば「課金=SubscriptionPlan / レート制限=RateLimitPlan」と用途が名前で自明になる。混乱は完全に解消する
- **`SubscriptionPlan` は既に意味的に正しい**（subscription=課金）。改名する積極的理由がない
- **スコープと退行リスクの非対称性**:
  - `PricingPlan`: 7箇所/3ファイル、非永続・非JSON・フロント参照なし → 振る舞い完全不変、低リスク
  - `SubscriptionPlan`: 193箇所/54ファイル、JSON契約・DB永続化・フロント・AIプロンプトに波及 → 巨大 churn と退行リスクのみで、MVP価値はゼロ
- 「MVPに直結しない大規模リネームは着手前に費用対効果を問う」という秘書（アーキ守護者）の判断に基づき、最小侵襲で課題本質を解消する選択を採用した

## 結果

- レート制限の型が `RateLimitPlan` となり、課金 (`SubscriptionPlan`) との用途差が命名で自明化
- `PricingPlan` はコードベースから消滅
- 振る舞い・JSON契約・DBスキーマに変更なし（退行なし）
- `SubscriptionPlan` の大規模リネームは費用対効果から非実施（将来必要になれば別途 ADR を起こす）
