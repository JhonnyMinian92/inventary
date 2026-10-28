-- Los servicios normalizan las claves, y los índices funcionales preservan esa invariancia
-- aun cuando existan inserciones concurrentes o un cliente SQL escriba directamente.
CREATE UNIQUE INDEX ux_app_user_username_lower ON app_user (lower(username));
CREATE UNIQUE INDEX ux_category_name_lower ON category (lower(name));
CREATE UNIQUE INDEX ux_product_sku_upper ON product (upper(sku));
CREATE UNIQUE INDEX ux_business_partner_code_upper ON business_partner (upper(code));
