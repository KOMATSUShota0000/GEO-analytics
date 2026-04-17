DO $$
BEGIN
    CREATE ROLE api_worker WITH LOGIN PASSWORD 'api_worker_pass';
EXCEPTION WHEN duplicate_object THEN
    NULL;
END $$;

DO $$
BEGIN
    CREATE ROLE batch_worker WITH LOGIN PASSWORD 'batch_worker_pass';
EXCEPTION WHEN duplicate_object THEN
    NULL;
END $$;

ALTER ROLE api_worker WITH LOGIN PASSWORD 'api_worker_pass';
ALTER ROLE batch_worker WITH LOGIN PASSWORD 'batch_worker_pass';
ALTER ROLE batch_worker BYPASSRLS;

ALTER ROLE api_worker SET statement_timeout = 10000;
ALTER ROLE batch_worker SET statement_timeout = 0;

GRANT USAGE ON SCHEMA public TO api_worker, batch_worker;

ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO api_worker, batch_worker;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO api_worker, batch_worker;
