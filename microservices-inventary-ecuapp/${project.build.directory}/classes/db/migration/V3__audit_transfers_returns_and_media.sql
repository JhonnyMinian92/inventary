CREATE TABLE audit_event (
 id UUID PRIMARY KEY,
 actor VARCHAR(80) NOT NULL,
 action VARCHAR(120) NOT NULL,
 resource_type VARCHAR(80) NOT NULL,
 resource_id UUID,
 outcome VARCHAR(20) NOT NULL CHECK(outcome IN ('SUCCESS','DENIED','FAILURE')),
 correlation_id VARCHAR(80),
 metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
 occurred_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_audit_event_time ON audit_event(occurred_at DESC);
CREATE INDEX ix_audit_event_actor_time ON audit_event(actor,occurred_at DESC);
CREATE INDEX ix_audit_event_resource ON audit_event(resource_type,resource_id,occurred_at DESC);

CREATE TABLE stock_transfer (
 id UUID PRIMARY KEY,
 transfer_number VARCHAR(48) NOT NULL,
 status VARCHAR(20) NOT NULL CHECK(status IN ('DRAFT','IN_TRANSIT','COMPLETED','CANCELLED')),
 source_warehouse_id UUID NOT NULL REFERENCES warehouse(id) ON DELETE RESTRICT,
 destination_warehouse_id UUID NOT NULL REFERENCES warehouse(id) ON DELETE RESTRICT,
 idempotency_key VARCHAR(120) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 created_by VARCHAR(80) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 dispatched_at TIMESTAMPTZ,
 completed_at TIMESTAMPTZ,
 CONSTRAINT uk_stock_transfer_number UNIQUE(transfer_number),
 CONSTRAINT uk_stock_transfer_idempotency UNIQUE(idempotency_key),
 CONSTRAINT ck_stock_transfer_distinct_warehouses CHECK(source_warehouse_id <> destination_warehouse_id)
);
CREATE INDEX ix_stock_transfer_status_created ON stock_transfer(status,created_at DESC);
CREATE TABLE stock_transfer_line (
 id UUID PRIMARY KEY,
 transfer_id UUID NOT NULL REFERENCES stock_transfer(id) ON DELETE RESTRICT,
 product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
 sku_snapshot VARCHAR(64) NOT NULL,
 product_name_snapshot VARCHAR(180) NOT NULL,
 requested_quantity NUMERIC(14,3) NOT NULL CHECK(requested_quantity > 0),
 received_quantity NUMERIC(14,3) NOT NULL DEFAULT 0 CHECK(received_quantity >= 0 AND received_quantity <= requested_quantity),
 unit_cost NUMERIC(14,2) NOT NULL CHECK(unit_cost >= 0),
 CONSTRAINT uk_stock_transfer_line_product UNIQUE(transfer_id,product_id)
);
CREATE INDEX ix_stock_transfer_line_transfer ON stock_transfer_line(transfer_id);

CREATE TABLE sales_return (
 id UUID PRIMARY KEY,
 return_number VARCHAR(48) NOT NULL,
 status VARCHAR(16) NOT NULL CHECK(status IN ('POSTED','CANCELLED')),
 source_sale_id UUID NOT NULL REFERENCES sale(id) ON DELETE RESTRICT,
 warehouse_id UUID NOT NULL REFERENCES warehouse(id) ON DELETE RESTRICT,
 reason VARCHAR(1000) NOT NULL,
 idempotency_key VARCHAR(120) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 created_by VARCHAR(80) NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_sales_return_number UNIQUE(return_number),
 CONSTRAINT uk_sales_return_idempotency UNIQUE(idempotency_key)
);
CREATE INDEX ix_sales_return_source_sale ON sales_return(source_sale_id,created_at DESC);
CREATE TABLE sales_return_line (
 id UUID PRIMARY KEY,
 sales_return_id UUID NOT NULL REFERENCES sales_return(id) ON DELETE RESTRICT,
 source_sale_item_id UUID NOT NULL REFERENCES sale_item(id) ON DELETE RESTRICT,
 product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity > 0),
 disposition VARCHAR(16) NOT NULL CHECK(disposition IN ('RESTOCK','SCRAP')),
 unit_price NUMERIC(14,2) NOT NULL CHECK(unit_price >= 0),
 line_total NUMERIC(14,2) NOT NULL CHECK(line_total >= 0),
 CONSTRAINT uk_sales_return_source_item UNIQUE(sales_return_id,source_sale_item_id)
);
CREATE INDEX ix_sales_return_line_source ON sales_return_line(source_sale_item_id);

CREATE TABLE product_image (
 id UUID PRIMARY KEY,
 product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
 image_url VARCHAR(2048) NOT NULL,
 alt_text VARCHAR(300),
 sort_order INTEGER NOT NULL DEFAULT 0 CHECK(sort_order >= 0),
 is_primary BOOLEAN NOT NULL DEFAULT FALSE,
 published BOOLEAN NOT NULL DEFAULT TRUE,
 created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_product_image_url UNIQUE(product_id,image_url)
);
CREATE INDEX ix_product_image_product_sort ON product_image(product_id,published,sort_order);
CREATE UNIQUE INDEX ux_product_image_primary ON product_image(product_id) WHERE is_primary = TRUE AND published = TRUE;
