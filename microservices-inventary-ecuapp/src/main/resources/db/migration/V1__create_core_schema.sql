CREATE TABLE app_user (
 id UUID PRIMARY KEY, username VARCHAR(80) NOT NULL, password_hash VARCHAR(100) NOT NULL,
 role VARCHAR(32) NOT NULL CHECK (role IN ('ADMIN','INVENTORY_MANAGER','SALES','AUDITOR')),
 active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL, CONSTRAINT uk_app_user_username UNIQUE(username)
);
CREATE TABLE category (
 id UUID PRIMARY KEY, name VARCHAR(120) NOT NULL, slug VARCHAR(140) NOT NULL, description VARCHAR(1000),
 active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_category_name UNIQUE(name), CONSTRAINT uk_category_slug UNIQUE(slug)
);
CREATE INDEX ix_category_active_name ON category(active,name);
CREATE TABLE product (
 id UUID PRIMARY KEY, sku VARCHAR(64) NOT NULL, name VARCHAR(180) NOT NULL, description VARCHAR(2000),
 category_id UUID NOT NULL REFERENCES category(id) ON DELETE RESTRICT,
 cost_price NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK(cost_price >= 0),
 sale_price NUMERIC(14,2) NOT NULL CHECK(sale_price >= 0),
 currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK(currency ~ '^[A-Z]{3}$'),
 image_url VARCHAR(2048), active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 version BIGINT NOT NULL DEFAULT 0, CONSTRAINT uk_product_sku UNIQUE(sku)
);
CREATE INDEX ix_product_active_name ON product(active,name);
CREATE INDEX ix_product_category_id ON product(category_id);
CREATE TABLE warehouse (
 id UUID PRIMARY KEY, code VARCHAR(40) NOT NULL, name VARCHAR(140) NOT NULL, address VARCHAR(500),
 active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL, updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_warehouse_code UNIQUE(code)
);
CREATE TABLE stock_balance (
 id UUID PRIMARY KEY, product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
 warehouse_id UUID NOT NULL REFERENCES warehouse(id) ON DELETE RESTRICT,
 on_hand NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK(on_hand >= 0),
 reserved NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK(reserved >= 0 AND reserved <= on_hand),
 version BIGINT NOT NULL DEFAULT 0, updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_stock_balance_product_warehouse UNIQUE(product_id,warehouse_id)
);
CREATE INDEX ix_stock_balance_warehouse ON stock_balance(warehouse_id,product_id);
CREATE TABLE stock_movement (
 id UUID PRIMARY KEY, product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
 warehouse_id UUID NOT NULL REFERENCES warehouse(id) ON DELETE RESTRICT,
 movement_type VARCHAR(32) NOT NULL CHECK(movement_type IN ('ADJUSTMENT_IN','ADJUSTMENT_OUT','SALE','RECEIPT','RETURN_IN','RETURN_OUT','TRANSFER_IN','TRANSFER_OUT','RESERVE','RELEASE')),
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity > 0), unit_cost NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK(unit_cost >= 0),
 reference_type VARCHAR(40), reference_id UUID, idempotency_key VARCHAR(120), request_hash VARCHAR(64),
 notes VARCHAR(1000), created_by VARCHAR(80) NOT NULL, occurred_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_stock_movement_idempotency_key UNIQUE(idempotency_key)
);
CREATE INDEX ix_stock_movement_product_time ON stock_movement(product_id,occurred_at DESC);
CREATE INDEX ix_stock_movement_warehouse_time ON stock_movement(warehouse_id,occurred_at DESC);
CREATE INDEX ix_stock_movement_reference ON stock_movement(reference_type,reference_id);
CREATE TABLE sale (
 id UUID PRIMARY KEY, sale_number VARCHAR(48) NOT NULL, status VARCHAR(24) NOT NULL CHECK(status IN ('COMPLETED','CANCELLED')),
 customer_name VARCHAR(160), customer_phone VARCHAR(40), channel VARCHAR(24) NOT NULL CHECK(channel IN ('POS','WHATSAPP','WEB','MANUAL')),
 warehouse_id UUID NOT NULL REFERENCES warehouse(id) ON DELETE RESTRICT,
 subtotal NUMERIC(14,2) NOT NULL CHECK(subtotal >= 0), tax NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK(tax >= 0),
 total NUMERIC(14,2) NOT NULL CHECK(total >= 0), currency VARCHAR(3) NOT NULL DEFAULT 'USD' CHECK(currency ~ '^[A-Z]{3}$'),
 idempotency_key VARCHAR(120), request_hash VARCHAR(64), created_by VARCHAR(80) NOT NULL, sold_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_sale_number UNIQUE(sale_number), CONSTRAINT uk_sale_idempotency_key UNIQUE(idempotency_key)
);
CREATE INDEX ix_sale_sold_at ON sale(sold_at DESC);
CREATE INDEX ix_sale_warehouse_sold_at ON sale(warehouse_id,sold_at DESC);
CREATE TABLE sale_item (
 id UUID PRIMARY KEY, sale_id UUID NOT NULL REFERENCES sale(id) ON DELETE RESTRICT,
 product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
 sku_snapshot VARCHAR(64) NOT NULL, product_name_snapshot VARCHAR(180) NOT NULL,
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity > 0), unit_price NUMERIC(14,2) NOT NULL CHECK(unit_price >= 0),
 line_total NUMERIC(14,2) NOT NULL CHECK(line_total >= 0)
);
CREATE INDEX ix_sale_item_sale ON sale_item(sale_id);
CREATE INDEX ix_sale_item_product ON sale_item(product_id);
CREATE TABLE outbox_event (
 id UUID PRIMARY KEY, aggregate_type VARCHAR(80) NOT NULL, aggregate_id UUID NOT NULL, event_type VARCHAR(120) NOT NULL,
 payload JSONB NOT NULL, status VARCHAR(20) NOT NULL DEFAULT 'PENDING' CHECK(status IN ('PENDING','PROCESSING','PUBLISHED','FAILED')),
 attempts INTEGER NOT NULL DEFAULT 0 CHECK(attempts >= 0), next_attempt_at TIMESTAMPTZ NOT NULL, created_at TIMESTAMPTZ NOT NULL, published_at TIMESTAMPTZ
);
CREATE INDEX ix_outbox_pending ON outbox_event(status,next_attempt_at,created_at);
