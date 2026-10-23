CREATE TABLE whatsapp_cart (
 id UUID PRIMARY KEY,
 customer_ref VARCHAR(120) NOT NULL,
 status VARCHAR(16) NOT NULL CHECK(status IN ('OPEN','QUOTED','ORDERED','EXPIRED')),
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL
);
CREATE INDEX ix_whatsapp_cart_customer_status ON whatsapp_cart(customer_ref,status,updated_at DESC);
CREATE UNIQUE INDEX ux_whatsapp_cart_one_open_per_customer ON whatsapp_cart(customer_ref) WHERE status IN ('OPEN','QUOTED');
CREATE TABLE whatsapp_cart_item (
 id UUID PRIMARY KEY,
 cart_id UUID NOT NULL REFERENCES whatsapp_cart(id) ON DELETE RESTRICT,
 product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity > 0),
 updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_whatsapp_cart_item_product UNIQUE(cart_id,product_id)
);
CREATE INDEX ix_whatsapp_cart_item_cart ON whatsapp_cart_item(cart_id);

CREATE TABLE whatsapp_quote (
 id UUID PRIMARY KEY,
 quote_number VARCHAR(48) NOT NULL,
 cart_id UUID NOT NULL REFERENCES whatsapp_cart(id) ON DELETE RESTRICT,
 warehouse_id UUID NOT NULL REFERENCES warehouse(id) ON DELETE RESTRICT,
 status VARCHAR(16) NOT NULL CHECK(status IN ('ACTIVE','ORDERED','EXPIRED')),
 currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
 subtotal NUMERIC(14,2) NOT NULL CHECK(subtotal >= 0),
 tax NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK(tax >= 0),
 total NUMERIC(14,2) NOT NULL CHECK(total >= 0),
 idempotency_key VARCHAR(120) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_whatsapp_quote_number UNIQUE(quote_number),
 CONSTRAINT uk_whatsapp_quote_idempotency UNIQUE(idempotency_key)
);
CREATE INDEX ix_whatsapp_quote_cart_created ON whatsapp_quote(cart_id,created_at DESC);
CREATE TABLE whatsapp_quote_item (
 id UUID PRIMARY KEY,
 quote_id UUID NOT NULL REFERENCES whatsapp_quote(id) ON DELETE RESTRICT,
 product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
 sku_snapshot VARCHAR(64) NOT NULL,
 product_name_snapshot VARCHAR(180) NOT NULL,
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity > 0),
 unit_price NUMERIC(14,2) NOT NULL CHECK(unit_price >= 0),
 line_total NUMERIC(14,2) NOT NULL CHECK(line_total >= 0),
 CONSTRAINT uk_whatsapp_quote_item_product UNIQUE(quote_id,product_id)
);
CREATE INDEX ix_whatsapp_quote_item_quote ON whatsapp_quote_item(quote_id);

CREATE TABLE whatsapp_order (
 id UUID PRIMARY KEY,
 order_number VARCHAR(48) NOT NULL,
 cart_id UUID NOT NULL REFERENCES whatsapp_cart(id) ON DELETE RESTRICT,
 quote_id UUID NOT NULL REFERENCES whatsapp_quote(id) ON DELETE RESTRICT,
 warehouse_id UUID NOT NULL REFERENCES warehouse(id) ON DELETE RESTRICT,
 customer_ref VARCHAR(120) NOT NULL,
 status VARCHAR(24) NOT NULL CHECK(status IN ('AWAITING_PAYMENT','CONFIRMED','CANCELLED','EXPIRED')),
 currency VARCHAR(3) NOT NULL CHECK(currency ~ '^[A-Z]{3}$'),
 subtotal NUMERIC(14,2) NOT NULL CHECK(subtotal >= 0),
 tax NUMERIC(14,2) NOT NULL DEFAULT 0 CHECK(tax >= 0),
 total NUMERIC(14,2) NOT NULL CHECK(total >= 0),
 idempotency_key VARCHAR(120) NOT NULL,
 request_hash VARCHAR(64) NOT NULL,
 expires_at TIMESTAMPTZ NOT NULL,
 created_at TIMESTAMPTZ NOT NULL,
 updated_at TIMESTAMPTZ NOT NULL,
 CONSTRAINT uk_whatsapp_order_number UNIQUE(order_number),
 CONSTRAINT uk_whatsapp_order_quote UNIQUE(quote_id),
 CONSTRAINT uk_whatsapp_order_idempotency UNIQUE(idempotency_key)
);
CREATE INDEX ix_whatsapp_order_status_expiry ON whatsapp_order(status,expires_at);
CREATE INDEX ix_whatsapp_order_customer_created ON whatsapp_order(customer_ref,created_at DESC);
CREATE TABLE whatsapp_order_item (
 id UUID PRIMARY KEY,
 order_id UUID NOT NULL REFERENCES whatsapp_order(id) ON DELETE RESTRICT,
 product_id UUID NOT NULL REFERENCES product(id) ON DELETE RESTRICT,
 sku_snapshot VARCHAR(64) NOT NULL,
 product_name_snapshot VARCHAR(180) NOT NULL,
 quantity NUMERIC(14,3) NOT NULL CHECK(quantity > 0),
 unit_price NUMERIC(14,2) NOT NULL CHECK(unit_price >= 0),
 line_total NUMERIC(14,2) NOT NULL CHECK(line_total >= 0),
 CONSTRAINT uk_whatsapp_order_item_product UNIQUE(order_id,product_id)
);
CREATE INDEX ix_whatsapp_order_item_order ON whatsapp_order_item(order_id);
