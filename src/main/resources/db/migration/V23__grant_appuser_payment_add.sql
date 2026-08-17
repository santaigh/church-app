-- ---------------------------------------------------------------------------
-- V23: An ordinary parish account may collect a payment.
--
-- A volunteer or the treasurer records money received and prints a receipt, so
-- AppUser needs PAYMENT ADD. It deliberately gets no EDIT and no DELETE: once
-- money is recorded, altering it is an administrator's job and removing it is
-- the super admin's. That separation is the point -- whoever takes the cash is
-- not the person who can quietly change the record of it afterwards.
--
--   AppUser   VIEW + ADD
--   AppAdmin  VIEW + ADD + EDIT   (already seeded)
--   AppSA     VIEW + ADD + EDIT + DELETE  (already seeded)
--
-- NOTE: V11 describes AppUser as "a read-only parish account". That stops being
-- true here. The comment in V11 is left alone on purpose -- Flyway checksums
-- applied migrations, and editing one to fix its prose would break validation
-- on every database that has already run it.
-- ---------------------------------------------------------------------------

INSERT INTO role_permission (uuid, role_id, resource_code, operation_code, created_user)
SELECT UUID(), r.id, 'PAYMENT', 'ADD', 'SYSTEM'
FROM role r
WHERE r.role_code = 'AppUser'
  AND NOT EXISTS (SELECT 1
                  FROM role_permission existing
                  WHERE existing.role_id = r.id
                    AND existing.resource_code = 'PAYMENT'
                    AND existing.operation_code = 'ADD');
