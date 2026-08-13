-- ---------------------------------------------------------------------------
-- V6: The six roles.
--
-- SAAS roles are held in saas_user (platform-wide, no church).
-- APP  roles are held in member    (scoped to that member's church).
--
-- Codes are used as string literals in security checks, so casing matters and
-- must stay stable. `AppAdmin` is normalised from the spec's `APPAdmin`, whose
-- siblings were written `AppSA` and `AppUser`.
-- ---------------------------------------------------------------------------

INSERT INTO role (uuid, role_code, role_name, role_level, system_defined, description) VALUES
    ('a0000000-0000-4000-8000-000000000001', 'SaaSSAdmin', 'Platform Super Admin', 'SAAS', 1,
     'Full control across every church on the platform.'),
    ('a0000000-0000-4000-8000-000000000002', 'SaaSAdmin',  'Platform Admin',       'SAAS', 1,
     'Onboards and edits churches and users across the platform. No delete.'),
    ('a0000000-0000-4000-8000-000000000003', 'SaaSUser',   'Platform Support',     'SAAS', 1,
     'Read-only visibility across the platform.'),
    ('a0000000-0000-4000-8000-000000000004', 'AppSA',      'Church Super Admin',   'APP',  1,
     'Full control within one church, e.g. the Parish Priest.'),
    ('a0000000-0000-4000-8000-000000000005', 'AppAdmin',   'Church Admin',         'APP',  1,
     'Day-to-day administration within one church, e.g. the secretary.'),
    ('a0000000-0000-4000-8000-000000000006', 'AppUser',    'Church User',          'APP',  1,
     'Read-only within one church, e.g. an Anbiyam animator or ordinary member.');
