---
name: Phase1.3.5 Cleanup
overview: "`TokenProfitGuard` のスニペット切り詰めをコードポイント境界で行うよう改修し、`PromptInjectionValidator` を新設の `application.security` パッケージへ移動して import を更新する。それ以外のビジネスロジックは変更しない。"
todos:
  - id: cp-truncate
    content: "TokenProfitGuard: codePointCount/offsetByCodePoints ベースの切り詰め＋必要ならテスト1件"
    status: completed
  - id: move-validator
    content: PromptInjectionValidator を application.security へ移動、Orchestrator/Test の import・テストパッケージ修正
    status: completed
  - id: verify-build
    content: mvnw compile / 関連テストで回帰確認
    status: completed
isProject: false
---

# フェーズ1.3.5：クリーンアップ（計画）

## スコープ宣誓

**本フェーズでは次の 2 タスクのみを実施する。**  
**上記以外の既存ビジネスロジック（ディベート条件、ガード方針、Token 予算式、WORM、XML 構造、プロンプト文言等）には一切手を入れない。**

---

## タスク1: サロゲートペア安全な切り詰め（[`TokenProfitGuard`](geo-analytics/src/main/java/com/geo/analytics/domain/logic/TokenProfitGuard.java)）

### 現状の問題

- [`fitSingleEvidence`](geo-analytics/src/main/java/com/geo/analytics/domain/logic/TokenProfitGuard.java) が `n` を **`String.length()`（UTF-16 コード単位）** として `snippetPrefixWithEllipsis` に渡し、[`substring(0, n)`](geo-analytics/src/main/java/com/geo/analytics/domain/logic/TokenProfitGuard.java) で切断している。補助平面文字（絵文字・一部漢字）では **サロゲート境界で split** する恐れがある。

### 改修方針（具体的ロジック）

1. `full` に対し `int totalCp = full.codePointCount(0, full.length())` を算出。
2. **`for (int prefixCp = totalCp; prefixCp >= 0; prefixCp--)`** で二分探索ではなく現状と同様の線形探索を維持（挙動・性能特性を変えない。N はスニペット長によりせい小さい）。
3. 新ヘルパー（例 `snippetPrefixWithEllipsisByCodePoints(String full, int prefixCpCount)`）:
   - `full.isEmpty()` → `""`
   - `prefixCpCount >= totalCp` → `full`（省略なし）
   - `prefixCpCount <= 0` かつ非空 → `SNIPPET_TAIL_ELLIPSIS` のみ（現行と同様）
   - それ以外: `int endCharIndex = full.offsetByCodePoints(0, prefixCpCount)` → `full.substring(0, endCharIndex) + SNIPPET_TAIL_ELLIPSIS`  
     `offsetByCodePoints` により **常にサロゲート境界**で切れる。
4. 既存の `snippetPrefixWithEllipsis` は **削除**し、上記に置換（タスク範囲内の整理としてプライベート API を一本化）。

### テスト（推奨・タスク1の範囲内）

- [`TokenProfitGuardTest`](geo-analytics/src/test/java/com/geo/analytics/domain/logic/TokenProfitGuardTest.java) に、BMP+絵文字など **補助平面を含む `snippet`** で切り詰め後に **`Character.isHighSurrogate` のみで終わる文字列にならない**こと、または **XML 長が閾値以下**であることの 1 ケース追加（過剰なロジック追加はしない）。

---

## タスク2: [`PromptInjectionValidator`](geo-analytics/src/main/java/com/geo/analytics/domain/logic/PromptInjectionValidator.java) のパッケージ移動

### 移動先

- **新規パッケージ**: `com.geo.analytics.application.security`（現状リポジトリに該当ディレクトリなし → **新設**）
- **新パス**: [`geo-analytics/src/main/java/com/geo/analytics/application/security/PromptInjectionValidator.java`](geo-analytics/src/main/java/com/geo/analytics/application/security/PromptInjectionValidator.java)
- クラス本体（依存: `AiConfig`, `AppProperties`, LangChain4j, Spring `@Component`）は**そのまま**。**パッケージ宣言と import のみ**変更不要なものは据え置き。

### 影響ファイル（import / テスト配置）

| 種別 | パス |
|------|------|
| 本番 | [`DebateOnboardingOrchestrator.java`](geo-analytics/src/main/java/com/geo/analytics/application/service/DebateOnboardingOrchestrator.java) — `import` とコンストラクタ型は同一クラス名のためパッケージ更新のみ |
| 移動＋削除 | `domain/logic/PromptInjectionValidator.java` → 上記へ物理移動し旧ファイル削除 |
| テスト | [`PromptInjectionValidatorTest.java`](geo-analytics/src/test/java/com/geo/analytics/domain/logic/PromptInjectionValidatorTest.java) → **`src/test/java/com/geo/analytics/application/security/PromptInjectionValidatorTest.java`** へ移動し、`package` と `PromptInjectionValidator` の import を更新 |

**Grep 上、他参照はなし**（実装前に再 grep で最終確認）。

### 検証

- `mvnw -q test`（少なくとも `TokenProfitGuardTest`, `PromptInjectionValidatorTest`）および `compile`。

---

## タスク外（明示的にやらないこと）

- `escapeXml` の制御文字フィルタ、`fail-open` 設定の削除、WORM/監査、`SeoDataEvidenceProvider` の `@Component` 移動、`SimilarityScorer` の Bean 明示化など **監査で WARN となった他項目**は **フェーズ1.3.5 の対象外延長として扱わない**。
