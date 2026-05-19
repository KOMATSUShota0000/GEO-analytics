# 仕様書: `isProPlanUi` ハードコード解消

## 目的

`JobAnalysisPage.tsx` の255行目にある `const isProPlanUi = false` を撤廃し、ログインユーザーが所属するワークスペースの実際のサブスクリプションプラン（STANDARD / PRO / EXPERT）に基づいて `TierDiagnosisCard` のアップセルバナー表示を制御する。

現状は全ユーザーが常に Standard プラン扱いとなり、SoM スコアが 69.5 以上になっても正しくアップセルバナーが発火しない。SaaS グロースの根幹を壊しているバグであり、最優先で修正する。

## 対象ユーザー

- Geo Analytics を利用中のすべてのワークスペースユーザー
- 特に Pro / Expert プランの契約者（現状は誤ってアップセルバナーが出続ける）
- Standard プランで SoM スコアが高い契約者（現状はアップセルバナーが出ない）

## 機能要件

- `JobAnalysisPage` が表示される際、現在のワークスペースのサブスクリプションプランをバックエンドから取得できること
- 取得したプランが PRO または EXPERT の場合、`isProPlanUi` を `true` として扱い、アップセルバナーを非表示にすること
- 取得したプランが STANDARD の場合、`isProPlanUi` を `false` として扱い、SoM スコア 69.5 以上でアップセルバナーを表示すること
- API 呼び出しが失敗した場合や、レスポンスが不正な場合は、`false`（保守的なフォールバック）として扱うこと（ダウングレード方向ではなくアップセル表示を優先する）

## UI/UX 要件

- プラン情報の取得は、ページ初回マウント時にバックグラウンドで非同期取得する
- 取得完了前は `isProPlanUi = false` として扱う（アップセルが一瞬表示されてから消える可能性は許容する。ちらつきが問題になれば Sprint 2 以降で対処）
- ページのローディング体験を壊さないこと（プラン取得の失敗でページ全体が壊れてはいけない）
- PDF 印刷モード（`?pdf=1`）では、アップセルバナーは既存の `pdf-no-print` クラスで非表示になるため、この仕様の変更対象外

## データ要件

### フロントエンド側

- ワークスペース情報を取得するための新規 API 呼び出し（`/api/v1/workspaces/{workspaceId}` の GET）を追加する
- レスポンスに `subscriptionPlan` フィールド（`"STANDARD"` / `"PRO"` / `"EXPERT"` のいずれか）を含める
- ワークスペース ID はジョブ解析レスポンスの `projectId` から辿るか、認証情報から取得する

### バックエンド側

- `WorkspaceController` の GET エンドポイントが現状 `noContent()` を返しているため、ワークスペース情報（少なくとも `subscriptionPlan`）を JSON で返すよう拡張する
- レスポンス DTO に `subscription_plan` フィールドを含めること
- `WorkspacePlanResolver.resolvePlan()` はすでに実装済みのため、コントローラーはこれを呼び出すだけでよい
- テナントアクセス制御（`@PreAuthorize`）は既存の `tenantAccessEvaluator` を維持すること

### 具体的なデータフロー

```
フロント: JobAnalysisPage マウント
  → /api/v1/workspaces/{workspaceId} GET
  → WorkspaceController がサブスクリプションプランを取得して返却
  → フロント: isProPlanUi = plan === "PRO" || plan === "EXPERT"
  → TierDiagnosisCard に渡す
```

### ワークスペース ID の取得順序

1. `data.project.projectId` が存在する場合、プロジェクト経由でワークスペース ID を取得する（バックエンド側でプロジェクトからワークスペースを解決するエンドポイントを追加するか、ジョブ解析レスポンスにワークスペース ID を含める）
2. または、認証済みユーザーの「現在のワークスペース」エンドポイント（`/api/v1/workspaces/current` 等）を新設する方が最もシンプル

> コーダーへの注記: 実装方法は上記2択のどちらでも可。最もシンプルな方を選ぶこと。既存の `BrandingContext` でワークスペース ID が保持されているかを確認し、流用できれば流用する。

## プロダクトの核との整合性

- **WOW体験**: 直接の影響なし。ただしプラン表示が正しくなることで信頼性が向上する
- **実利**: PDF 出力に影響しないため変更なし
- **SaaSグロース**: アップセルバナーが正しく機能するようになる。これがこの仕様の主目的。Pro ユーザーにはバナーが出なくなり UX も改善する
- **高利益率**: LLM 呼び出しなし。新規 DB クエリ1本のみ追加（`WorkspacePlanResolver` の既存実装を再利用）

## スプリント（タスク）分割案

- [ ] Sprint 1: バックエンド — `WorkspaceController` の GET エンドポイントを実装し、`subscription_plan` を含む JSON を返す
- [ ] Sprint 2: フロントエンド — ワークスペース情報取得 API クライアントを追加し、`JobAnalysisPage` でプランを取得して `isProPlanUi` を動的に計算する
- [ ] Sprint 3: 動作確認と型安全性 — `EXPERT` プランを `usesProTierFeatures()` の定義と同様に PRO 扱いとして扱う型変換を `SubscriptionPlanApi` 型に追加する（現状 `"STANDARD" | "PRO"` の2値だが `"EXPERT"` を追加して正規化する）

## 受け入れ条件

- Standard プランのワークスペースで SoM スコアが 69.5 以上のジョブを表示すると、アップセルバナーが表示される
- Pro または Expert プランのワークスペースでは、SoM スコアにかかわらずアップセルバナーが表示されない
- API エラー時はアップセルバナーが表示される（保守的フォールバック）
- 既存の39個のテストが引き続きパスすること
- フロントエンドが `npm run build` で正常にビルドされること

## 成果物の保存先

`C:\cursor\project\.cursor\plans\2026-05-16-isProPlanUi-fix.md`
