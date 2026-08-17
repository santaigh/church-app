-- ---------------------------------------------------------------------------
-- V22 (dev only): put the right kind of value in family_role.
--
-- Curated per person rather than inferred. Gender and date of birth would allow
-- a rule -- opposite gender near the head's age is probably the spouse -- but a
-- guess written into a database stops looking like a guess very quickly.
--
-- HEAD follows family.head_member_id, which was already correct. Note family 6:
-- its recorded head is Amala, not the older Sebastian. The data is followed as
-- it stands rather than second-guessed.
--
-- The occupations move to member_ext.occupation, where they always belonged.
-- ANIMATOR and PRIEST are simply dropped: anbiyam.head_member_id and the
-- parish_priest table already record both, properly. SECRETARY is dropped too --
-- there is no parish-duty field, and one is not wanted for now.
-- ---------------------------------------------------------------------------

-- Everyone a family points at is that family's head.
UPDATE member m
    JOIN family f ON f.head_member_id = m.id
SET m.family_role = 'HEAD';

-- The remaining adults in these households are spouses; Joseph is a child.
UPDATE member SET family_role = 'SPOUSE' WHERE id IN (2, 5, 8, 10);
UPDATE member SET family_role = 'CHILD' WHERE id IN (3);

-- Anything still holding a parish duty is cleared: those are not family roles.
UPDATE member
SET family_role = NULL
WHERE family_role NOT IN ('HEAD', 'SPOUSE', 'CHILD', 'FATHER', 'MOTHER')
   OR family_role IS NULL;

-- Occupations to where they belong. member_ext already exists for some members
-- and not others, so upsert on the unique member_id key added by V16.
INSERT INTO member_ext (uuid, church_id, family_id, member_id, occupation)
SELECT UUID(), m.church_id, m.family_id, m.id,
       CASE m.id WHEN 3 THEN 'Student' WHEN 5 THEN 'Homemaker' WHEN 11 THEN 'Teacher' END
FROM member m
WHERE m.id IN (3, 5, 11)
ON DUPLICATE KEY UPDATE occupation = VALUES(occupation);
