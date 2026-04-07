-- Phase 9.6 evidence: competitor noun counts; SGE brand mention counts (PostgreSQL).
ALTER TABLE job_competitor_scores ADD COLUMN IF NOT EXISTS noun_count INTEGER NOT NULL DEFAULT 0;
ALTER TABLE sge_results ADD COLUMN IF NOT EXISTS mention_count INTEGER NOT NULL DEFAULT 0;
