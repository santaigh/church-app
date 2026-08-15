-- ---------------------------------------------------------------------------
-- V18 (dev only): one real substation, so the parent/child structure can
-- actually be seen on screen.
--
-- Every church in the sample data had parent_church_id = NULL, so the nested
-- church list would have rendered as a flat list of three and the feature would
-- never have been exercised.
--
-- Deliberately holds nothing: no members, no families, no anbiyams, no priest.
-- That is what a substation is -- people living nearby come here to pray, but
-- they belong to St. Mary's, and St. Mary's priest looks after this chapel.
-- ---------------------------------------------------------------------------

INSERT INTO church (uuid, church_name, parent_church_id, parent_church_name,
                    location, diocese, address_line1, city, district, state,
                    country, pincode, phone, record_status)
VALUES ('c0000000-0000-4000-8000-000000000004',
        'St. Anthony''s Chapel',
        (SELECT id FROM (SELECT id FROM church WHERE church_name = 'St. Mary''s Cathedral') AS s),
        'St. Mary''s Cathedral',
        'Pattinapakkam',
        'Archdiocese of Madras-Mylapore',
        'Beach Road, Pattinapakkam',
        'Chennai', 'Chennai', 'Tamil Nadu', 'India', '600028',
        '+914424001004',
        'ACTIVE');
