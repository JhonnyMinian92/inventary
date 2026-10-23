-- Only the super administrator can explicitly replay dead-lettered Outbox events.
INSERT INTO app_permission(id, code, description)
VALUES ('20000000-0000-0000-0000-000000000019', 'outbox:manage', 'Reactivar eventos Outbox fallidos')
ON CONFLICT (code) DO NOTHING;

INSERT INTO app_role_permission(role_id, permission_id)
SELECT r.id, p.id
FROM app_role r
JOIN app_permission p ON p.code = 'outbox:manage'
WHERE r.code = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
