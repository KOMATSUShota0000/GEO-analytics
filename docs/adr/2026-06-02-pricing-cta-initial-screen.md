# ADR-016: ログイン後初期画面へのプラン導線設置（A案: 画面直接設置を採用）

## 日付

2026-06-02

## 状況

オーナー要望により、ログイン後の最初の画面から課金（プラン）ページへ遷移できる導線が必要。現状を実コードで確認したところ：

- ログイン後の最初の画面は `/` ルートの `JobCreationPage`（`App.tsx`）。
- `/pricing` ルートと `PricingPage` は実装済み。
- `/pricing` への導線は文脈依存の2箇所のみ（`DebateAdviceTeaserBanner`、`TierDiagnosisCard`）で、**初期画面に常設のプラン導線が無い**。
- `RequireAuthLayout` は認証ゲートのみで `<Outlet/>` を返すだけ＝**全画面共通のヘッダー/ナビが存在しない**ため、導線の配置方法で実装範囲が変わる。

## 決定

**A案（初期画面に直接設置）** を採用。`JobCreationPage` 上部ヘッダー（ロゴ＋ツール名の行）の右端に「プラン・料金」ボタンを設置し、クリックで `/pricing` へ遷移する。

- `frontend/src/pages/JobCreationPage.tsx`
  - `WorkspacePremiumIcon` を import。
  - ヘッダー `Box` に `Button`（`variant="outlined"` / `size="small"` / `startIcon={<WorkspacePremiumIcon />}` / `sx={{ ml: "auto", flexShrink: 0 }}`）を追加し、既存の `useNavigate` で `navigate("/pricing")`。

検討した代替案：

- **B案（共通ヘッダー新設）**: `RequireAuthLayout` に全画面共通ヘッダーを追加し、プラン導線・残クレジット等を常設する。SaaS として本来は筋が良いが、各ページが個別レイアウトを持つ現状で全画面に挿入するとマージン崩れ等の回帰リスクがあり、MVP 最速に逆行するため**見送り**。
- **C案（現状維持）**: 既存の文脈導線で足りるとして新規実装しない。オーナーの明示要望（初期画面の常設導線）を満たさないため**却下**。

## 理由

- **最優先目標（高品質な MVP を最速で完成）** に対し、A案は最小工数で要望を確実に満たす。差分は既存の `Button`・`useNavigate`・MUI 標準アイコンのみで回帰リスクが極小。
- **プロダクトの核③（SaaS グロース＝Pro アップセル誘導）** に対し、ログイン直後はアップセルが最も効く位置。`WorkspacePremiumIcon`（王冠）でプレミアム感を出しつつ `outlined`/`small` で押し付けがましさを抑える。
- A案の実装は将来 B案（共通ヘッダー）へ昇格しても無駄にならない。画面数が増えてナビが必要になった時点で B案へ拡張する方針とする。

## 結果

- 要望（初期画面からのプラン導線）を充足。`JobCreationPage` の他フォームには干渉しない。
- **未コミット**。型チェック（`frontend/` で `npm run build`）はオーナー手動実行で確認が必要。
- 将来 B案（共通ヘッダー）へ移行する場合は、本ボタンをヘッダーへ移設し、残クレジット表示等と統合する。
