-- ---------------------------------------------------------------------------
-- V16: One extended profile per member.
--
-- member_ext.member_id carried only a non-unique index, so nothing prevented two
-- extended-profile rows for the same member -- and then "what is this member's
-- blood group" has two answers with no way to tell which is correct.
--
-- WHY THIS IS CONDITIONAL
-- The constraint was first applied by hand directly against the development
-- database, which left Flyway's history describing a schema that no longer
-- matched reality. A plain ALTER here would fail on that database with
-- "Duplicate key name", while omitting it would leave every freshly built
-- database without the constraint.
--
-- Checking information_schema first makes the migration idempotent: a no-op
-- where the index already exists, and a real change everywhere else. Both end up
-- at the same schema, which is the point.
--
-- Going forward, schema changes belong in a migration rather than being applied
-- directly, so the two cannot drift apart again.
-- ---------------------------------------------------------------------------

SET @index_exists := (SELECT COUNT(*)
                      FROM information_schema.STATISTICS
                      WHERE TABLE_SCHEMA = DATABASE()
                        AND TABLE_NAME = 'member_ext'
                        AND INDEX_NAME = 'uk_member_ext_member');

SET @statement := IF(@index_exists = 0,
                     'ALTER TABLE member_ext ADD UNIQUE KEY uk_member_ext_member (member_id)',
                     'DO 0');

PREPARE apply_unique_index FROM @statement;
EXECUTE apply_unique_index;
DEALLOCATE PREPARE apply_unique_index;
