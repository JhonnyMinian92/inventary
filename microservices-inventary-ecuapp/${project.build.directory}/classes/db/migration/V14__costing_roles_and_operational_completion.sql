-- P13: completes the operational backend without rewriting applied migrations.

-- 1. Custom roles created through the admin API must be assignable to users.
--    The fixed CHECK list is replaced by a foreign key to the RBAC catalog.
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS ck_app_user_role;
ALTER TABLE app_user ADD CONSTRAINT fk_app_user_role FOREIGN KEY(role) REFERENCES app_role(code) ON DELETE RESTRICT;
-- Serializes changes that could leave the system without an active SUPER_ADMIN.
CREATE TABLE security_lock (
 lock_key VARCHAR(40) PRIMARY KEY,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
INSERT INTO security_lock(lock_key) VALUES ('SUPER_ADMIN_MEMBERSHIP');

-- 2. Running valuation state per product and stock location (moving average and FIFO totals).
CREATE TABLE inventory_cost_state (
 id UUID PRIMARY KEY,
 product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
 location_id UUID NOT NULL REFERENCES warehouse_location(id) ON DELETE RESTRICT,
 quantity NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK(quantity >= 0),
 total_value NUMERIC(18,4) NOT NULL DEFAULT 0 CHECK(total_value >= 0),
 currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
 version BIGINT NOT NULL DEFAULT 0,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_inventory_cost_state_product_location UNIQUE(product_id,location_id)
);
CREATE INDEX ix_inventory_cost_state_location ON inventory_cost_state(location_id,product_id);
CREATE INDEX ix_inventory_cost_layer_source_movement ON inventory_cost_layer(source_movement_id);
ALTER TABLE inventory_costing_config ADD COLUMN updated_by VARCHAR(80);
ALTER TABLE inventory_costing_config ADD COLUMN updated_at TIMESTAMPTZ;

-- 3. Manual payment confirmation converts a WhatsApp order into a completed sale.
ALTER TABLE whatsapp_order ADD COLUMN sale_id UUID REFERENCES sale(id) ON DELETE RESTRICT;
ALTER TABLE whatsapp_order ADD COLUMN payment_reference VARCHAR(120);
ALTER TABLE whatsapp_order ADD COLUMN payment_method VARCHAR(40);
ALTER TABLE whatsapp_order ADD COLUMN confirmed_by VARCHAR(80);
ALTER TABLE whatsapp_order ADD COLUMN confirmed_at TIMESTAMPTZ;
ALTER TABLE whatsapp_order ADD CONSTRAINT uk_whatsapp_order_sale UNIQUE(sale_id);
ALTER TABLE whatsapp_order ADD CONSTRAINT ck_whatsapp_order_confirmation CHECK (
 (status <> 'CONFIRMED') OR
 (sale_id IS NOT NULL AND payment_reference IS NOT NULL AND payment_method IS NOT NULL AND confirmed_by IS NOT NULL AND confirmed_at IS NOT NULL)
);

-- 4. Purchase order workflow metadata for segregation of duties.
ALTER TABLE purchase_order ADD COLUMN submitted_by VARCHAR(80);
ALTER TABLE purchase_order ADD COLUMN submitted_at TIMESTAMPTZ;
ALTER TABLE purchase_order ADD COLUMN approved_by VARCHAR(80);
ALTER TABLE purchase_order ADD COLUMN approved_at TIMESTAMPTZ;
ALTER TABLE purchase_order ADD COLUMN cancelled_by VARCHAR(80);
ALTER TABLE purchase_order ADD COLUMN cancelled_at TIMESTAMPTZ;
CREATE INDEX ix_purchase_order_status_ordered ON purchase_order(status,ordered_at DESC);
CREATE INDEX ix_purchase_order_warehouse_ordered ON purchase_order(warehouse_id,ordered_at DESC);

-- 5. Outbox lease and diagnostics for safe out-of-transaction publication and redrive.
ALTER TABLE outbox_event ADD COLUMN locked_until TIMESTAMPTZ;
ALTER TABLE outbox_event ADD COLUMN last_error VARCHAR(500);
CREATE INDEX ix_outbox_status_created ON outbox_event(status,created_at);

-- 6. Variants: one active combination per product and case-insensitive SKUs.
CREATE UNIQUE INDEX ux_product_variant_sku_upper ON product_variant(upper(sku));
CREATE UNIQUE INDEX ux_product_variant_options ON product_variant(product_id,option_values) WHERE active = TRUE;
CREATE UNIQUE INDEX ux_unit_of_measure_code_upper ON unit_of_measure(upper(code));
CREATE UNIQUE INDEX ux_warehouse_code_upper ON warehouse(upper(code));

-- 7. Query support for filtered listings and reports.
CREATE INDEX ix_stock_movement_time ON stock_movement(occurred_at DESC);
CREATE INDEX ix_stock_movement_type_time ON stock_movement(movement_type,occurred_at DESC);
CREATE INDEX ix_stock_movement_source_line ON stock_movement(reference_type,reference_id,source_line_id);
CREATE INDEX ix_sale_status_sold_at ON sale(status,sold_at DESC);
CREATE INDEX ix_goods_receipt_warehouse_received ON goods_receipt(warehouse_id,received_at DESC);
CREATE INDEX ix_purchase_return_warehouse_created ON purchase_return(warehouse_id,created_at DESC);
CREATE INDEX ix_sales_return_warehouse_created ON sales_return(warehouse_id,created_at DESC);
CREATE INDEX ix_audit_event_action_time ON audit_event(action,occurred_at DESC);
CREATE INDEX ix_whatsapp_order_status_created ON whatsapp_order(status,created_at DESC);

-- 8. New permissions for valuation policy and outbox operations.
INSERT INTO app_permission(id,code,description) VALUES
('20000000-0000-0000-0000-000000000019','costing:manage','Configurar la política de valoración de inventario'),
('20000000-0000-0000-0000-000000000020','operations:manage','Diagnosticar y reprocesar eventos Outbox');
INSERT INTO app_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM app_role r JOIN app_permission p ON p.code IN ('costing:manage','operations:manage') WHERE r.code='SUPER_ADMIN';
INSERT INTO app_role_permission(role_id,permission_id)
SELECT r.id,p.id FROM app_role r JOIN app_permission p ON p.code='operations:manage' WHERE r.code='SOPORTE_TI';
