-- ---------------------------------------------------------------------------
-- V10: Bring church.category_id in line with the ChurchCategory enum.
--
-- V7 seeded 'PARISH', which is not a value the enum recognises, so those rows
-- would fail to map once Church.category is an enum. The vocabulary is
-- STATION (a church in its own right) and SUBSTATION (an outstation attached
-- to a parent church, via church.parent_church_id).
--
-- Only rows written by V7 are affected; the table held no other data.
-- ---------------------------------------------------------------------------

UPDATE church
SET category_id = 'STATION'
WHERE category_id = 'PARISH';
