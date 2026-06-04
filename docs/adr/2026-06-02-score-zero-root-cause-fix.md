# ADR-015: 解析結果スコア全0問題の真因修正（target_url 保存・SoM 経路クロール接続・ルーブリック監査 400・non_jp フィルタ撤去）

## 日付

2026-06-02

## 状況

オーナーより「解析は完了するが結果ページのスコアが全部0になる」との報告。当初は単一原因と読んだが、ログ・実DB・実URL（`日横クリニック` https://www.himawari-kai.org/station.html）で段階的に調査したところ、**4層の独立した障害**が重なっていた。いずれも例外を握り潰してジョブを COMPLETED にする「サイレント失敗」だったため、不具合が見えにくくなっていた。

1. **project.target_url 保存漏れ**: `ProjectManagementService` がプロジェクトを `brandName` だけで getOrCreate し、`target_url` を `""` 固定で作成していた。後段の解析パイプライン（競合抽出・ルーブリック監査・ベンチマーク）が `project.getTargetUrl()`（空）を参照して軒並み空振り。
2. **SoM 経路がクロール未接続**: `JobQuerySubmissionService` が `verify()`（URL 無し）を使用し `verifyWithUrl()` を呼んでいなかった。対象ページがクロールされず、AI が internal knowledge mode で空応答（raw_response 約209字）。
3. **non_jp フィルタの誤適用**: `DomainTrustService` が `.jp` のみを日本ドメイン扱いし、`.org`/`.com` の日本企業サイト（himawari-kai.org）の**自社解析対象ページのクロールまで破棄**していた。GEO ツールとして致命的。
4. **ルーブリック監査の空 contents 400**: `RubricAuditService` が `SystemMessage` のみで Gemini を呼び、`contents`（UserMessage）が無く HTTP 400「contents is not specified」。**ルーブリック監査は一度も成功しておらず**、`audit_rubric_results` は常に0行だった。

## 決定

実装済み・未コミットの4修正（A〜D）。

### 修正A: project.target_url を実 URL で保存

`ProjectManagementService.getOrCreateDefaultProject` / `getOrCreateDefaultProjectForWorkspace` のシグネチャに `String targetUrl` を追加し、新規作成時に `target_url` をセット。既存プロジェクトが空の場合は `backfillTargetUrlIfMissing` で一度だけ補完。呼び出し側 `JobPersistenceService` から `fields.targetUrl()` を渡す。

- `application/service/ProjectManagementService.java`
- `application/service/JobPersistenceService.java`

### 修正B: SoM 測定をクロール接続（verify → verifyWithUrl）

`JobQuerySubmissionService.executeImmediateParallelProcessing` で `job.targetUrl` を取得して `processOneQueryRealtimeCore` へ渡し、`targetUrl` 非空時は `syncVerificationService.verifyWithUrl(...)`、空（旧ジョブ）の場合のみ既存の `verify(...)` にフォールバック。

- `application/service/JobQuerySubmissionService.java`

### 修正C: ルーブリック監査の Gemini 呼び出しを System＋User 2メッセージ構成へ

`RubricAuditPrompts.systemPrompt` を `systemInstruction()`（指示）と `userPayload(websiteText, jobContextBlock)`（本文）に分割。`RubricAuditService` の `ChatRequest` を `SystemMessage.from(systemInstruction())` ＋ `UserMessage.from(userPayload(...))` の2メッセージに変更し、`dev.langchain4j.data.message.UserMessage` の import を追加。

- `infrastructure/ai/RubricAuditPrompts.java`
- `application/service/RubricAuditService.java`

### 修正D: non_jp フィルタの撤去

`DomainTrustService.applyDomainPolicy` から `non_jp` チェックブロック（`!isJapanDomestic(h) && !isAllowNonJp(h)` → `stripCrawl`）と private メソッド `isJapanDomestic` / `isAllowNonJp` を削除。自社の解析対象ページ（ユーザーが明示指定した `target_url`）は、日本ドメインか否かに関わらず必ずクロールする。明示ブロック（`isBlockedByRule`）・翻訳サイト除外（translation_host）・trust boost（`resolveTrustBoost`）は維持。

- `application/service/DomainTrustService.java`

## 理由

- **修正A**: パイプライン全体が `project.getTargetUrl()` を解析対象の起点に据えているため、ここが空だと下流の全機能が空振りする。新規・既存の両経路で確実に URL を持たせるのが最小かつ根本の修正。backfill は空のときのみ作用させ、既存の有効値は上書きしない。
- **修正B**: SoM（ブランド可視性）の測定には対象ページの実コンテンツが必須。URL 無しの `verify()` では AI が internal knowledge に頼り空応答になる。旧ジョブ（URL 無し）の後方互換のため、空時のみ従来経路へフォールバックする。
- **修正C**: Gemini の `generateContent` は `contents`（User ロール）が空だと 400 を返す。System のみの構成は仕様上不正で、監査が一度も成立していなかった。指示と解析対象本文を System/User に正しく分離する。
- **修正D**: GEO ツールの中核は「ユーザーが指定した自社ページを解析すること」。`.jp` 以外を一律破棄するフィルタは、この一丁目一番地を破壊していた。一方で外部 RAG 証拠に対する明示ブロック・翻訳サイト除外・信頼度ブーストは引き続き必要なため維持する。`applyDomainPolicy` が自社ページ経路でのみ呼ばれることを確認の上で撤去した。

## 結果

- 実ジョブ（dev 環境）で検証：クロール成功、AI 実解析（raw_response 700〜970字）、`audit_rubric_results` 12行（スコア入り）、**GEO Readiness Score 0 → 30.0/100（AI 監査 30/50）** を画面 F5 再読込で確認。`project.target_url` も保存・表示されることを確認。
- ビルド: `./mvnw test-compile` BUILD SUCCESS（既存 deprecation 警告のみ）。**フルテスト（`./mvnw clean test`）は未実施**＝コミット前にオーナー手動実行で確認が必要。
- **保留（要オーナー設計判断・本 ADR のスコープ外）**: SoM / Tier が依然0。これはバグではなく評価モデル（GBVS）の設計仕様で、`GeoVisibilityCalculatorService.weightedSom` の `progressRate` が `ai_citation_position`（AI 回答内の順位）に全依存し、単独サイト解析では構造的に0になる。プロダクトの核のため別途設計判断とする。
- 残課題: MEO トラスト0（Google Places 未配線）、機械可読性0（対象サイトが Schema.org 未実装で妥当の可能性）。
- 学び: 「単一原因」と早期に結論づけたが実際は4層だった。コンソールログが最速の真因特定手段であり、サイレント失敗（クロール空・AI 空応答・監査400 を握り潰して COMPLETED）の可視化が今後の堅牢性改善候補。
- トレードオフ: 修正Dにより自社解析対象ページは非.jp でも必ずクロールされる。明示ブロック・翻訳除外は維持されるが、対象ページ自体のドメイン国別フィルタは無くなった（GEO 解析対象としては意図どおり）。
