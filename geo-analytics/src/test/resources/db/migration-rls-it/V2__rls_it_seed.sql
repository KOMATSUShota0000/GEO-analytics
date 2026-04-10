INSERT INTO organizations (id, name, plan_id, credit_balance, billing_cycle_anchor, created_at, updated_at, deleted_at)
VALUES ('11111111-1111-1111-1111-111111111101', 'Org A', 'STANDARD', 0, now(), now(), now(), NULL),
       ('22222222-2222-2222-2222-222222222202', 'Org B', 'STANDARD', 0, now(), now(), now(), NULL);

INSERT INTO tenants (id, organization_id, name, created_at, updated_at, deleted_at)
VALUES ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaa1', '11111111-1111-1111-1111-111111111101', 'Tenant A1', now(), now(), NULL),
       ('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbb1', '22222222-2222-2222-2222-222222222202', 'Tenant B1', now(), now(), NULL);

INSERT INTO organization_users (id, organization_id, email, password_hash, role, created_at, updated_at, deleted_at)
VALUES
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa01', '11111111-1111-1111-1111-111111111101', 'org-a-admin@test.local',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'ADMIN'::user_role, now(), now(), NULL),
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa02', '11111111-1111-1111-1111-111111111101', 'org-a-member@test.local',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'MEMBER'::user_role, now(), now(), NULL),
    ('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaa03', '11111111-1111-1111-1111-111111111101', 'org-a-viewer@test.local',
     '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy', 'VIEWER'::user_role, now(), now(), NULL);
