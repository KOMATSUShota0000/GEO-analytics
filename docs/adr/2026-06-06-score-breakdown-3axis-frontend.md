# ADR-028: スコア内訳フロントの3軸固定表示化（ADR-019 業種別再配分の廃止）

## 日付
2026-06-06

## 状況

Sprint4a-1（ADR-025）でバックエンドの `web/dto/ScoreBreakdown` に V13_GEO4AXIS の新3軸（`content_total` 0-50 / `technical_total` 0-20 / `authority_total` 0-30）と権威小計（第三者中核・ローカルMEOサブ・Wikipedia/KGボーナス）・`calculation_version` が露出済みになった。

一方フロント `GeoScoreBreakdown.tsx` は旧モデルのまま残っていた:

- 旧3軸「AI監査(0-50) / MEOトラスト(0-25) / 機械可読性(0-25)」をバー表示。
- ADR-019 の業種別再配分（非地域業種は MEO を非表示にし AI監査×1.2=最大60・機械可読性×1.6=最大40へ再配分）をフロント側で再計算して表示。

結果、**総合点はV13（権威軸込み）・内訳バーは旧モデル**という不整合が画面上に出ていた（合計が合わない）。

## 決定

`GeoScoreBreakdown.tsx` を新3軸固定表示へ刷新し、フロント側の業種別再配分ロジックを撤去する。

- 表示軸を「コンテンツ素地(0-50) / 技術素地(0-20) / 権威・エンティティ認知(0-30)」の3軸固定に変更。`breakdown.contentTotal / technicalTotal / authorityTotal` を直接バインドする。
- 権威軸の下に内訳サブ行を控えめに展開（`SubRow`）。**3サブ行すべて「値>0のときのみ表示」で統一**（第三者言及の広がり0-20、ローカル評判MEOサブ0-10、Wikipedia/ナレッジグラフ0-10＝Sprint5まで常に非表示）。権威=core+local+bonus なので権威軸>0なら必ず少なくとも1行は残り、旧BE併送・権威0の過渡期に「0.0/20」の空行が出るのを防ぐ（監査指摘を反映）。
- ADR-019 由来の `isNonLocalIndustry` 判定・`AI_WEIGHT_NON_LOCAL`/`MACHINE_WEIGHT_NON_LOCAL` 再配分定数・MEO非表示分岐を**全削除**（Shadow実装を残さない）。
- `industryMode` prop は呼び出し側（`JobAnalysisPage`）の互換のため受け取りのみ維持し、表示計算には使わない。
- 型側 `types/analysis.ts`：`ScoreBreakdown` に新7フィールドを追加し、`parseScoreBreakdown` で `content_total` 等を読む。旧バックエンド併送・未提供時の後方互換として、新フィールドが無ければ旧値から導出（content←aiAuditTotal, technical←machineReadabilityTotal×20/25, authority系←0）。
- `PublicDemoPage` のサンプル内訳に新3軸値（content38.5+technical16.0+authority12.5=67.0）を付与。

## 理由

- V13 では MEO 軸はすでに権威軸へ昇華されており（ADR-023）、ADR-019 が解こうとした「非地域業種の天井25%減」問題は**権威軸の導入そのもので解消済み**。フロントで業種別再配分を続ける必要がなくなった。
- 内訳と総合点の単一ソース化：バックエンドが算出した3軸値をフロントは表示するだけにすることで、二重計算による不整合を構造的に排除する。
- 権威小計をサブ行で見せることで「なぜ権威が低い/高いのか」の説明力（提案価値）を保ちつつ、スコア本体の軸は3つに保つ。
- ローカルMEOサブ・KGボーナスは値0のとき非表示にし、非地域業種でも破綻なく自然な見た目になる。

## 結果

- 総合点と内訳バーが V13 で整合する。全業種で3軸固定表示となり、業種設定による表示分岐が消えた（運用上の認知負荷減）。
- ADR-019 の**フロント表示部分は本ADRで置換・廃止**。バックエンドの業種別ロジック（権威軸内のローカルMEOサブ算入可否）は引き続き有効で、本変更はあくまで表示層。
- `vite build` 緑（3304 modules, built）。`industryMode` prop は当面残置（将来の完全撤去は cleanup スプリント候補）。
- 後方互換パーサにより、旧バックエンド応答でも新フィールドを旧値から導出して表示が壊れない。
