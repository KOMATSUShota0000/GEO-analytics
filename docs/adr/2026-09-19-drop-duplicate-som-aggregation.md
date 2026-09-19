# ADR-056: SoM の二重実装を撤去する（未配線の集約を削除）

## 日付
2026-09-19

## 状況

SoM の算出が2箇所にあった（#67）。

| | 版名 | 稼働 |
|---|---|---|
| `SomScoreCalculator` → `GeoVisibilityCalculatorService` | V13_GEO4AXIS → V14_AIOVERVIEW | **本番稼働中** |
| `InformationTheoryBasedAggregator.aggregate()` | V11_GEO_PURE | **未配線**（テストからのみ呼ばれる） |

`aggregate()` は複数モデルの検証結果を束ねる想定の集約で、自社シグナルと競合シェアの比から独自に SoM を算出していた。`.cursorrules` 10節「Shadow Implementation 禁止」に反する。

## 決定（オーナー確定 2026-09-19）

**`aggregate()` をメソッドごと撤去する。** 併せて、それだけが使っていたクラス（`InformationTheoryBasedAggregator`）を削除し、本番で使われていた `finalizeGbvsBatchForJob` は `SomScoreCalculator.computeBatchForJob` の呼び出しへ置き換えた（1行の委譲しか残らないため）。

## 理由

### 残すほうが危険

`aggregate()` は「呼べば動く」状態で残っていた。将来これを配線すると、本番とは**別の式で別の値**が出る。しかも版名が `V11_GEO_PURE` のため、記録上も別物として混ざる。

### 表記ゆれの検証は失われない

`aggregate()` の価値はエンティティ集約（表記ゆれの合算）にあったが、その検証は #64/#65 で追加した `CompetitorSelectionSudachiTest`（自社除外・残余カテゴリ除外・表記ゆれの重複除外）が担う。**名寄せの実行位置が「集約時」から「生成時」へ移った**ため、テストもそこへ移した。

### 複数モデルの集約が必要になったら

現在 LLM は Gemini の1系統のみ。将来複数モデルを束ねるときは、本番の計算（`SomScoreCalculator`）を土台に設計し直す。**当時の実装を復活させない。**

## 結果

- SoM の算出ロジックが1箇所になった。
- `InformationTheoryBasedAggregator` と専用テスト、未配線の集約に依存していた統合テスト1件（`scenarioD`）を削除した。
- `CompetitorResult` は生成されるが消費先が無い状態が続く（#112 の保存・表示で消費する）。
