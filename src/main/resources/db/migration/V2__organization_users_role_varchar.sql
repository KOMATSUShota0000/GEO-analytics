ALTER TABLE organization_users
    ALTER COLUMN role DROP DEFAULT;

ALTER TABLE organization_users
    ALTER COLUMN role TYPE VARCHAR(32) USING (role::text);

ALTER TABLE organization_users
    ALTER COLUMN role SET NOT NULL;

ALTER TABLE organization_users
    ALTER COLUMN role SET DEFAULT 'MEMBER';

DROP TYPE user_role;
