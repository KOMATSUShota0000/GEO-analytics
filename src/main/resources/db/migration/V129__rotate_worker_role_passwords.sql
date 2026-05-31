-- Why: V3 はリリース済みマイグレーションのため改変不可（SQL本文を変えると checksum が変わり
--      既存環境の validate が失敗して起動不能になる）。公開リポから弱い固定パスワードを排除し、
--      新規環境でもロール側を環境変数由来の強い値で設定するため、パスワードのみここで更新する。
-- Why: 権限（GRANT / BYPASSRLS / statement_timeout）は V3 で設定済みのため再設定しない。
-- Why: パスワードは Flyway placeholder（spring.flyway.placeholders 経由で環境変数を注入）で展開する。
--      checksum は placeholder 置換前のファイル本文で計算されるため、環境ごとに値が違っても不変。
ALTER ROLE api_worker   WITH PASSWORD '${api_worker_password}';
ALTER ROLE batch_worker WITH PASSWORD '${batch_worker_password}';
