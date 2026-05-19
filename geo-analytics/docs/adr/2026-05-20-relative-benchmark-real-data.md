# ADR-004: StrategyDashboard 相対評価セクションの実データ化

## 日付

2026-05-20

## 状況

`RelativeEvaluationSection.tsx` が `MOCK_ROWS`（AI言及シェア／構造化シグナル密度／エンティティ解像度の3行）をハードコード表示していた。バックエンドの競合比較APIが存在せず、全契約先に同一の偽データが表示される状態であり、プロダクトの核②「完全ホワイトラベル対応の競合比較チャート＝実利」の信頼性を根本から毀損していた。

## 決定

- `GET /api/v1/projects/{projectId}/relative-benchmark` を新設（`ProjectAnalyticsController`）
- 認可は既存 `@tenantAccessEvaluator.canReadProjectAssetSnapshots` を流用（プロジェクトスコープ＋ロールチェックの意味論が一致するため新規 evaluator を作らない）
- `RelativeBenchmarkService`（`@Transactional(readOnly=true)`）が3指標を**既存の実テーブル**のみから算出：
  - **AI言及シェア**: `AnalyticsAggregationService.summarizeProject()` の `trendData`（自社SoM）と `competitorShares`（競合シェア）
  - **構造化シグナル密度**: `audit_rubric_results` の `MACHINE_READABILITY_SIGNAL`（max 25.0）self/competitor 平均
  - **エンティティ解像度**: `audit_rubric_results` の `ENTITY_BIOGRAPHY`（max 5.0）self/competitor 平均
- rubric 集計は `AuditRubricResultRepository.aggregateByCriterion()` の単一 GROUP BY クエリで N+1 を回避。RLS により tenant_id は自動スコープ
- 競合比較は Pro 機能（既存 `competitorShares` の Pro ゲート方針に一致）。非対象プランは `locked=true`、未解析・実データ皆無は `available=false` を返し、**偽値は一切返さない**
- フロントは `useRelativeBenchmark` フックで取得し、locked / unavailable / loading / error / data の各状態を明示描画。`MOCK_ROWS` を削除

## 理由

- **モック撤廃の絶対要件**: CLAUDE.md「スタブやモックで誤魔化さない」。実データが無い場合に推測値を出すより、未解析/上位プラン限定であることを明示する方がプロダクトの信頼に資する
- **既存資産の再利用**: SoM・競合シェアの集計ロジックは `AnalyticsAggregationService` に既存。Jackson パースを重複させず単一の真実源を維持
- **アーキ規約遵守**: 読み取り専用トランザクション、テナントスコープは `TenantPlanScope`/`TenantContextHolder` 経由、ThreadLocal/synchronized/parallel 不使用
- **テスト容易性**: 行組み立てロジックを静的純粋メソッド `assemble`/`foldAggregates` に分離し、テナント静的依存なしで単体検証可能

## 結果

- Pro 以上 ＋ 解析済み: 3指標が `audit_rubric_results` と SoM 集計から実データで描画される
- Standard プラン: ぼかしロック表示（Pro アップセル導線）
- 未解析プロジェクト: 「解析完了後に表示されます」を表示し偽値を出さない
- 退行なし: 既存テーブル・既存集計を読むのみでスキーマ変更・マイグレーション不要
- スコープ外: `/api/v1/projects/{id}/analytics` 自体の `@PreAuthorize` 欠落は別チケット
