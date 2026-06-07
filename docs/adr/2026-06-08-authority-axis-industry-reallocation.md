# ADR-035: 権威軸の業種別再配分（非地域業種でMEO撤廃・第三者言及を0-30へ拡張）

- 日付: 2026-06-08
- ステータス: Accepted
- 関連: ADR-023（V13_GEO4AXIS 3軸配点）、`2026-06-08-authority-axis-industry-reallocation.md`（仕様）、`2026-06-04-decisions`（MEO=ローカル遺産の指摘）

## コンテキスト

V13_GEO4AXIS の権威軸(0-30) は「第三者言及中核(0-20) ＋ ローカルMEOサブ(0-10)」で構成される。`authorityLocalMeoSub` は非地域業種（`CORPORATE_SERVICE` / `ONLINE_SERVICE`）で 0 固定であり、かつ `wikipediaKgBonus` は未配線(0.0)。結果、**非地域業種は権威軸の実質天井が20点に張り付き、全体100点のうち10点を構造的に取得できない**。BtoB/SaaS/EC クライアントのスコアが不当に低く出て、代理店の提案の説得力を損なっていた。

MEO（Googleクチコミ件数）は GEO（生成AIでの言及・引用）との因果が薄く（2026-06-04 の指摘）、本来ローカル業種限定の指標である。一方、事実確認（Google「Grounding with Google Maps」GA, 2026）により、**ローカル業種ではクチコミが生成AIの回答根拠になる**ことが裏付けられたため、地域業種ではMEOを温存する。

## 決定

権威軸の内訳を業種で再配分する。**3軸総配点（コンテンツ50／技術20／権威30）は全業種で不変**。

| 業種 | 第三者言及中核 | ローカルMEOサブ |
|------|---------------|----------------|
| `LOCAL_STORE` | 0-20（従来） | 0-10（従来） |
| `CORPORATE_SERVICE` / `ONLINE_SERVICE` | **0-30（拡張）** | 撤廃（0固定・UI行非表示） |

### 実装方式（アプローチY: 集約段階でのスケール）

素点生成（`ThirdPartyMentionScorer.scoreFromMentionUrls` ＝ SerpAPI測定、DB保存される `THIRD_PARTY_MENTIONS` の score）は **0-20 のまま業種非依存で据え置く**。業種差は集約計算 `GeoVisibilityCalculatorService.authorityThirdPartyCore(core, mode)` の1点でのみ生む：

- 地域業種: `clamp(core, 0, 20)`（従来通り）
- 非地域業種: `clamp(core × 30/20, 0, 30)`（中核を1.5倍に線形拡張）

これにより非地域業種は第三者言及だけで権威軸の天井30に到達可能になる（独立8ドメインで満点）。

### 代替案（不採用）
- **素点生成段階でmode適用**: `scoreFromMentionUrls`/`measure`/`AiRubricAuditService` 全てにmode伝播が必要で配線が広く、DB保存値の意味が業種で変わり後方互換を損なう。アプローチYを採用。
- **MEO完全撤廃（全業種統一）**: 地域業種では事実上クチコミが生成AIの根拠になる（Grounding with Google Maps）ため不適切。

## 結果

- **後方互換**: DB保存の `THIRD_PARTY_MENTIONS` score は不変。既存ジョブは再計算せず、新規解析から新配点。
- **回帰ゼロ**: 地域業種(LOCAL_STORE)のスコアは従来と完全一致（単体テストで担保）。
- **配線最小**: 変更は `GeoVisibilityCalculatorService.authorityThirdPartyCore`／`combineAuthority`、`JobPersistenceService` の内訳露出1行、`GeoScoreBreakdown`（FE）の出し分けのみ。`measure`/`ThirdPartyMentionScorer`/`AiRubricAuditService` は不変。
- **UI**: 非地域業種は権威軸サブ行「第三者言及の広がり」を上限30で表示し、「ローカル評判（クチコミ）」行を非表示。

## 申し送り（別タスク）

現MEO点は「クチコミ件数」のスケールだが、生成AIが根拠化するのは「クチコミの内容・評価・属性」。件数→評価への精度改善余地あり（MVP優先で今回スコープ外）。
