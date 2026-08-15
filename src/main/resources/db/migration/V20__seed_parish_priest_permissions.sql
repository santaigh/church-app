-- ---------------------------------------------------------------------------
-- V20: Who may do what with the clergy of a parish.
--
-- Deliberately narrower than the matrix V11 generated for every other resource.
-- An appointment is a diocese-level act: parish staff see who serves them but
-- cannot change it, so no App-level role gets anything beyond VIEW.
--
--   VIEW    every role -- a parishioner may see who their priest is
--   ADD     SaaSSAdmin, SaaSAdmin
--   EDIT    SaaSSAdmin, SaaSAdmin
--   DELETE  SaaSSAdmin only, as everywhere else
--
-- Data, not structure: widening this later is an INSERT, with no migration and
-- no redeploy.
-- ---------------------------------------------------------------------------

INSERT INTO role_permission (uuid, role_id, resource_code, operation_code, created_user)
SELECT UUID(), r.id, 'PARISH_PRIEST', 'VIEW', 'SYSTEM'
FROM role r;

INSERT INTO role_permission (uuid, role_id, resource_code, operation_code, created_user)
SELECT UUID(), r.id, 'PARISH_PRIEST', op.code, 'SYSTEM'
FROM role r
         CROSS JOIN (SELECT 'ADD' AS code UNION ALL SELECT 'EDIT') op
WHERE r.role_code IN ('SaaSSAdmin', 'SaaSAdmin');

INSERT INTO role_permission (uuid, role_id, resource_code, operation_code, created_user)
SELECT UUID(), r.id, 'PARISH_PRIEST', 'DELETE', 'SYSTEM'
FROM role r
WHERE r.role_code = 'SaaSSAdmin';
