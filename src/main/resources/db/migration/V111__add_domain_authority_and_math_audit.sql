-- Phase 1.2: global domain authority cache (distinct from existing rag_domain_rules)
CREATE TABLE public.domain_authority_cache (
    id UUID PRIMARY KEY,
    "domain" TEXT NOT NULL,
    authority_tier VARCHAR(32) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uq_domain_authority_cache_domain UNIQUE ("domain")
);

-- Phase 1.2: append-only WORM-style math debate audit (JSON payload; no updated_at)
CREATE TABLE public.math_debate_audit_events (
    id UUID PRIMARY KEY,
    target_id UUID NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    audit_data JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_math_debate_audit_events_target_created
    ON public.math_debate_audit_events (target_id, created_at);

GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.domain_authority_cache TO api_worker, batch_worker;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.math_debate_audit_events TO api_worker, batch_worker;
