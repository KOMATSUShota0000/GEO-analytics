-- 解析完了のお知らせをメールだけにし、宛先を3件まで持てるようにする（#174）。
-- Why: 宛先は3件までの短い並びで、宛先ごとの属性も持たないため、別テーブルではなく配列の列にした（オーナー確定 2026-09-28）。
--      本番の利用者はまだいないため、Slack の列はそのまま削除する。
ALTER TABLE projects ADD COLUMN notification_emails TEXT[] NOT NULL DEFAULT '{}';

UPDATE projects
SET notification_emails = ARRAY[btrim(notification_email)]
WHERE notification_email IS NOT NULL AND btrim(notification_email) <> '';

ALTER TABLE projects
    ADD CONSTRAINT chk_projects_notification_emails_max CHECK (cardinality(notification_emails) <= 3);

ALTER TABLE projects
    DROP COLUMN notification_email,
    DROP COLUMN slack_webhook_url;
