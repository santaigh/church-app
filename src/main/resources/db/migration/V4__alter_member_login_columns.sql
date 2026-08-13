-- ---------------------------------------------------------------------------
-- V4: Give `member` what login needs.
--
--   password_flag        0 = still on the system default, 1 = user's own password
--   failed_attempt_count / locked_at   admin-unlock lockout (no timed expiry)
--   last_login_at, password_changed_at audit of the account's own history
--
-- The UNIQUE keys on email and mobile make them usable as login identifiers.
-- MySQL allows unlimited NULLs in a UNIQUE index, so members with no contact
-- details are unaffected -- they simply cannot log in, which is intended.
-- ---------------------------------------------------------------------------

ALTER TABLE member
    ADD COLUMN password_flag TINYINT(1) NOT NULL DEFAULT 0 AFTER member_password,
    ADD COLUMN failed_attempt_count INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_at DATETIME NULL,
    ADD COLUMN last_login_at DATETIME NULL,
    ADD COLUMN password_changed_at DATETIME NULL,
    ADD UNIQUE KEY uk_member_email (email),
    ADD UNIQUE KEY uk_member_mobile (mobile),
    ADD CONSTRAINT fk_member_role_id FOREIGN KEY (role_id) REFERENCES role (id),
    ADD CONSTRAINT fk_member_family_id FOREIGN KEY (family_id) REFERENCES family (id);
