-- ---------------------------------------------------------------------------
-- V21: member.catagory becomes member.family_role.
--
-- Two problems in one column. The name was misspelled, and the values were the
-- wrong kind of thing entirely: the seed had written PRIEST, SECRETARY,
-- ANIMATOR, STUDENT, HOMEMAKER and TEACHER -- parish duties and occupations,
-- none of which is a position in a family.
--
-- The column now holds a FamilyRole: HEAD, SPOUSE, CHILD, FATHER or MOTHER,
-- where FATHER and MOTHER are relative to the head -- a parent of the head or
-- of the spouse, living in the household. That is why there is no limit of one
-- each.
--
-- Nullable, because a member's position in the family may not be known yet.
-- The values themselves are corrected in the seed: only churchnew has members,
-- and the correction is curated per person rather than guessed by a rule.
-- ---------------------------------------------------------------------------

ALTER TABLE member
    CHANGE COLUMN catagory family_role VARCHAR(20) NULL COMMENT 'FamilyRole enum name';
