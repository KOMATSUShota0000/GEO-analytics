UPDATE audit_histories SET ai_citation_position = NULL;
UPDATE job_competitor_scores SET ai_citation_position = NULL;
UPDATE audit_histories_aud SET ai_citation_position = NULL;

UPDATE project_keywords SET preferred_engine = 'AI_OVERVIEW';
UPDATE project_keywords_aud SET preferred_engine = 'AI_OVERVIEW';
