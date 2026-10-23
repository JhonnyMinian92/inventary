-- P05: normalized RBAC and revocation of already-issued access tokens.
DO $$
DECLARE role_constraint RECORD;
BEGIN
 FOR role_constraint IN SELECT conname FROM pg_constraint
 WHERE conrelid='app_user'::regclass AND contype='c' AND pg_get_constraintdef(oid) ILIKE '%role%'
 LOOP EXECUTE format('ALTER TABLE app_user DROP CONSTRAINT %I',role_constraint.conname); END LOOP;
END $$;
UPDATE app_user SET role=CASE role WHEN 'ADMIN' THEN 'SUPER_ADMIN' WHEN 'INVENTORY_MANAGER' THEN 'MANAGER_ALMACEN' WHEN 'SALES' THEN 'VENDEDOR' ELSE role END;
ALTER TABLE app_user ADD CONSTRAINT ck_app_user_role CHECK(role IN ('SUPER_ADMIN','AUDITOR','MANAGER_ALMACEN','VENDEDOR','SOPORTE_TI'));
ALTER TABLE app_user ADD COLUMN token_version INTEGER NOT NULL DEFAULT 0 CHECK(token_version>=0);
CREATE TABLE app_role(id UUID PRIMARY KEY,code VARCHAR(40) NOT NULL,display_name VARCHAR(100) NOT NULL,active BOOLEAN NOT NULL DEFAULT TRUE,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,CONSTRAINT uk_app_role_code UNIQUE(code));
CREATE TABLE app_permission(id UUID PRIMARY KEY,code VARCHAR(64) NOT NULL,description VARCHAR(240) NOT NULL,active BOOLEAN NOT NULL DEFAULT TRUE,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,CONSTRAINT uk_app_permission_code UNIQUE(code));
CREATE TABLE app_role_permission(role_id UUID NOT NULL REFERENCES app_role(id) ON DELETE RESTRICT,permission_id UUID NOT NULL REFERENCES app_permission(id) ON DELETE RESTRICT,created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,PRIMARY KEY(role_id,permission_id));
INSERT INTO app_role(id,code,display_name) VALUES
('10000000-0000-0000-0000-000000000001','SUPER_ADMIN','Administrador general'),
('10000000-0000-0000-0000-000000000002','AUDITOR','Auditor de solo lectura'),
('10000000-0000-0000-0000-000000000003','MANAGER_ALMACEN','Responsable de almacén'),
('10000000-0000-0000-0000-000000000004','VENDEDOR','Vendedor'),
('10000000-0000-0000-0000-000000000005','SOPORTE_TI','Soporte técnico');
INSERT INTO app_permission(id,code,description) VALUES
('20000000-0000-0000-0000-000000000001','security:manage','Administrar usuarios, roles y permisos'),
('20000000-0000-0000-0000-000000000002','catalog:read','Consultar catálogo interno'),
('20000000-0000-0000-0000-000000000003','catalog:manage','Crear y mantener catálogo'),
('20000000-0000-0000-0000-000000000004','inventory:read','Consultar inventario y movimientos'),
('20000000-0000-0000-0000-000000000005','inventory:manage','Ajustar, transferir y mantener inventario'),
('20000000-0000-0000-0000-000000000006','purchasing:read','Consultar compras y recepciones'),
('20000000-0000-0000-0000-000000000007','purchasing:manage','Crear y procesar compras y recepciones'),
('20000000-0000-0000-0000-000000000008','sales:read','Consultar ventas'),
('20000000-0000-0000-0000-000000000009','sales:manage','Crear ventas'),
('20000000-0000-0000-0000-000000000010','sales:cancel','Aprobar cancelaciones de ventas'),
('20000000-0000-0000-0000-000000000011','audit:read','Consultar auditoría'),
('20000000-0000-0000-0000-000000000012','technical:read','Consultar información técnica autorizada'),
('20000000-0000-0000-0000-000000000013','reports:financial','Consultar reportes financieros'),
('20000000-0000-0000-0000-000000000014','partners:read','Consultar clientes y proveedores'),
('20000000-0000-0000-0000-000000000015','partners:manage','Mantener clientes y proveedores'),
('20000000-0000-0000-0000-000000000016','media:manage','Mantener medios del catálogo');
INSERT INTO app_role_permission(role_id,permission_id) SELECT r.id,p.id FROM app_role r CROSS JOIN app_permission p WHERE r.code='SUPER_ADMIN';
INSERT INTO app_role_permission(role_id,permission_id) SELECT r.id,p.id FROM app_role r JOIN app_permission p ON p.code IN ('catalog:read','inventory:read','purchasing:read','sales:read','audit:read','partners:read') WHERE r.code='AUDITOR';
INSERT INTO app_role_permission(role_id,permission_id) SELECT r.id,p.id FROM app_role r JOIN app_permission p ON p.code IN ('catalog:read','catalog:manage','inventory:read','inventory:manage','purchasing:read','purchasing:manage','partners:read','partners:manage','media:manage') WHERE r.code='MANAGER_ALMACEN';
INSERT INTO app_role_permission(role_id,permission_id) SELECT r.id,p.id FROM app_role r JOIN app_permission p ON p.code IN ('catalog:read','inventory:read','sales:read','sales:manage','partners:read') WHERE r.code='VENDEDOR';
INSERT INTO app_role_permission(role_id,permission_id) SELECT r.id,p.id FROM app_role r JOIN app_permission p ON p.code='technical:read' WHERE r.code='SOPORTE_TI';
CREATE INDEX ix_app_user_active_role ON app_user(active,role);
CREATE INDEX ix_app_role_permission_permission ON app_role_permission(permission_id,role_id);
