-- ---------------------------------------------------------------------------
-- V15: Put every stored mobile number into one canonical form.
--
-- From here on PhoneNumberNormalizer does this on save, but any number already
-- in the table predates that. Without it the UNIQUE index on mobile is only
-- half-effective: '+919840100001' and '9840100001' are different strings, so the
-- same phone could be registered against two members.
--
-- A no-op on the current sample data, which is already in +91 form. It exists so
-- that importing legacy records cannot leave the column inconsistent.
--
-- The +91 here is a one-off data fix for records created before normalisation,
-- not the application's country setting -- that lives in app.security.default-country-code.
-- ---------------------------------------------------------------------------

-- Strip anything a human typed as decoration.
UPDATE member
SET mobile = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(mobile, ' ', ''), '-', ''), '(', ''), ')', ''), '.', '')
WHERE mobile IS NOT NULL;

UPDATE saas_user
SET mobile = REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(mobile, ' ', ''), '-', ''), '(', ''), ')', ''), '.', '')
WHERE mobile IS NOT NULL;

-- Drop the domestic trunk prefix: 09840100001 -> 9840100001
UPDATE member
SET mobile = SUBSTRING(mobile, 2)
WHERE mobile IS NOT NULL AND mobile LIKE '0%';

UPDATE saas_user
SET mobile = SUBSTRING(mobile, 2)
WHERE mobile IS NOT NULL AND mobile LIKE '0%';

-- Country code typed without the '+': 919840100001 -> +919840100001
UPDATE member
SET mobile = CONCAT('+', mobile)
WHERE mobile IS NOT NULL AND mobile NOT LIKE '+%' AND CHAR_LENGTH(mobile) = 12 AND mobile LIKE '91%';

UPDATE saas_user
SET mobile = CONCAT('+', mobile)
WHERE mobile IS NOT NULL AND mobile NOT LIKE '+%' AND CHAR_LENGTH(mobile) = 12 AND mobile LIKE '91%';

-- Bare local number: 9840100001 -> +919840100001
UPDATE member
SET mobile = CONCAT('+91', mobile)
WHERE mobile IS NOT NULL AND mobile NOT LIKE '+%' AND CHAR_LENGTH(mobile) = 10;

UPDATE saas_user
SET mobile = CONCAT('+91', mobile)
WHERE mobile IS NOT NULL AND mobile NOT LIKE '+%' AND CHAR_LENGTH(mobile) = 10;
