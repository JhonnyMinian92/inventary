-- Dedicated tax-data permissions: operational inventory access must not imply access to taxpayer identifiers.
INSERT INTO app_permission(id,code,description) VALUES
('20000000-0000-0000-0000-000000000022','taxpayer:read','Consultar datos tributarios y snapshots de contribuyentes'),
('20000000-0000-0000-0000-000000000023','taxpayer:manage','Importar y administrar registros tributarios y sus asociaciones');
-- Taxpayer registry is restricted to security administrators and read-only auditors.
INSERT INTO app_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM app_role r JOIN app_permission p ON p.code='taxpayer:read'
WHERE r.code IN ('SUPER_ADMIN','AUDITOR');
INSERT INTO app_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM app_role r JOIN app_permission p ON p.code='taxpayer:manage'
WHERE r.code='SUPER_ADMIN';
