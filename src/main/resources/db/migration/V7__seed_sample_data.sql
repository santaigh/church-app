-- ---------------------------------------------------------------------------
-- V7: Sample data for development.
--
-- Three churches so tenant scoping can actually be exercised -- a bug where one
-- parish sees another's members is invisible with only one church in the table.
--
-- Every account's password is the default 'Welcome123$' stored as a BCrypt hash
-- with password_flag = 0, so the first login forces a password change. The hash
-- is shared across the sample rows because the password is the same; it was
-- generated with strength 12 and verified before being pasted here.
--
-- Anbiyam names are in Tamil on purpose: they prove the utf8mb4 column and
-- connection charset really do round-trip Tamil script.
--
-- DEVELOPMENT DATA. Do not apply this migration to a production database.
-- ---------------------------------------------------------------------------

-- --------------------------------------------------------------- churches
INSERT INTO church (id, uuid, church_name, category_id, location, diocese, parish_priest,
                    address_line1, city, district, state, country, pincode, phone, email,
                    established_date, record_status) VALUES
    (1, 'c0000000-0000-4000-8000-000000000001', 'St. Mary''s Cathedral', 'PARISH', 'Chennai',
     'Archdiocese of Madras-Mylapore', 'Fr. Antony Raj',
     '1, Santhome High Road', 'Chennai', 'Chennai', 'Tamil Nadu', 'India', '600004',
     '+914424985455', 'office@stmarys-chennai.org', '1904-06-12', 'ACTIVE'),
    (2, 'c0000000-0000-4000-8000-000000000002', 'St. Joseph''s Church', 'PARISH', 'Madurai',
     'Archdiocese of Madurai', 'Fr. Xavier Britto',
     '14, East Marret Street', 'Madurai', 'Madurai', 'Tamil Nadu', 'India', '625001',
     '+914522345678', 'office@stjosephs-madurai.org', '1921-03-19', 'ACTIVE'),
    (3, 'c0000000-0000-4000-8000-000000000003', 'Our Lady of Lourdes', 'SUBSTATION', 'Tiruchirappalli',
     'Diocese of Tiruchirappalli', 'Fr. Sebastian Rayan',
     '8, College Road', 'Tiruchirappalli', 'Tiruchirappalli', 'Tamil Nadu', 'India', '620001',
     '+914312701234', 'office@lourdes-trichy.org', '1896-02-11', 'ACTIVE');

-- ---------------------------------------------------------- parish priests
-- One historical and one current posting for the cathedral; active_flag = 1
-- marks whoever is serving now.
INSERT INTO parish_priest (uuid, priest_name, priest_last_place, from_date, to_date,
                           active_flag, church_id) VALUES
    ('p0000000-0000-4000-8000-000000000001', 'Fr. Gnanaprakasam', 'Vellore',
     '2016-06-01 00:00:00', '2021-05-31 00:00:00', 0, 1),
    ('p0000000-0000-4000-8000-000000000002', 'Fr. Antony Raj', 'Kanchipuram',
     '2021-06-01 00:00:00', '2027-05-31 00:00:00', 1, 1),
    ('p0000000-0000-4000-8000-000000000003', 'Fr. Xavier Britto', 'Dindigul',
     '2022-06-01 00:00:00', '2027-05-31 00:00:00', 1, 2),
    ('p0000000-0000-4000-8000-000000000004', 'Fr. Sebastian Rayan', 'Thanjavur',
     '2023-06-01 00:00:00', '2028-05-31 00:00:00', 1, 3);

-- ---------------------------------------------------------------- anbiyams
INSERT INTO anbiyam (id, uuid, church_id, anbiyam_name, area_description, active_flag, record_status) VALUES
    (1, 'b0000000-0000-4000-8000-000000000001', 1, 'புனித அந்தோணியார் அன்பியம்',  'Santhome, streets 1-12',    1, 'ACTIVE'),
    (2, 'b0000000-0000-4000-8000-000000000002', 1, 'புனித சூசையப்பர் அன்பியம்',   'Mylapore East',             1, 'ACTIVE'),
    (3, 'b0000000-0000-4000-8000-000000000003', 1, 'அன்னை வேளாங்கண்ணி அன்பியம்', 'Foreshore Estate',          1, 'ACTIVE'),
    (4, 'b0000000-0000-4000-8000-000000000004', 2, 'புனித சவேரியார் அன்பியம்',    'East Marret, wards 3-5',    1, 'ACTIVE'),
    (5, 'b0000000-0000-4000-8000-000000000005', 2, 'திருஇருதய அன்பியம்',          'Tallakulam',                1, 'ACTIVE'),
    (6, 'b0000000-0000-4000-8000-000000000006', 3, 'லூர்து அன்னை அன்பியம்',       'College Road and Cantonment', 1, 'ACTIVE');

-- ---------------------------------------------------------------- families
INSERT INTO family (id, uuid, church_id, anbiyam_id, family_code, family_name,
                    address_line1, city, district, state, pincode, phone, record_status) VALUES
    (1, 'f0000000-0000-4000-8000-000000000001', 1, 1, 'FAM-001', 'Arulraj Family',
     '22, Leith Castle Street', 'Chennai', 'Chennai', 'Tamil Nadu', '600028', '+919840011001', 'ACTIVE'),
    (2, 'f0000000-0000-4000-8000-000000000002', 1, 1, 'FAM-002', 'Devasagayam Family',
     '5, Beach Road', 'Chennai', 'Chennai', 'Tamil Nadu', '600028', '+919840011002', 'ACTIVE'),
    (3, 'f0000000-0000-4000-8000-000000000003', 1, 2, 'FAM-003', 'Fernando Family',
     '17, Luz Church Road', 'Chennai', 'Chennai', 'Tamil Nadu', '600004', '+919840011003', 'ACTIVE'),
    (4, 'f0000000-0000-4000-8000-000000000004', 2, 4, 'FAM-001', 'Pandian Family',
     '3, West Veli Street', 'Madurai', 'Madurai', 'Tamil Nadu', '625001', '+919840022001', 'ACTIVE'),
    (5, 'f0000000-0000-4000-8000-000000000005', 2, 5, 'FAM-002', 'Selvaraj Family',
     '41, Tallakulam Main Road', 'Madurai', 'Madurai', 'Tamil Nadu', '625002', '+919840022002', 'ACTIVE'),
    (6, 'f0000000-0000-4000-8000-000000000006', 3, 6, 'FAM-001', 'Irudayaraj Family',
     '9, Cantonment', 'Tiruchirappalli', 'Tiruchirappalli', 'Tamil Nadu', '620001', '+919840033001', 'ACTIVE');

-- ----------------------------------------------------------------- members
-- Password for every seeded account is 'Welcome123$' (BCrypt, strength 12),
-- password_flag = 0 so the first login is forced through a password change.
--
-- Members with NULL email and NULL mobile are intentional: they demonstrate a
-- parishioner who has a record and a role but no way to sign in.
INSERT INTO member (id, uuid, church_id, family_id, anbiyam_id, role_id,
                    first_name, middle_name, last_name, member_password, password_flag,
                    gender, date_of_birth, mobile, email, catagory, record_status) VALUES
    -- St. Mary's Cathedral (church 1)
    (1, 'm0000000-0000-4000-8000-000000000001', 1, 1, 1,
     (SELECT id FROM role WHERE role_code = 'AppSA'),
     'Antony', NULL, 'Raj', '$2a$12$OUQ7SO9sZsTTAqcifVIoh.HfF3wC9lnaIoKOistiHJ5QjLuU5GHOq', 0,
     'MALE', '1968-04-02', '+919840100001', 'antony.raj@stmarys-chennai.org', 'PRIEST', 'ACTIVE'),
    (2, 'm0000000-0000-4000-8000-000000000002', 1, 1, 1,
     (SELECT id FROM role WHERE role_code = 'AppAdmin'),
     'Mary', 'Grace', 'Arulraj', '$2a$12$OUQ7SO9sZsTTAqcifVIoh.HfF3wC9lnaIoKOistiHJ5QjLuU5GHOq', 0,
     'FEMALE', '1979-11-17', '+919840100002', 'mary.arulraj@example.com', 'SECRETARY', 'ACTIVE'),
    (3, 'm0000000-0000-4000-8000-000000000003', 1, 1, 1,
     (SELECT id FROM role WHERE role_code = 'AppUser'),
     'Joseph', NULL, 'Arulraj', '$2a$12$OUQ7SO9sZsTTAqcifVIoh.HfF3wC9lnaIoKOistiHJ5QjLuU5GHOq', 0,
     'MALE', '2009-01-23', NULL, NULL, 'STUDENT', 'ACTIVE'),
    (4, 'm0000000-0000-4000-8000-000000000004', 1, 2, 1,
     (SELECT id FROM role WHERE role_code = 'AppUser'),
     'Stephen', NULL, 'Devasagayam', '$2a$12$OUQ7SO9sZsTTAqcifVIoh.HfF3wC9lnaIoKOistiHJ5QjLuU5GHOq', 0,
     'MALE', '1985-07-30', '+919840100004', 'stephen.d@example.com', 'ANIMATOR', 'ACTIVE'),
    (5, 'm0000000-0000-4000-8000-000000000005', 1, 2, 1,
     (SELECT id FROM role WHERE role_code = 'AppUser'),
     'Agnes', NULL, 'Devasagayam', '$2a$12$OUQ7SO9sZsTTAqcifVIoh.HfF3wC9lnaIoKOistiHJ5QjLuU5GHOq', 0,
     'FEMALE', '1988-02-14', NULL, NULL, 'HOMEMAKER', 'ACTIVE'),
    (6, 'm0000000-0000-4000-8000-000000000006', 1, 3, 2,
     (SELECT id FROM role WHERE role_code = 'AppUser'),
     'Peter', NULL, 'Fernando', '$2a$12$OUQ7SO9sZsTTAqcifVIoh.HfF3wC9lnaIoKOistiHJ5QjLuU5GHOq', 0,
     'MALE', '1972-09-05', '+919840100006', 'peter.fernando@example.com', 'ANIMATOR', 'ACTIVE'),
    -- St. Joseph's Church (church 2)
    (7, 'm0000000-0000-4000-8000-000000000007', 2, 4, 4,
     (SELECT id FROM role WHERE role_code = 'AppSA'),
     'Xavier', NULL, 'Britto', '$2a$12$OUQ7SO9sZsTTAqcifVIoh.HfF3wC9lnaIoKOistiHJ5QjLuU5GHOq', 0,
     'MALE', '1971-12-03', '+919840200001', 'xavier.britto@stjosephs-madurai.org', 'PRIEST', 'ACTIVE'),
    (8, 'm0000000-0000-4000-8000-000000000008', 2, 4, 4,
     (SELECT id FROM role WHERE role_code = 'AppAdmin'),
     'Lourdes', NULL, 'Pandian', '$2a$12$OUQ7SO9sZsTTAqcifVIoh.HfF3wC9lnaIoKOistiHJ5QjLuU5GHOq', 0,
     'FEMALE', '1983-05-21', '+919840200002', 'lourdes.pandian@example.com', 'SECRETARY', 'ACTIVE'),
    (9, 'm0000000-0000-4000-8000-000000000009', 2, 5, 5,
     (SELECT id FROM role WHERE role_code = 'AppUser'),
     'Arokia', NULL, 'Selvaraj', '$2a$12$OUQ7SO9sZsTTAqcifVIoh.HfF3wC9lnaIoKOistiHJ5QjLuU5GHOq', 0,
     'MALE', '1990-08-11', '+919840200003', 'arokia.selvaraj@example.com', 'ANIMATOR', 'ACTIVE'),
    -- Our Lady of Lourdes (church 3)
    (10, 'm0000000-0000-4000-8000-000000000010', 3, 6, 6,
     (SELECT id FROM role WHERE role_code = 'AppSA'),
     'Sebastian', NULL, 'Rayan', '$2a$12$OUQ7SO9sZsTTAqcifVIoh.HfF3wC9lnaIoKOistiHJ5QjLuU5GHOq', 0,
     'MALE', '1975-10-28', '+919840300001', 'sebastian.rayan@lourdes-trichy.org', 'PRIEST', 'ACTIVE'),
    (11, 'm0000000-0000-4000-8000-000000000011', 3, 6, 6,
     (SELECT id FROM role WHERE role_code = 'AppUser'),
     'Amala', NULL, 'Irudayaraj', '$2a$12$OUQ7SO9sZsTTAqcifVIoh.HfF3wC9lnaIoKOistiHJ5QjLuU5GHOq', 0,
     'FEMALE', '1994-03-09', '+919840300002', 'amala.irudayaraj@example.com', 'TEACHER', 'ACTIVE');

-- Family heads, set after the members exist.
UPDATE family SET head_member_id = 1 WHERE id = 1;
UPDATE family SET head_member_id = 4 WHERE id = 2;
UPDATE family SET head_member_id = 6 WHERE id = 3;
UPDATE family SET head_member_id = 7 WHERE id = 4;
UPDATE family SET head_member_id = 9 WHERE id = 5;
UPDATE family SET head_member_id = 11 WHERE id = 6;

-- Anbiyam animators.
UPDATE anbiyam SET head_member_id = 4 WHERE id = 1;
UPDATE anbiyam SET head_member_id = 6 WHERE id = 2;
UPDATE anbiyam SET head_member_id = 9 WHERE id = 5;
UPDATE anbiyam SET head_member_id = 11 WHERE id = 6;

-- ------------------------------------------------------------- member_ext
-- A couple of rows only, to show the extended profile is wired up.
INSERT INTO member_ext (uuid, church_id, family_id, member_id, blood_group, marital_status,
                        address_line1, city, district, state, pincode, occupation, education,
                        native_place, baptism_date, baptism_place, holy_communion_date,
                        confirmation_date, marriage_date, record_status) VALUES
    ('e0000000-0000-4000-8000-000000000001', 1, 1, 2, 'O+', 'MARRIED',
     '22, Leith Castle Street', 'Chennai', 'Chennai', 'Tamil Nadu', '600028',
     'School Teacher', 'M.A., B.Ed.', 'Nagercoil',
     '1979-12-09', 'St. Mary''s Cathedral', '1988-05-15', '1993-04-18', '2005-09-12', 'ACTIVE'),
    ('e0000000-0000-4000-8000-000000000002', 1, 2, 4, 'B+', 'MARRIED',
     '5, Beach Road', 'Chennai', 'Chennai', 'Tamil Nadu', '600028',
     'Bank Officer', 'B.Com.', 'Thoothukudi',
     '1985-09-22', 'Our Lady of Snows, Thoothukudi', '1994-05-08', '1999-04-11', '2012-01-29', 'ACTIVE');

-- -------------------------------------------------------------- saas users
-- One account per platform role. No church_id: these reach every church.
INSERT INTO saas_user (uuid, role_id, first_name, last_name, email, mobile,
                       user_password, password_flag, record_status) VALUES
    ('s0000000-0000-4000-8000-000000000001',
     (SELECT id FROM role WHERE role_code = 'SaaSSAdmin'),
     'Platform', 'Super Admin', 'superadmin@churchapp.local', '+919000000001',
     '$2a$12$OUQ7SO9sZsTTAqcifVIoh.HfF3wC9lnaIoKOistiHJ5QjLuU5GHOq', 0, 'ACTIVE'),
    ('s0000000-0000-4000-8000-000000000002',
     (SELECT id FROM role WHERE role_code = 'SaaSAdmin'),
     'Platform', 'Admin', 'admin@churchapp.local', '+919000000002',
     '$2a$12$OUQ7SO9sZsTTAqcifVIoh.HfF3wC9lnaIoKOistiHJ5QjLuU5GHOq', 0, 'ACTIVE'),
    ('s0000000-0000-4000-8000-000000000003',
     (SELECT id FROM role WHERE role_code = 'SaaSUser'),
     'Platform', 'Support', 'support@churchapp.local', '+919000000003',
     '$2a$12$OUQ7SO9sZsTTAqcifVIoh.HfF3wC9lnaIoKOistiHJ5QjLuU5GHOq', 0, 'ACTIVE');
