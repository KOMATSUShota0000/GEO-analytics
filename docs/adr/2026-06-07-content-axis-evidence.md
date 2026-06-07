# ADR-035: コンテンツの充実度のサイト固有エビデンス露出（クリックで根拠表示）

## 日付
2026-06-07

## 状況
スコアの信頼性を高めるため、各軸の「根拠」をユーザーに見せたい（オーナー要望）。要件は「どのサイトでも同じ定型文ではなく、そのサイト固有の事実・違いを示す」こと。

調査の結果、`audit_rubric_results` テーブル（`AuditRubricResultEntity`）が **監査×項目×サイト** 単位で `criterion_id`・`verdict`(YES/PARTIAL/NO)・**`evidence`(対象サイト本文からの直接引用)**・`score`・`is_self` を永続化していることが判明。`RubricAuditPrompts` はAIに「evidence は入力テキストからの連続した直接引用のみ」と指示しており、エビデンスは各サイトの実文章の引用＝構造的にサイト固有で定型文にならない。ただしこのper-criterion詳細はフロントに**未露出**（スコア計算にしか使われていなかった）。

## 決定（Phase1＝コンテンツ軸）
ルーブリックLLM10項目の「判定＋直接引用＋スコア」をジョブ解析レスポンスに露出し、フロントの「コンテンツの充実度」軸をクリックで展開してエビデンス表示する。

**BE（最小変更）**:
- 新DTO `web/dto/ContentEvidenceItemResponse`（criterion_id / verdict / evidence / score / max_score）。
- `JobPersistenceService.loadJobAnalysisAttachment` は既に `audit_rubric_results` を読み込み済み。これを**自社(is_self)×LLM項目**でフィルタし enum 定義順に並べて `JobAnalysisAttachment.contentEvidence` として返す（`buildContentEvidence`）。
- `JobAnalysisDetailResponse` に `content_evidence` フィールド追加。`JobController` が `attachment.contentEvidence()` を渡す。

**FE**:
- `types/analysis.ts`: `ContentEvidenceItem` 型・`parseContentEvidence`・`JobAnalysisDetail.contentEvidence`。
- `GeoScoreBreakdown`: コンテンツ軸に「根拠を見る/隠す」トグル（`Collapse`）。`ContentEvidencePanel` が10項目を表示（criterionId→日本語ラベル、判定チップ 満たす/一部/なし、本文からの直接引用「」、項目別スコア）。`JobAnalysisPage` から `contentEvidence` を渡す。

## 理由
- **サイト固有の直接引用**が「しっかり自分のサイトを読んでくれている」という信頼（オーナーの言葉）を生む。定型文の対極。
- 既存の永続データを露出するだけ＝BE変更最小・追加LLMコストゼロ。
- "保存→露出→クリックUI" の順で、競合URLのような「形だけ」を回避（測定済みアーティファクトに厳密バインド、LLM再生成しない）。

## 結果
- コンテンツ軸クリックで、10項目の判定＋サイト本文引用＋スコアが展開。`vite build` 緑、`mvn test` 緑（BE露出は既存rubric読込の再利用）。
- 技術軸（seo_technical_evidence_summary）・権威軸（第三者出典URL）のエビデンスは未永続化のため Phase2/3 で別途対応（[pending-implementations.md]）。
- 申し送り: ハブ型サイトはクロール3ページ上限で本文を取りこぼし、ルーブリックが過小評価＝エビデンスにも「該当なし」が増える。クロール深さN（費用はルーブリック15k上限で頭打ちのため案A=ページ増＋上限据え置きが低コスト）と相乗で精度向上（pending-implementations 参照）。
