ALTER TABLE audit_histories
    ADD CONSTRAINT chk_audit_histories_ai_citation_position_geo
    CHECK (ai_citation_position IS NULL OR ai_citation_position >= 1);

ALTER TABLE job_competitor_scores
    ADD CONSTRAINT chk_job_competitor_scores_ai_citation_position_geo
    CHECK (ai_citation_position IS NULL OR ai_citation_position >= 1);