# 仕様書: dev環境ハードニング（Sprint10.6）

- **作成**: CPO室 / 2026-05-20
- **対象**: Sprint10.6（実機検証ブロック解消）
- **関連**: 実機検証で「ジョブが SerpAPI キー未設定で即死」「クレジット 0 で全 API がInsufficientCreditException」発覚

## 1. 背景・目的（What / Why）

オーナーが UI から解析を実行したところ 2 系統で失敗：
- **A. SGE 計測の設計矛盾**：`SerpApiKeyStartupCheck` のコメントは「プレースホルダ降格は既存設計」と謳うが、`AsyncSgeMeasurementService:67` は実際には `IllegalStateException` を投げてジョブを FAILED にする。Gemini 解析自体は完走しているのに勿体ない
- **B. クレジット未シード**：`V1__init_schema.sql:482` でデフォルト org の `credit_balance=0` 投入。`DataSeeder` がトップアップしない → あらゆる解析が `CreditVaultService.reserve` で即死

## 2. 設計（How）

### 2.A SGE 降格の真実装（AsyncSgeMeasurementService）

`measureSgeForJob()` の入口でキー有無を判定：
- **キー設定済み** → 従来どおり SerpAPI を呼ぶ
- **キー未設定** → **新動作**：各クエリに対し空 SGE 結果（`mentioned=false, mentionCount=0, rawResponseJson="{}"`）を `insertSgeResult` で書き込み、WARN ログ、**例外を投げず正常 return**

これで：
- 起動時 WARN が約束する "降格" が実装と一致
- Gemini 解析は引き続き完走、ジョブ全体は SUCCESS で完了
- 本番では SERPAPI_API_KEY 必須運用のため、この分岐は走らない

### 2.B dev クレジット潤沢化（DataSeeder）

`DataSeeder.seedData()` 内：
- `OrganizationRepository.findByIdForUpdate(orgId)` でデフォルト org をロック
- `creditBalance == 0` のときのみ `1_000_000` を設定（冪等）
- 既に課金で減ったテスト残高を勝手に書き戻さない＝開発者の意図保護

`@ConditionalOnProperty(prefix = "app.bootstrap", name = "enabled", havingValue = "true", matchIfMissing = true)` により prod では走らない既存制御を踏襲。

## 3. 非機能・退行防止

- A は SerpAPI キー設定時の挙動を一切変えない（条件分岐の追加のみ）
- B はクレジット残高 > 0 の workspace の動作を変えない（== 0 のときだけ動作）
- DB スキーマ・JSON 契約・外部 API 不変

## 4. 受け入れ基準（オーディター監査項目）

1. `./mvnw test` 既存テスト退行ゼロ
2. **A の新規テスト** `AsyncSgeMeasurementServiceDegradeTest`：
   - SerpAPI キー空 → `insertSgeResult` が **クエリ数ぶん** 呼ばれる（`mentioned=false, mentionCount=0`）
   - SerpAPI キー空 → `updateJobStatus(FAILED)` が **呼ばれない**
3. **B の新規テスト** `DataSeederCreditTopupTest`：
   - 初期 0 → seedData 後 1,000,000
   - 残高 500 → seedData 後 500（保護）
4. アーキ規約遵守（@Transactional 適切配置・ThreadLocal 不使用）
5. ADR 作成（A の "降格設計とコードの乖離" を是正した理由を明記）

## 5. スコープ外

- プラン別チケット Seeding の本格設計（Sprint11+ で本格対応）
- SERPAPI_API_KEY の Vault 統合
- credit_balance の課金連動
