# ADR-023: GEO Readiness スコアモデル V13_GEO4AXIS（MEO軸の権威・エンティティ軸への昇華）

## 日付
2026-06-04

## 状況
GEO Readiness 総合スコア(0-100)は「AI監査50 / MEO25 / 機械可読性25」で構成されていた。実コード調査で、MEO軸(`MEO_TRUST_SCORE`)が**実質「Googleクチコミ件数」をスケールしただけ**であり、生成エンジンでの言及・引用との因果が薄いと判明。ローカル業種限定でもあり（ADR-019で非地域業種から既に除外）、総合スコアの1/4をクチコミ件数に乗せている状態だった。GEO製品として「外部メディア等への第三者参照＝AIからの信頼の源泉」を測る軸へ再設計する（仕様: `.cursor/plans/2026-06-04-score-v13-geo4axis.md`）。本ADRはその第1スプリント（計算層の3軸再編＋バージョン更新）を記録する。

## 決定
- GEO Readiness を3軸に再編: **コンテンツ素地50 / 技術素地20 / 権威・エンティティ認知30**。
- `calculateFinalGeoScore` を**「適用可能な軸の最大値での正規化」**方式へ変更: `finalScore = 100 × (content + technical + authority) / applicableMax`。
- 技術素地は素点(0-25)を配点(0-20)へ線形圧縮（構造化データ等は配管シグナルのため軽め）。
- 権威軸は Sprint1 では暫定的にローカルのMEO素点(0-25)を配点(0-30)へ拡大して供給。非地域業種は当軸の入力を持たないため「適用外」とし分母から除外。
- 計算バージョンを `V12_PWIM` → `V13_GEO4AXIS` へ更新。**過去データは再計算せず**、新規ジョブから新モデルで記録（バージョン併記移行）。

## 理由
- **正規化方式の採用**: 旧 ADR-019 は非地域業種でMEO除外分を係数(×1.2/×1.6)で再配分し天井100を維持していた。軸の出し入れが増えるV13では係数手当てが破綻しやすい。「適用可能な軸の合計最大で正規化」すれば、どの軸が欠けても天井100が構造的に保たれ、Sprint2で権威軸を全業種に適用した際もこの分岐を解消するだけで済む。
- **MEOを削除せず昇華**: クチコミは権威軸のローカル向けサブ指標として価値が残るため、破棄ではなく権威軸の一構成要素へ移す。
- **バージョン併記**: スコア構成変更は全顧客の数字を変える。過去スナップショットの再計算はコスト・誤読リスクが高く、`CALCULATION_VERSION` で新旧系列を分離するのが安全。

## 結果
- `GeoVisibilityCalculatorServiceTest`（バージョン assert を V13 に更新）・`ScoringServiceTest`・`InformationTheoryBasedAggregatorTest` を含む関連テスト 24件 PASS、test-compile 成功。
- ローカル業種のスコアはMEO重み増(25→30)・技術重み減(25→20)で変動。非地域業種も正規化方式により再計算され変動するが、いずれもV13系列として記録されるため経時比較は破綻しない。
- **トレードオフ/申し送り**:
  - 権威軸はSprint1では中核（第三者言及）未配線でMEO由来の暫定値。Sprint2で SerpAPI 流用の第三者言及へ差し替え、全業種適用＝非地域の「適用外」分岐を解消する。
  - **スコア計算の第2系統**: `DefaultScoringService`(50/25/25)が `GeoAssetSnapshotService` の経時スナップショットで使用されており、本ADRの対象外。新旧モデル混在を避けるため後続スプリントで整合させる必要がある（要対応）。

## Sprint 2 追記（2026-06-04）— 権威軸の本配線

- **第三者言及の本配線完了**: 権威軸の中核を「自社ドメイン以外の独立第三者ドメイン数」で実測。
  - `ThirdPartyMentionScorer`（純粋ロジック・0-20へ飽和スケール・自社/サブドメイン除外・外部ライブラリ非依存）。
  - `ThirdPartyMentionMeasurementService`（既存SerpAPI基盤 `GeoCompetitorSearchService` を流用・1解析1コール上限・職種ワード同名対策に対応）。
  - `AiRubricAuditService` がジョブ完了監査時に自社URLへ新基準 `THIRD_PARTY_MENTIONS`(`Source.AUTHORITY`, 0-20) を1行永続化。
- **権威(0-30)の合成**: `GeoVisibilityCalculatorService.combineAuthority` = 第三者中核(0-20) ＋ ローカル業種のみMEOサブ(0-10)。非地域業種は中核のみ。
- **mode依存の解消**: 権威軸が全業種共通になったため `calculateFinalGeoScore` から `CompetitorExtractionMode` を撤去。天井は常に100で固定（Sprint1の正規化分岐は不要化）。
- **トレードオフ/申し送り**:
  - 非地域業種は当面 Wikipedia/KG ボーナス(0-10)未配線のため権威上限が実質20＝GEO Readiness 上限が90になり得る（Sprint5でボーナス配線時に解消。authority signal を欠く実体が満点に届かないのはGEO的に妥当）。
  - 飽和点(独立8ドメイン)・MEOサブ係数(×0.4)は実データでの絶対値チューニング対象。
  - `categoryKeyword` の audit 経路への引き回しは未配線（現状ブランド名クエリのみ）。次イテレーション。
