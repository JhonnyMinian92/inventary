-- Markup used to suggest sale prices from purchase costs (25 means cost x 1.25). Prices are no longer typed when creating a product.
ALTER TABLE product ADD COLUMN margin_percent NUMERIC(5,2) NOT NULL DEFAULT 25 CHECK (margin_percent >= 0 AND margin_percent <= 1000);
ALTER TABLE product ALTER COLUMN sale_price SET DEFAULT 0;
