# ADR-089: 公開デモを GitHub Pages に置く

## 日付

2026-09-30

## 状況

- 公開デモページ（`/demo`）と公開プランページ（`/plans`）は、ログイン不要でサーバーも使わない画面として作ってあった。しかしアプリをどこにも公開していないため、見るには手元でアプリを起動するしかなかった
- 就職活動で見せるリポジトリとして、README の URL を踏めば誰でもデモを見られるようにしたい（#187、2026-09-29 オーナー確定）
- アプリ全体の公開は今はしない。見た人が触るにはアカウントの配り方が要り、サーバー・DB・メールの固定費と、解析ごとの従量課金がかかるため（#187）
- リポジトリは公開設定なので、GitHub Pages が無料で使える

## 決定

### 1. 公開版は画面のファイルだけを GitHub Pages に置く

`vite build --mode pages` で公開版を作り、GitHub Actions（`.github/workflows/pages.yml`）で配置する。main に画面の変更（`frontend/**`）が入るたびに作り直す。サーバーは置かない。

### 2. 公開版だけ、置き場所をリポジトリ名の下にする

GitHub Pages の URL は `https://<ユーザー名>.github.io/<リポジトリ名>/` になる。公開版のビルドのときだけ Vite の `base` をそこに合わせ、React Router の `basename` を `import.meta.env.BASE_URL` からとる。置き場所は `actions/configure-pages` の `base_path` から受け取り、無ければ `/GEO-analytics` にする。普段の開発とビルドでは `base` が `/` のままなので、何も変わらない。

### 3. `/demo` を直接開けるよう、`index.html` を `404.html` としても置く

GitHub Pages は、画面側で道順を決めるアプリの URL（`/GEO-analytics/demo` など）を知らないので 404 を返す。そのとき `404.html` を返す仕組みを使い、中身を `index.html` と同じにして、そこから画面を立ち上げる（`build:pages` の中でコピーする）。

### 4. 公開版で出す画面は `/demo` と `/plans` だけにする

公開版（`IS_PUBLIC_SITE`、`frontend/src/publicSite.ts`）では、この2つ以外の URL をデモへ送る。ログインや解析の画面は API が無いと動かないため出さない。

### 5. 公開版では申し込みのボタンを押せないようにする

公開プランページの「このプランで始める」は、ログイン画面へ進む。公開版ではログインできないので、ボタンを「デモ版では申し込めません」にして押せないようにし、下の説明も「このページは紹介用のデモ版です。実際のお申し込みはできません。」に変える。

## 理由

- **GitHub Pages を選んだ理由**: 公開リポジトリなら無料で、GitHub Actions だけで配置まで済む。別のホスティングのアカウントを作らずに済む
- **`404.html` にした理由**: GitHub Pages には、URL を書き換えて `index.html` を返す設定が無い。`#` 付きの URL（HashRouter）にすれば 404 は避けられるが、普段のアプリと道順の作りが分かれ、URL も見慣れない形になる
- **公開版を別のモードで作る理由**: 公開版だけの違い（置き場所・出す画面・ボタン）を1か所の判定にまとめ、普段のアプリの動きを変えないため

## 結果

| 区分 | ファイル |
|---|---|
| 新規 | `.github/workflows/pages.yml`、`frontend/src/publicSite.ts` |
| 変更 | `frontend/vite.config.ts`（公開版の `base`）、`frontend/src/main.tsx`（`basename`）、`frontend/src/App.tsx`（公開版の道順）、`frontend/src/pages/PublicPlansPage.tsx`（申し込みボタン）、`frontend/package.json`（`build:pages`） |

- 普段のビルド（`npm run build`）の出力は変わらない（参照先は `/assets/…` のまま）
- 公開版のビルドの参照先は `/GEO-analytics/assets/…` になる

## 申し送り

- 配置を動かす前に、リポジトリの Settings → Pages で公開元を「GitHub Actions」にする必要がある。有効になっていないと、`actions/configure-pages` のところで配置が止まる
- 公開プランページの料金表には、まだ無い機能（ChatGPT・Claude での測定）が書かれている。公開すると誰でも読めるようになるので、表記の扱い（#206）を決めてから公開するのが望ましい
- デモページの中身を本番と同じ見せ方にするのは DEMO-1（#188）。公開版は main に入るたびに作り直されるので、DEMO-1 が入れば自動で置き換わる
