ALTER TABLE sale ADD COLUMN customer_id UUID REFERENCES business_partner(id) ON DELETE RESTRICT;
CREATE INDEX ix_sale_customer_sold_at ON sale(customer_id,sold_at DESC);
