# ADR-076: パスワードでのログインを撤去する

## 日付
2026-09-26

## 状況

ログインはメールに届くコードで行えるようになり（ADR-072〜075）、画面からパスワードでのログイン（`POST /api/login`）は呼ばれなくなった（#148）。サーバー側には API・パスワードの照合・パスワードの列が残っていた。`.cursorrules` 10節（Shadow Implementation 禁止）に従い、旧経路を残さない（#149）。

オーナー確定事項:

- ログイン方法はメールに届くコードだけにし、パスワードは撤去する（#144 確定事項1）
- パスワードの列（`organization_users.password_hash`）は削除する（#144 確定事項14）
- リフレッシュ処理にあった「パスワード変更後に古いログインを失効させる」ための予定地（TODO コメントと常に false の判定）を消す（2026-09-26）
- それに連なる失効の仕組み（`CredentialsRevokedException`・エラー応答 `credentials_revoked`・画面の文言）も、使い道がその予定地だけであることを確かめたうえで消す（2026-09-26）
- `AuthService#issueTokens` の説明を「コードでのログインから呼ぶ」に直す（2026-09-26）

## 決定

**パスワードに関わるものをすべて撤去する。**

| 撤去したもの | 場所 |
|---|---|
| `POST /api/login` と入力 | `AuthController#login`、`LoginRequest` |
| パスワードの照合 | `AuthService#login`、`AuthenticationManager` Bean、`JpaUserDetailsService` |
| パスワードのハッシュ化 | `PasswordEncoder` Bean（本体・テスト用設定とも） |
| 開発用の初期パスワード | `DataSeeder` の `SEED_PASSWORD` と、起動時にパスワードを出すログ |
| `/api/login` の例外指定 | `SecurityConfig`（許可・CSRF）、`JwtAuthenticationFilter`、`TenantContextFilter`、`RateLimitFilter` |
| パスワードの列 | V144 で `organization_users.password_hash` を削除。`OrganizationUser` のフィールドも |
| 使われていない処理 | `BatchPersistenceService#findFirstActiveOrgUser` と `OrgUserInfo`（呼び出し元ゼロで `password_hash` を読んでいた） |
| パスワード変更による失効 | リフレッシュ処理の予定地、`CredentialsRevokedException`、`GlobalExceptionHandler` の対応、画面の `credentials_revoked` と文言 |
| フォームログインと Basic 認証 | `SecurityConfig` で無効にした |

## 理由

### フォームログインと Basic 認証も止める

パスワードを確かめる部品（`UserDetailsService` など）を消すと、Spring Boot の `UserDetailsServiceAutoConfiguration` が「ランダムなパスワードの仮ユーザー」を自動で作る。フォームログイン（`formLogin`）と Basic 認証（`httpBasic`）が有効なままだと、その仮ユーザーでパスワードによる認証ができる入口が残る。どちらも無効にし、`UserDetailsServiceAutoConfiguration` 自体も除外した。

### 失効の仕組みは使い道を確かめてから消す

`CredentialsRevokedException` を投げていたのは、リフレッシュ処理にある常に false の判定だけだった。受け取る側は、エラー応答への変換と画面の文言だけで、テスト・手順書・ADR・DB定義には出てこないことを確かめた。予定地を消すと投げる場所が無くなり、使われないコードが残るため一緒に消した。

### テストの置き換え

- `AuthLoginIntegrationTest`（パスワードでのログイン）は消した。コードでのログインは `LoginCodeVerifyIntegrationTest` が確かめている。
- `AuthJwtIntegrationTest` は JWT の扱いを確かめるテストなので、ログインに成功したときと同じ発行処理（`AuthService#issueTokens`）で直接トークンを得る形にした。
- `/api/login` を呼んでもトークンが返らないこと（4xx になること）を、`LoginCodeVerifyIntegrationTest` に足した。

## 結果

- ログインはメールに届くコードだけになった。
- `organization_users` にパスワードの列は無い。元には戻せないが、パスワードを持っていたのは開発用の初期ユーザーだけだった。
- テスト用の初期データ（`src/test/resources/db/migration-rls-it/V5__rls_it_seed.sql`）は `password_hash` を入れているが、列を削除する V144 より前に適用されるので、そのままで動く。
- リフレッシュ処理に残る予定地は、組織の一時停止（`TenantSuspendedException`）の1つだけになった（パスワードとは関係ないので触っていない）。
- 本番でも初期データ投入が動く問題（#152）は変わらない。初期ユーザーはパスワードを持たなくなったので、`bootstrap@example.com` で入るにはそのアドレスのメールを受け取る必要がある。
