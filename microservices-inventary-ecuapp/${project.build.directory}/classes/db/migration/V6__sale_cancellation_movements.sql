ALTER TABLE stock_movement DROP CONSTRAINT IF EXISTS stock_movement_movement_type_check;
ALTER TABLE stock_movement ADD CONSTRAINT ck_stock_movement_type
 CHECK (movement_type IN ('ADJUSTMENT_IN','ADJUSTMENT_OUT','SALE','SALE_CANCEL_IN','RECEIPT','RETURN_IN','RETURN_OUT','TRANSFER_IN','TRANSFER_OUT','RESERVE','RELEASE'));
CREATE INDEX ix_sales_return_line_sale_item ON sales_return_line(source_sale_item_id);
