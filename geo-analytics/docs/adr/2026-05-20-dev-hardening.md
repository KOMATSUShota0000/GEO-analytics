# ADR-008: dev環境ハードニング（SGE降格の真実装 + dev クレジット潤沢化）

## 日付

2026-05-20

## 状況

オーナーが UI から実機解析を実行した際、以下 2 系統で失敗が連発した。

1. `AsyncSgeMeasurementService:67` が `app.serpapi.api-key` 未設定で `IllegalStateException` を投げ、ジョブ全体を `FAILED` にマーク。一方 `SerpApiKeyStartupCheck.java:14` のコメントは「未設定でも例外は投げず起動を止めない（**プレースホルダ降格は既存設計**）」と謳う＝**設計コメントとコードが乖離**
2. `V1__init_schema.sql:482` のシードで `organizations.credit_balance = 0`。`DataSeeder` がトップアップしないため `CreditVaultService.reserve` がいかなる呼び出しでも `InsufficientCreditException` → AiRubricAudit / GooglePlacesAdapter 等が全滅

## 決定

### A. SGE 降格の真実装

`AsyncSgeMeasurementService.measureSgeForJob()` 入口で SerpAPI キーが空の場合：
- 各クエリに対し `BatchPersistenceService.insertSgeResult(...mentioned=false, mentionCount=0, raw="{}")` を書き込む
- WARN ログ出力
- **例外を投げず正常 return**

キーが設定されていれば従来通りの SerpAPI 呼び出し経路を走る。

### B. dev クレジット潤沢化

`DataSeeder.seedData()` 内で `OrganizationRepository.findByIdForUpdate(orgId)` でデフォルト org を引き、`creditBalance == 0` のときだけ `1_000_000` を投入（冪等）。`@ConditionalOnProperty("app.bootstrap.enabled")` により prod では走らない。

## 理由

- **A の発生源是正の優位**：UI 側で band-aid（"SGE 失敗時のリトライ" など）を貼ると、SoM / 相対評価 / 改善ロードマップなど下流のあらゆる Gemini 解析パイプラインに条件分岐が散ることになる。発生源で降格すれば下流は無変更で恩恵を受ける
- **本番安全性**：本番では SERPAPI_API_KEY 必須運用のため A の分岐は走らない。`SerpApiKeyStartupCheck` の起動時 WARN を「真の降格」に格上げするだけ
- **B の冪等性**：`creditBalance == 0` 時のみトップアップ＝開発者が手で 500 まで使い切ったテスト残高を勝手に書き戻さない。再起動でもガチャ的に増えない
- **band-aid 案を却下した理由**：DB 初期化 SQL を直接 `1,000,000` に書き換えるとマイグレーション履歴が汚れるうえ、prod に流出するリスクが残る

## 結果

- `AsyncSgeMeasurementServiceDegradeTest`（2ケース）追加
- `DataSeederCreditTopupTest`（3ケース）追加
- 全 157 ユニットテスト PASS、退行ゼロ
- 実機解析の dev ブロックが解除（オーナーの再検証はこの後）

## スコープ外

- プラン別チケット Seeding の本格設計（Sprint11+）
- SERPAPI_API_KEY の Vault 統合
- credit_balance の課金連動
