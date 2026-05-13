-- Legacy password/WebAuthn-only table from initial schema; authentication uses organization_users (see user_sessions FK).
-- No inbound foreign keys reference public.users.
DROP TABLE IF EXISTS public.users;
