---
name: Phase1.3 R7 Audit WORM
overview: "`saveOnboardingAudit` に「モデル入力に実際に載った RAG 証跡」（URL・タイトル・priorityScore・relevanceCategory）を JSONB へマージし、DB コミット後に `WormAuditExportPort` を別 Bean の `@Async` で叩いて WORM 布石のローカル書き出しを行う。既存 Flyway スキーマは jsonb 汎用のためマイグレーション不要。"
todos:
  - id: port-dto
    content: "domain: MathDebateAuditExportEvent + WormAuditExportPort"
    status: pending
  - id: local-adapter
    content: LocalWormAuditAdapter + 任意 yml 出力先
    status: pending
  - id: async-bridge
    content: WormAuditExportBridge @Async + DebateMathAuditService 保存後呼び出し
    status: pending
  - id: audit-jsonb
    content: "DebateMathAuditService: JSONB usedEvidenceRefs + Orchestrator 連携"
    status: pending
  - id: tests-r7
    content: 単体テスト（監査マップ / 非同期はモック）
    status: pending
isProject: false
---

# フェーズ1.3 第7回：最終統合と S3 WORM 監査証跡（計画）

## 前提（現状）

- [`MathDebateAuditEventEntity`](geo-analytics/src/main/java/com/geo/analytics/domain/entity/MathDebateAuditEventEntity.java) の `audit_data` は **JSONB（`Map<String,Object>`）** で柔軟に拡張可能。**DB 列追加は不要**（第7回スコープ）。
- [`DebateMathAuditService.saveOnboardingAudit`](geo-analytics/src/main/java/com/geo/analytics/application/service/DebateMathAuditService.java) は `REQUIRES_NEW` で短くコミット。[`DebateOnboardingOrchestrator`](geo-analytics/src/main/java/com/geo/analytics/application/service/DebateOnboardingOrchestrator.java) がディベート完了後に呼び出し。RAG 証拠は「クリップ後」`seoEvidences` あり、**インジェクションガードで `competitorXml` が空化**されうる（LLM に載った事実とリストのずれが生じうる）。
- [`AsyncConfig`](geo-analytics/src/main/java/com/geo/analytics/infrastructure/config/AsyncConfig.java): `@EnableAsync` 済み。非同期実行は **仮想スレッド**＋[`ContextPropagator.wrapRunnable`](geo-analytics/src/main/java/com/geo/analytics/infrastructure/tenant/ContextPropagator.java) でテナント文脈継承可能。

---

## 1. JSONB 拡張案（データ構造と意味）

### 1.1 マージ方針

既存キー（`turnCount`, `stopReason`, `wasserstein1`, `threshold`, `informationGeometryDrift`, `geoIgScore`, `trustScore`, `friction`）は**そのまま**。次を追加する。

| キー | 型 | 意味 |
|------|-----|------|
| `competitorXmlIncluded` | boolean | **`competitorXml` がガード通過後に非空でディベートへ注入されたか**（監査上の「モデルが RAG ブロックを見たか」） |
| `usedEvidenceRefs` | array | 上記が true のとき、**その XML に対応する**参照一覧（スニペット本文は保存しない） |

ガード却下や元から RAG 空の場合は `competitorXmlIncluded: false` かつ `usedEvidenceRefs: []`。**クリップ後・ガード前**に候補があったが却下された場合の差分が必要なら、オプションで `preGuardEvidenceCount`（整数のみ）を追加可能（第7回は必須としない）。

### 1.2 JSON 例

```json
{
  "turnCount": 5,
  "stopReason": "CONVERGED",
  "wasserstein1": 0.012,
  "threshold": 0.05,
  "informationGeometryDrift": 0.001,
  "geoIgScore": 0.42,
  "trustScore": 0.71,
  "friction": 0.5,
  "competitorXmlIncluded": true,
  "usedEvidenceRefs": [
    {
      "url": "https://example.com/a",
      "title": "Example A",
      "priorityScore": 0.92,
      "relevanceCategory": "OTHER"
    }
  ]
}
```

### 1.3 ゼロコピー規約（Orchestrator）

- `evidencesActuallyConsumed` をループ直前に決定:  
  `competitorXml` がガード後も非空なら `seoEvidences`（クリップ済みリスト）、**空なら `List.of()`**（ガード却下・初源なしの区別は `competitorXmlIncluded` で表現）。
- `saveOnboardingAudit(..., List<SeoEvidence> usedEvidences, boolean competitorXmlIncluded)` のように引数を拡張するか、`usedEvidences` のみ渡してサービス側で `included = !usedEvidences.isEmpty()` とするか：**明示フラグ**を推奨（「リスト非空だが XML 空」は通常無いが、将来の差分に強い）。

### 1.4 ビルドヘルパー（任意）

[`DebateMathAuditService`](geo-analytics/src/main/java/com/geo/analytics/application/service/DebateMathAuditService.java) 内で `SeoEvidence` → `Map` に変換する private メソッド、または [`domain` 配下に小さな `AuditEvidenceRef` record](geo-analytics/src/main/java/com/geo/analytics/domain/model/SeoEvidence.java) とマッパーを置く。**DTO 専用クラス名**: ユーザー指定の `DebateMathAuditData` は「意味としての監査 JSON」に相当。実装では `Map` 生成でも `record DebateMathAuditDataPayload` でも可（JSON 出力用に `ObjectMapper` は WORM 側のみ）。

---

## 2. WORM エクスポート（Port / Adapter）

### 2.1 Port（ユーザー指定パス）

新規 [`com.geo.analytics.domain.port.WormAuditExportPort`](geo-analytics/src/main/java/com/geo/analytics/domain/port/WormAuditExportPort.java):

```java
void export(MathDebateAuditExportEvent event);
```

インフラに引きずらないため、**永続後スナップショット**用の domain record を定義する（例: `MathDebateAuditExportEvent` with `UUID id`, `UUID targetId`, `String eventType`, `Map<String,Object> auditData`, `Instant createdAt`）。エンティティをそのまま渡さない方が Port の依存方向がきれい。

### 2.2 ダミー実装

[`LocalWormAuditAdapter`](geo-analytics/src/main/java/com/geo/analytics/infrastructure/adapter/LocalWormAuditAdapter.java)（`@Component`）が `WormAuditExportPort` を実装:

- **第7回**: `ObjectMapper` で 1 行 JSON または pretty JSON を **標準出力**、または設定パス（例 `app.worm-audit.local-output-dir`）への **`{id}.json`** 追記。**S3 / Object Lock は未実装**（コメントで後続フェーズを明示）。

### 2.3 非同期の切り離し（レスポンス影響を与えない）

**同クラス内 `@Async` は効かない**ため、次のいずれかを採用する（推奨は A）。

- **A（推奨）**: 新規 `@Service` `WormAuditExportBridge`（名前任意）に `@Async` メソッド `void exportAsync(MathDebateAuditExportEvent payload)` を置き、内部で `wormAuditExportPort.export(payload)` を同期呼び出し。  
  [`DebateMathAuditService`](geo-analytics/src/main/java/com/geo/analytics/application/service/DebateMathAuditService.java) は `repository.save(row)` で **`UUID id` を取得した直後**、`bridge.exportAsync(snapshot)` を呼ぶ。**`REQUIRES_NEW` メソッドが return した後**にトランザクションはコミット済み。`@Async` は別スレッドで即スケジュールされ、呼び出しスレッドはブロックしない（ごくわずかなキューイングコストのみ）。

- **B**: `ApplicationEventPublisher` + `@TransactionalEventListener(phase = AFTER_COMMIT)` で同趣旨。第6回方針と整合だが、第7回は A で十分。

**失敗時**: アダプタ内で例外はキャッチしログ（本番で S3 時も再試行キューは別タスク）。アプリ本体のオンボーディング結果は既に確定しているため **WORM 失敗でユーザー API を失敗させない**。

---

## 3. 変更ファイル一覧（承認後）

| ファイル | 内容 |
|----------|------|
| [`DebateMathAuditService`](geo-analytics/src/main/java/com/geo/analytics/application/service/DebateMathAuditService.java) | 引数拡張、`audit_data` への `usedEvidenceRefs` / `competitorXmlIncluded`、保存後 `exportAsync` |
| [`DebateOnboardingOrchestrator`](geo-analytics/src/main/java/com/geo/analytics/application/service/DebateOnboardingOrchestrator.java) | ガード後の「実消費」リストとフラグを算出し `saveOnboardingAudit` に渡す |
| 新規 `domain/port/WormAuditExportPort.java`, `domain/model/MathDebateAuditExportEvent.java`（名は実装で調整可） | Port とimmutable DTO |
| 新規 `infrastructure/adapter/LocalWormAuditAdapter.java` | ダミー WORM 出力 |
| 新規 `application/service/WormAuditExportBridge.java`（案） | `@Async` 薄ラッパー |
| [`application.yml`](geo-analytics/src/main/resources/application.yml) / [`AppProperties`](geo-analytics/src/main/java/com/geo/analytics/infrastructure/config/AppProperties.java) | 任意: ローカル出力ディレクトリ、STDOUT トグル |

---

## 4. Oracle / 将来 S3 への方針（宣言）

- トランザクション境界は従来どおり **Spring `@Transactional` / `REQUIRES_NEW`** のみ。WORM 書き込みは **コミット後・非同期**で DB 接続と分離。
- S3 Object Lock / WORM バケットは **Adapter 差し替え**で対応。Port 契約は変えない。

---

## 5. テスト（承認後）

- `DebateMathAuditService` の単体テスト（モック Repository）: `usedEvidenceRefs` のサイズとフィールド、`competitorXmlIncluded`。
- `LocalWormAuditAdapter` またはマッパーのスモーク（一時ディレクトリにファイル生成）。
