# ADR-017: SoM 可視性スコアを PWIM-Smoothed へ転換（順位乗算依存の廃止）

## 日付

2026-06-02

## 状況

`GeoVisibilityCalculatorService.weightedSom` は各クエリの自社 SoM（GBVS）を
`brandSignal = presence × progressRate × brandSignalCore` で算出していた。`progressRate`
（`VisibilityStageMapper`）は `ai_citation_position`（AI 回答内の順位）に全依存し、順位が無い
（`null`）と 0 を返す。単独サイトのクロール＋要約解析では AI が順位リストを生成しないため
`ai_citation_position` が常に空 → `progressRate = 0` → **SoM が構造的に必ず 0**（ブランドが
118 回言及されていても 0）。これは残課題①の正体であり、長らく「プロダクトの核の設計仕様」として
保留されていた。

Gemini Deep Research（生成AI検索の可視性計測モデル調査）により、この現象が業界共通の
「順位と言及の二重構造への未対応／SoM ゼロ問題」であり、PWIM（Position-Weighted Integration
Model）＋ベイズ平滑化のハイブリッドが最適解であることが確認された。オーナー承認のもと採用する。

## 決定

**順位への乗算依存を廃し、言及=基礎点・順位=加算ボーナスの線形統合（PWIM）へ転換する。**

### レイヤA（本 ADR で実装）

`weightedSom` の `progressRate` 乗算ブロックを以下に置換：

```
mentionSignal          = clamp01(0.55·densityFactor + 0.45·countFactor)  // 既存資産を流用
                         （mentions==0 かつ aiPos!=null のとき下限 0.12）
normalizedSourceWeight = clamp01(sourceWeight / SOURCE_WEIGHT_HIGH)        // 0.2〜1.0
mentionComponent       = clamp01(mentionSignal · normalizedSourceWeight)
citationBonus          = aiPos!=null && aiPos>0 ? clamp01(1 / log2(aiPos+1)) : 0  // NDCG 対数減衰
pwim                   = clamp01(α·mentionComponent + β·citationBonus)     // α=0.6, β=0.4
```

- `computeBatch`: ジョブ内 min-max 正規化（相対評価）を廃止し、各クエリ `scorePercent = clamp(100·pwim)`
  の絶対評価へ。`modified Z-score`（median/IQR）は `visibilityStage` 判定用に温存。
- `CALCULATION_VERSION` を `V11_GEO_PURE` → `V12_PWIM` に更新（過去ジョブと区別）。
- 未使用化した `VisibilityStageMapper` の import を削除（クラス自体は他用途のため存置）。
- パラメータは定数化（`PWIM_ALPHA=0.6` / `PWIM_BETA=0.4`）。単独サイト解析が主用途のため言及重視。

### レイヤB（次スプリント・本 ADR のスコープ外）

ジョブ代表 SoM のベイズ平滑化 `GBVS_job = 100·(Σpwim + K·μ)/(N + K)`（K≈5, μ≈0.15）で
少数クエリのゼロ落ち・乱高下を防ぐ。画面/戦略診断が参照する「ジョブ代表 SoM」の保存・表示経路を
特定してから配線する（CPO 仕様書 `.cursor/plans/2026-06-02-som-pwim-smoothed.md` 参照）。

## 理由

- **乗算→加算**: 順位を「掛ける」と順位ゼロで全体がゼロになる。言及を基礎点として「足す」ことで、
  順位が無くても言及があれば可視性を非ゼロで評価できる。これが SoM ゼロ問題の根治。
- **NDCG 対数減衰**（`1/log2(aiPos+1)`: 1位=1.0, 2位≈0.63, 5位≈0.39）: 「上位引用ほど高価値」
  という情報検索の標準と Averi.ai の実務原則を数学的に表現。順位がある競合比較モードでも機能する。
- **センチメント分離の維持**: `sentimentIntensity` はスコアに乗算せずログのみ（現行設計を維持）。
  Deep Research の「可視性とセンチメントは分離せよ（ネガティブ露出を成果と誤認しない）」と一致。
- **絶対評価への変更**: min-max 正規化はジョブ内相対で、単独解析や少数クエリで意味が歪む。PWIM は
  絶対スコアなので月次の成長を素直に示せる（エージェンシーの報告用途に適合）。
- **再現性**: `StrictMath`・`clamp`・銀行家丸めにより決定論的。Z-score 温存で制約を維持。

## 結果

- **SoM ゼロ問題を解消**: 言及あり・順位なし（例: mentions=118, aiPos=null）でも非ゼロを返す。
- **テスト追加**（`GeoVisibilityCalculatorServiceTest`）: 言及のみ非ゼロ／順位のみ非ゼロ／1位>5位の
  単調性／異常密度の飽和（0〜100内）／言及なし=0／再現性。version テストを `V12_PWIM` に更新。
- **未コミット**。`./mvnw clean test` は未実施＝オーナー手動実行で確認が必要。オーディター監査は
  **実ファイル `src/` を対象**に行う（repomix スナップショットは陳腐化で偽陰性のため使わない）。
- **トレードオフ / 残課題**:
  - スコアの**絶対値はチューニング対象**。自社解析対象ページの `sourceWeight` が現状 `low=0.3` 扱いだと
    `normalizedSourceWeight=0.2` まで下がりスコアが低めに出る。「自社ページの信頼度重み付け」は α/β 調整・
    レイヤB と併せ実データで詰める。
  - レイヤB（ベイズ平滑化）未実装。少数クエリの乱高下耐性は次スプリントで付与する。
  - 戦略診断（`strategyInsightService.fromModifiedZ`）は SoM が非ゼロ化したことで動的化が見込まれる
    （オーナー要望②）。実ジョブでの確認は次回。
