-- ---------------------------------------------------------------------------
-- V8: Address belongs to member_ext, not family.
--
-- V3 gave family its own address columns, which duplicated what member_ext
-- already stores per person: a five-person household would hold the same
-- address six times, and a house move would be six edits that could drift apart.
--
-- family keeps only what is genuinely family-level: the register code, the
-- Anbiyam it belongs to, its head, and a shared phone number.
-- ---------------------------------------------------------------------------

ALTER TABLE family
    DROP COLUMN address_line1,
    DROP COLUMN address_line2,
    DROP COLUMN city,
    DROP COLUMN district,
    DROP COLUMN state,
    DROP COLUMN pincode;
