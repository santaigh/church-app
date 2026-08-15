-- ---------------------------------------------------------------------------
-- V19: The clergy serving a parish -- and an end date that may be empty.
--
-- Three roles share this table: PARISH_PRIEST, ASSISTANT_PRIEST and BROTHER (a
-- final-year seminarian, not ordained). Exactly one parish priest serves at a
-- time; assistants and brothers are optional and may be several.
--
-- to_date was NOT NULL, so a priest who has not left yet still needed a date.
-- The sample data shows what that forces: Fr. Antony Raj carried an end of
-- 2027-05-31 that nobody had decided. From here, to_date IS NULL means
-- currently serving, and that is the only definition.
--
-- active_flag answered the same question, which is how two columns end up
-- disagreeing. It is used once below -- it is the only column that currently
-- knows who is serving -- and then dropped.
-- ---------------------------------------------------------------------------

ALTER TABLE parish_priest
    ADD COLUMN clergy_role  VARCHAR(30) NOT NULL DEFAULT 'PARISH_PRIEST'
        COMMENT 'ClergyRole enum name' AFTER church_id,
    ADD COLUMN deleted_flag TINYINT(1)  NOT NULL DEFAULT 0,
    ADD COLUMN version      BIGINT      NOT NULL DEFAULT 0,
    MODIFY COLUMN to_date DATETIME NULL COMMENT 'NULL means currently serving';

-- Everyone still serving loses the invented end date.
UPDATE parish_priest SET to_date = NULL WHERE active_flag = 1;

ALTER TABLE parish_priest DROP COLUMN active_flag;

-- "Who serves this parish now" is the query every screen asks.
CREATE INDEX idx_parish_priest_current
    ON parish_priest (church_id, clergy_role, to_date, deleted_flag);
