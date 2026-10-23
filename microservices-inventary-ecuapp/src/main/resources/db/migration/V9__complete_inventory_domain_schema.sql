-- P04: complete the operational schema without rewriting applied migrations.
CREATE TABLE unit_of_measure (
 id UUID PRIMARY KEY,
 code VARCHAR(24) NOT NULL,
 name VARCHAR(100) NOT NULL,
 dimension VARCHAR(32) NOT NULL,
 decimal_scale SMALLINT NOT NULL DEFAULT 3 CHECK (decimal_scale BETWEEN 0 AND 6),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_unit_of_measure_code UNIQUE(code)
);

INSERT INTO unit_of_measure(id,code,name,dimension,decimal_scale)
VALUES ('00000000-0000-0000-0000-000000000001','EA','Unidad','COUNT',3);

CREATE TABLE unit_conversion (
 id UUID PRIMARY KEY,
 from_unit_id UUID NOT NULL REFERENCES unit_of_measure(id) ON DELETE RESTRICT,
 to_unit_id UUID NOT NULL REFERENCES unit_of_measure(id) ON DELETE RESTRICT,
 factor NUMERIC(20,9) NOT NULL CHECK(factor > 0),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT ck_unit_conversion_different_units CHECK(from_unit_id <> to_unit_id),
 CONSTRAINT uk_unit_conversion_pair UNIQUE(from_unit_id,to_unit_id)
);

ALTER TABLE product
 ADD COLUMN product_kind VARCHAR(24) NOT NULL DEFAULT 'STOCKED_GOOD'
   CHECK(product_kind IN ('STOCKED_GOOD','NON_STOCKED_GOOD','SERVICE')),
 ADD COLUMN unit_of_measure_id UUID,
 ADD COLUMN attributes JSONB NOT NULL DEFAULT '{}'::jsonb,
 ADD COLUMN attribute_schema_version INTEGER NOT NULL DEFAULT 1 CHECK(attribute_schema_version > 0);

UPDATE product SET unit_of_measure_id='00000000-0000-0000-0000-000000000001' WHERE unit_of_measure_id IS NULL;
ALTER TABLE product ALTER COLUMN unit_of_measure_id SET NOT NULL;
ALTER TABLE product ADD CONSTRAINT fk_product_unit_of_measure
 FOREIGN KEY(unit_of_measure_id) REFERENCES unit_of_measure(id) ON DELETE RESTRICT;
CREATE INDEX ix_product_unit_kind ON product(unit_of_measure_id,product_kind);
CREATE INDEX ix_product_attributes_gin ON product USING GIN(attributes);

CREATE TABLE category_attribute_definition (
 id UUID PRIMARY KEY,
 category_id UUID NOT NULL REFERENCES category(id) ON DELETE RESTRICT,
 attribute_key VARCHAR(80) NOT NULL,
 display_name VARCHAR(120) NOT NULL,
 value_type VARCHAR(24) NOT NULL CHECK(value_type IN ('STRING','NUMBER','BOOLEAN','DATE','ENUM','STRING_LIST')),
 required BOOLEAN NOT NULL DEFAULT FALSE,
 constraints JSONB NOT NULL DEFAULT '{}'::jsonb,
 options JSONB NOT NULL DEFAULT '[]'::jsonb,
 schema_version INTEGER NOT NULL DEFAULT 1 CHECK(schema_version > 0),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_category_attribute_version UNIQUE(category_id,attribute_key,schema_version)
);
CREATE INDEX ix_category_attribute_active ON category_attribute_definition(category_id,active);

CREATE TABLE product_variant (
 id UUID PRIMARY KEY,
 product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
 sku VARCHAR(64) NOT NULL,
 barcode VARCHAR(128),
 option_values JSONB NOT NULL DEFAULT '{}'::jsonb,
 unit_of_measure_id UUID NOT NULL REFERENCES unit_of_measure(id) ON DELETE RESTRICT,
 sale_price NUMERIC(14,2) CHECK(sale_price >= 0),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_product_variant_sku UNIQUE(sku),
 CONSTRAINT uk_product_variant_barcode UNIQUE(barcode)
);
CREATE INDEX ix_product_variant_product_active ON product_variant(product_id,active);

CREATE TABLE warehouse_location (
 id UUID PRIMARY KEY,
 warehouse_id UUID NOT NULL REFERENCES warehouse(id) ON DELETE RESTRICT,
 code VARCHAR(40) NOT NULL,
 name VARCHAR(120) NOT NULL,
 location_type VARCHAR(24) NOT NULL CHECK(location_type IN ('STORAGE','TRANSIT','QUARANTINE','SCRAP','PICKING')),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_warehouse_location_code UNIQUE(warehouse_id,code)
);
INSERT INTO warehouse_location(id,warehouse_id,code,name,location_type,active)
SELECT md5(w.id::text || ':MAIN')::uuid,w.id,'MAIN','Ubicación principal','STORAGE',w.active
FROM warehouse w;

ALTER TABLE stock_balance ADD COLUMN location_id UUID;
UPDATE stock_balance b SET location_id=l.id
FROM warehouse_location l WHERE l.warehouse_id=b.warehouse_id AND l.code='MAIN';
ALTER TABLE stock_balance ALTER COLUMN location_id SET NOT NULL;
ALTER TABLE stock_balance ADD CONSTRAINT fk_stock_balance_location
 FOREIGN KEY(location_id) REFERENCES warehouse_location(id) ON DELETE RESTRICT;
ALTER TABLE stock_balance DROP CONSTRAINT uk_stock_balance_product_warehouse;
ALTER TABLE stock_balance ADD CONSTRAINT uk_stock_balance_product_location UNIQUE(product_id,location_id);
-- Keep the current one-balance-per-product-per-warehouse API compatible during the location-aware rollout.
ALTER TABLE stock_balance ADD CONSTRAINT uk_stock_balance_product_warehouse UNIQUE(product_id,warehouse_id);
CREATE INDEX ix_stock_balance_location_product ON stock_balance(location_id,product_id);

ALTER TABLE stock_movement
 ADD COLUMN from_location_id UUID REFERENCES warehouse_location(id) ON DELETE RESTRICT,
 ADD COLUMN to_location_id UUID REFERENCES warehouse_location(id) ON DELETE RESTRICT,
 ADD COLUMN unit_of_measure_id UUID REFERENCES unit_of_measure(id) ON DELETE RESTRICT,
 ADD COLUMN source_line_id UUID,
 ADD COLUMN correlation_id VARCHAR(80),
 ADD COLUMN valuation_method VARCHAR(24) CHECK(valuation_method IS NULL OR valuation_method IN ('FIFO','MOVING_AVERAGE'));
CREATE INDEX ix_stock_movement_locations_time ON stock_movement(from_location_id,to_location_id,occurred_at DESC);

CREATE TABLE stock_reservation (
 id UUID PRIMARY KEY,
 reservation_number VARCHAR(48) NOT NULL,
 sale_item_id UUID REFERENCES sale_item(id) ON DELETE RESTRICT,
 product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
 warehouse_location_id UUID NOT NULL REFERENCES warehouse_location(id) ON DELETE RESTRICT,
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity > 0),
 status VARCHAR(16) NOT NULL CHECK(status IN ('ACTIVE','RELEASED','CONSUMED','EXPIRED')),
 idempotency_key VARCHAR(120) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 expires_at TIMESTAMPTZ,
 created_by VARCHAR(80) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 released_at TIMESTAMPTZ,
 CONSTRAINT uk_stock_reservation_number UNIQUE(reservation_number),
 CONSTRAINT uk_stock_reservation_idempotency UNIQUE(idempotency_key)
);
CREATE INDEX ix_stock_reservation_active_expiry ON stock_reservation(status,expires_at);
CREATE INDEX ix_stock_reservation_product_location ON stock_reservation(product_id,warehouse_location_id,status);

CREATE TABLE inventory_costing_config (
 id UUID PRIMARY KEY,
 singleton_key VARCHAR(24) NOT NULL DEFAULT 'DEFAULT',
 method VARCHAR(24) NOT NULL CHECK(method IN ('FIFO','MOVING_AVERAGE')),
 effective_from TIMESTAMPTZ NOT NULL,
 created_by VARCHAR(80) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 CONSTRAINT uk_inventory_costing_singleton UNIQUE(singleton_key),
 CONSTRAINT ck_inventory_costing_singleton CHECK(singleton_key='DEFAULT')
);
INSERT INTO inventory_costing_config(id,singleton_key,method,effective_from,created_by)
VALUES ('00000000-0000-0000-0000-000000000002','DEFAULT','MOVING_AVERAGE',CURRENT_TIMESTAMP,'SYSTEM');

CREATE TABLE inventory_cost_layer (
 id UUID PRIMARY KEY,
 product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
 warehouse_location_id UUID NOT NULL REFERENCES warehouse_location(id) ON DELETE RESTRICT,
 source_movement_id UUID NOT NULL REFERENCES stock_movement(id) ON DELETE RESTRICT,
 received_quantity NUMERIC(14,3) NOT NULL CHECK(received_quantity > 0),
 remaining_quantity NUMERIC(14,3) NOT NULL CHECK(remaining_quantity >= 0 AND remaining_quantity <= received_quantity),
 unit_cost NUMERIC(14,4) NOT NULL CHECK(unit_cost >= 0),
 currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
 received_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_inventory_cost_layer_fifo ON inventory_cost_layer(product_id,warehouse_location_id,received_at,id)
 WHERE remaining_quantity > 0;

CREATE TABLE inventory_valuation_entry (
 id UUID PRIMARY KEY,
 stock_movement_id UUID NOT NULL REFERENCES stock_movement(id) ON DELETE RESTRICT,
 cost_layer_id UUID REFERENCES inventory_cost_layer(id) ON DELETE RESTRICT,
 method VARCHAR(24) NOT NULL CHECK(method IN ('FIFO','MOVING_AVERAGE')),
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity > 0),
 unit_cost NUMERIC(14,4) NOT NULL CHECK(unit_cost >= 0),
 total_cost NUMERIC(18,4) NOT NULL CHECK(total_cost >= 0),
 currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_inventory_valuation_movement ON inventory_valuation_entry(stock_movement_id);
CREATE INDEX ix_inventory_valuation_layer ON inventory_valuation_entry(cost_layer_id);

CREATE TABLE idempotency_record (
 id UUID PRIMARY KEY,
 scope VARCHAR(120) NOT NULL,
 idempotency_key VARCHAR(120) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 status VARCHAR(16) NOT NULL CHECK(status IN ('IN_PROGRESS','COMPLETED','FAILED')),
 resource_type VARCHAR(80),
 resource_id UUID,
 response_status SMALLINT,
 response_body JSONB,
 created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
 completed_at TIMESTAMPTZ,
 CONSTRAINT uk_idempotency_scope_key UNIQUE(scope,idempotency_key),
 CONSTRAINT ck_idempotency_completion CHECK(
   (status='IN_PROGRESS' AND completed_at IS NULL) OR
   (status IN ('COMPLETED','FAILED') AND completed_at IS NOT NULL)
 )
);
CREATE INDEX ix_idempotency_created_at ON idempotency_record(created_at);

-- Ledger history is append-only; corrections must be represented by compensating movements.
CREATE OR REPLACE FUNCTION prevent_ledger_mutation() RETURNS trigger AS $$
BEGIN
 RAISE EXCEPTION 'Ledger records are immutable; create a compensating transaction instead.';
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_stock_movement_immutable
 BEFORE UPDATE OR DELETE ON stock_movement FOR EACH ROW EXECUTE FUNCTION prevent_ledger_mutation();
CREATE TRIGGER trg_inventory_valuation_immutable
 BEFORE UPDATE OR DELETE ON inventory_valuation_entry FOR EACH ROW EXECUTE FUNCTION prevent_ledger_mutation();
