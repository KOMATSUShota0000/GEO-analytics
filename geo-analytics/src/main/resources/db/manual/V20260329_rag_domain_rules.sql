CREATE TABLE IF NOT EXISTS rag_domain_rules (
    id UUID PRIMARY KEY,
    host_suffix VARCHAR(255) NOT NULL UNIQUE,
    rule_kind VARCHAR(32) NOT NULL,
    trust_boost DOUBLE PRECISION,
    active BOOLEAN NOT NULL DEFAULT TRUE
);
