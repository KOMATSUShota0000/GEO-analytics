-- 経時スナップショットがどのスコア計算モデルで算出されたかを記録する（V13 Sprint4a-3）。
-- 既存行は NULL（読み出し時に旧モデル V12_PWIM として扱う）。新規行から V13_GEO4AXIS を記録し、
-- 経時グラフで V12→V13 の切替点を描けるようにする（バージョン併記移行・ADR-023/027）。
ALTER TABLE geo_asset_snapshots
    ADD COLUMN IF NOT EXISTS calculation_version VARCHAR(32);
