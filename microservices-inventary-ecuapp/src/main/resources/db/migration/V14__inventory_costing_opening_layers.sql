-- Inventory already on hand at activation needs an opening valuation layer.
-- Opening layer costs use current catalog cost as a clearly marked estimate because prior COGS
-- cannot be recreated when historical movements were not cost-layered.
-- It has no source movement: inventing a synthetic physical movement would corrupt historical balances.
ALTER TABLE inventory_cost_layer ALTER COLUMN source_movement_id DROP NOT NULL;

INSERT INTO inventory_cost_layer (
    id, product_id, warehouse_location_id, source_movement_id,
    received_quantity, remaining_quantity, unit_cost, currency, received_at, created_at
)
SELECT
    gen_random_uuid(),
    b.product_id,
    b.location_id,
    NULL,
    b.on_hand - COALESCE(open_layers.quantity, 0),
    b.on_hand - COALESCE(open_layers.quantity, 0),
    p.cost_price::NUMERIC(14,4),
    p.currency,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM stock_balance b
JOIN product p ON p.id = b.product_id
LEFT JOIN LATERAL (
    SELECT SUM(remaining_quantity) AS quantity
    FROM inventory_cost_layer existing
    WHERE existing.product_id = b.product_id
      AND existing.warehouse_location_id = b.location_id
      AND existing.remaining_quantity > 0
) open_layers ON TRUE
WHERE b.on_hand > COALESCE(open_layers.quantity, 0);
