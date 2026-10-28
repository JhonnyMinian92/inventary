CREATE TABLE purchase_return (
 id UUID PRIMARY KEY,
 return_number VARCHAR(48) NOT NULL,
 status VARCHAR(16) NOT NULL CHECK(status IN ('POSTED','CANCELLED')),
 purchase_order_id UUID NOT NULL REFERENCES purchase_order(id) ON DELETE RESTRICT,
 goods_receipt_id UUID NOT NULL REFERENCES goods_receipt(id) ON DELETE RESTRICT,
 warehouse_id UUID NOT NULL REFERENCES warehouse(id) ON DELETE RESTRICT,
 reason VARCHAR(1000) NOT NULL,
 idempotency_key VARCHAR(120) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 created_by VARCHAR(80) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_purchase_return_number UNIQUE(return_number),
 CONSTRAINT uk_purchase_return_idempotency UNIQUE(idempotency_key)
);
CREATE INDEX ix_purchase_return_receipt ON purchase_return(goods_receipt_id,created_at DESC);
CREATE TABLE purchase_return_line (
 id UUID PRIMARY KEY,
 purchase_return_id UUID NOT NULL REFERENCES purchase_return(id) ON DELETE RESTRICT,
 source_goods_receipt_line_id UUID NOT NULL REFERENCES goods_receipt_line(id) ON DELETE RESTRICT,
 product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity > 0),
 unit_cost NUMERIC(14,2) NOT NULL CHECK(unit_cost >= 0),
 line_total NUMERIC(14,2) NOT NULL CHECK(line_total >= 0),
 CONSTRAINT uk_purchase_return_receipt_line UNIQUE(purchase_return_id,source_goods_receipt_line_id)
);
CREATE INDEX ix_purchase_return_line_source ON purchase_return_line(source_goods_receipt_line_id);

CREATE TABLE reorder_rule (
 id UUID PRIMARY KEY,
 product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
 warehouse_id UUID NOT NULL REFERENCES warehouse(id) ON DELETE RESTRICT,
 minimum_quantity NUMERIC(14,3) NOT NULL CHECK(minimum_quantity >= 0),
 target_quantity NUMERIC(14,3) NOT NULL CHECK(target_quantity >= minimum_quantity),
 lead_time_days INTEGER NOT NULL DEFAULT 0 CHECK(lead_time_days >= 0),
 preferred_supplier_id UUID REFERENCES business_partner(id) ON DELETE RESTRICT,
 active BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_reorder_rule_product_warehouse UNIQUE(product_id,warehouse_id)
);
CREATE INDEX ix_reorder_rule_active_min ON reorder_rule(active,minimum_quantity);
