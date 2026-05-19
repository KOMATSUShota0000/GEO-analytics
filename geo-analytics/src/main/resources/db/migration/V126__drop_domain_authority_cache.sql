-- GEO purification: domain_authority is a SEO backlink-evaluation remnant unrelated to LLM visibility.
-- The table was unused dead code (no service/repository references after entity removal).
-- The UNIQUE constraint and table GRANTs drop automatically with the table; no RLS policy was ever defined for it.
DROP TABLE IF EXISTS public.domain_authority_cache;
