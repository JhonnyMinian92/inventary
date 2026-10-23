CREATE TABLE business_partner (
 id UUID PRIMARY KEY,
 code VARCHAR(40) NOT NULL,
 partner_type VARCHAR(16) NOT NULL CHECK (partner_type IN ('CUSTOMER','SUPPLIER','BOTH')),
 name VARCHAR(180) NOT NULL,
 tax_id VARCHAR(32),
 email VARCHAR(254),
 phone VARCHAR(40),
 address VARCHAR(500),
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_business_partner_code UNIQUE(code),
 CONSTRAINT uk_business_partner_tax_id UNIQUE(tax_id)
);
CREATE INDEX ix_business_partner_type_active_name ON business_partner(partner_type,active,name);

CREATE TABLE purchase_order (
 id UUID PRIMARY KEY,
 order_number VARCHAR(48) NOT NULL,
 status VARCHAR(32) NOT NULL CHECK(status IN ('DRAFT','SUBMITTED','APPROVED','PARTIALLY_RECEIVED','RECEIVED','CANCELLED')),
 supplier_id UUID NOT NULL REFERENCES business_partner(id) ON DELETE RESTRICT,
 warehouse_id UUID NOT NULL REFERENCES warehouse(id) ON DELETE RESTRICT,
 currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
 subtotal NUMERIC(14,2) NOT NULL CHECK(subtotal >= 0),
 total NUMERIC(14,2) NOT NULL CHECK(total >= 0),
 notes VARCHAR(1000),
 created_by VARCHAR(80) NOT NULL,
 ordered_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_purchase_order_number UNIQUE(order_number)
);
CREATE INDEX ix_purchase_order_supplier_status ON purchase_order(supplier_id,status,ordered_at DESC);
CREATE TABLE purchase_order_line (
 id UUID PRIMARY KEY,
 purchase_order_id UUID NOT NULL REFERENCES purchase_order(id) ON DELETE RESTRICT,
 product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
 sku_snapshot VARCHAR(64) NOT NULL,
 product_name_snapshot VARCHAR(180) NOT NULL,
 ordered_quantity NUMERIC(14,3) NOT NULL CHECK(ordered_quantity > 0),
 received_quantity NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK(received_quantity >= 0 AND received_quantity <= ordered_quantity),
 unit_cost NUMERIC(14,2) NOT NULL CHECK(unit_cost >= 0),
 line_total NUMERIC(14,2) NOT NULL CHECK(line_total >= 0)
);
CREATE INDEX ix_purchase_order_line_order ON purchase_order_line(purchase_order_id);

CREATE TABLE goods_receipt (
 id UUID PRIMARY KEY,
 receipt_number VARCHAR(48) NOT NULL,
 status VARCHAR(16) NOT NULL CHECK(status IN ('POSTED','CANCELLED')),
 purchase_order_id UUID NOT NULL REFERENCES purchase_order(id) ON DELETE RESTRICT,
 warehouse_id UUID NOT NULL REFERENCES warehouse(id) ON DELETE RESTRICT,
 idempotency_key VARCHAR(120) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 created_by VARCHAR(80) NOT NULL,
 received_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_goods_receipt_number UNIQUE(receipt_number),
 CONSTRAINT uk_goods_receipt_idempotency UNIQUE(idempotency_key)
);
CREATE INDEX ix_goods_receipt_order ON goods_receipt(purchase_order_id,received_at DESC);
CREATE TABLE goods_receipt_line (
 id UUID PRIMARY KEY,
 receipt_id UUID NOT NULL REFERENCES goods_receipt(id) ON DELETE RESTRICT,
 purchase_order_line_id UUID NOT NULL REFERENCES purchase_order_line(id) ON DELETE RESTRICT,
 product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity > 0),
 unit_cost NUMERIC(14,2) NOT NULL CHECK(unit_cost >= 0),
 line_total NUMERIC(14,2) NOT NULL CHECK(line_total >= 0),
 CONSTRAINT uk_goods_receipt_po_line UNIQUE(receipt_id,purchase_order_line_id)
);
CREATE INDEX ix_goods_receipt_line_receipt ON goods_receipt_line(receipt_id);
