ALTER TABLE audit_histories RENAME COLUMN rank_position TO ai_citation_position;
ALTER TABLE job_competitor_scores RENAME COLUMN rank_position TO ai_citation_position;
ALTER TABLE audit_histories_aud RENAME COLUMN rank_position TO ai_citation_position;
