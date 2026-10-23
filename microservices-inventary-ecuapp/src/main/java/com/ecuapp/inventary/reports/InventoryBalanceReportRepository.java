package com.ecuapp.inventary.reports;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public class InventoryBalanceReportRepository {
    private static final String DATA_SQL = """
        WITH movement_delta AS (
          SELECT m.product_id,
            COALESCE(SUM(CASE WHEN m.occurred_at < :toExclusive THEN m.signed_quantity ELSE 0 END), 0) AS period_net,
            COALESCE(SUM(CASE WHEN m.occurred_at >= :toExclusive THEN m.signed_quantity ELSE 0 END), 0) AS after_net
          FROM (
            SELECT product_id, occurred_at,
              CASE
                WHEN movement_type IN ('ADJUSTMENT_IN','RECEIPT','RETURN_IN','TRANSFER_IN','SALE_CANCEL_IN') THEN quantity
                WHEN movement_type IN ('ADJUSTMENT_OUT','SALE','RETURN_OUT','TRANSFER_OUT') THEN -quantity
                ELSE 0
              END AS signed_quantity
            FROM stock_movement
            WHERE warehouse_id = :warehouseId
              AND occurred_at >= :fromInclusive AND occurred_at < :now
              AND (CAST(:productId AS UUID) IS NULL OR product_id = CAST(:productId AS UUID))
          ) m
          GROUP BY m.product_id
        ),
        purchase_events AS (
          SELECT grl.product_id, grl.quantity AS quantity, grl.line_total AS amount
          FROM goods_receipt_line grl
          JOIN goods_receipt gr ON gr.id = grl.receipt_id
          WHERE gr.warehouse_id = :warehouseId AND gr.status = 'POSTED'
            AND gr.received_at >= :fromInclusive AND gr.received_at < :toExclusive
            AND (CAST(:productId AS UUID) IS NULL OR grl.product_id = CAST(:productId AS UUID))
          UNION ALL
          SELECT prl.product_id, -prl.quantity, -prl.line_total
          FROM purchase_return_line prl
          JOIN purchase_return pr ON pr.id = prl.purchase_return_id
          WHERE pr.warehouse_id = :warehouseId AND pr.status = 'POSTED'
            AND pr.created_at >= :fromInclusive AND pr.created_at < :toExclusive
            AND (CAST(:productId AS UUID) IS NULL OR prl.product_id = CAST(:productId AS UUID))
        ),
        purchases AS (
          SELECT product_id, COALESCE(SUM(quantity), 0) AS quantity, COALESCE(SUM(amount), 0) AS amount
          FROM purchase_events GROUP BY product_id
        ),
        sales_events AS (
          SELECT si.product_id, si.quantity AS quantity, si.line_total AS amount
          FROM sale_item si
          JOIN sale s ON s.id = si.sale_id
          WHERE s.warehouse_id = :warehouseId
            AND s.sold_at >= :fromInclusive AND s.sold_at < :toExclusive
            AND (CAST(:productId AS UUID) IS NULL OR si.product_id = CAST(:productId AS UUID))
          UNION ALL
          SELECT si.product_id, -si.quantity, -si.line_total
          FROM sale_item si
          JOIN sale s ON s.id = si.sale_id
          WHERE s.warehouse_id = :warehouseId AND s.status = 'CANCELLED'
            AND s.cancellation_approved_at >= :fromInclusive AND s.cancellation_approved_at < :toExclusive
            AND (CAST(:productId AS UUID) IS NULL OR si.product_id = CAST(:productId AS UUID))
          UNION ALL
          SELECT srl.product_id, -srl.quantity, -srl.line_total
          FROM sales_return_line srl
          JOIN sales_return sr ON sr.id = srl.sales_return_id
          WHERE sr.warehouse_id = :warehouseId AND sr.status = 'POSTED'
            AND sr.created_at >= :fromInclusive AND sr.created_at < :toExclusive
            AND (CAST(:productId AS UUID) IS NULL OR srl.product_id = CAST(:productId AS UUID))
        ),
        sales AS (
          SELECT product_id, COALESCE(SUM(quantity), 0) AS quantity, COALESCE(SUM(amount), 0) AS amount
          FROM sales_events GROUP BY product_id
        )
        SELECT p.id AS product_id, p.sku, p.name AS product_name, p.currency,
          (COALESCE(sb.on_hand, 0) - COALESCE(md.after_net, 0) - COALESCE(md.period_net, 0)) AS opening_stock,
          COALESCE(pur.quantity, 0) AS purchased_quantity,
          COALESCE(pur.amount, 0) AS purchase_amount,
          COALESCE(sa.quantity, 0) AS sold_quantity,
          COALESCE(sa.amount, 0) AS sales_amount,
          (COALESCE(sb.on_hand, 0) - COALESCE(md.after_net, 0)) AS closing_stock,
          COALESCE(sb.on_hand, 0) AS current_stock,
          COALESCE(sb.reserved, 0) AS current_reserved,
          (COALESCE(sb.on_hand, 0) - COALESCE(sb.reserved, 0)) AS current_available
        FROM product p
        LEFT JOIN stock_balance sb ON sb.product_id = p.id AND sb.warehouse_id = :warehouseId
        LEFT JOIN movement_delta md ON md.product_id = p.id
        LEFT JOIN purchases pur ON pur.product_id = p.id
        LEFT JOIN sales sa ON sa.product_id = p.id
        WHERE (sb.id IS NOT NULL OR md.product_id IS NOT NULL)
          AND (CAST(:productId AS UUID) IS NULL OR p.id = CAST(:productId AS UUID))
        ORDER BY p.sku ASC
        LIMIT :limit OFFSET :offset
        """;

    private static final String COUNT_SQL = """
        SELECT COUNT(*) FROM (
          SELECT product_id FROM stock_balance WHERE warehouse_id = :warehouseId
          UNION
          SELECT product_id FROM stock_movement
            WHERE warehouse_id = :warehouseId AND occurred_at >= :fromInclusive AND occurred_at < :now
        ) report_products
        WHERE (CAST(:productId AS UUID) IS NULL OR product_id = CAST(:productId AS UUID))
        """;

    private final NamedParameterJdbcTemplate jdbc;

    public InventoryBalanceReportRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<InventoryBalanceReportRow> find(UUID warehouseId, UUID productId, Instant fromInclusive,
                                                 Instant toExclusive, Instant now, int limit, long offset) {
        MapSqlParameterSource parameters = parameters(warehouseId, productId, fromInclusive, toExclusive, now)
                .addValue("limit", limit)
                .addValue("offset", offset);
        return jdbc.query(DATA_SQL, parameters, (rs, rowNum) -> new InventoryBalanceReportRow(
                rs.getObject("product_id", UUID.class),
                rs.getString("sku"),
                rs.getString("product_name"),
                rs.getString("currency"),
                rs.getBigDecimal("opening_stock"),
                rs.getBigDecimal("purchased_quantity"),
                rs.getBigDecimal("purchase_amount"),
                rs.getBigDecimal("sold_quantity"),
                rs.getBigDecimal("sales_amount"),
                rs.getBigDecimal("closing_stock"),
                rs.getBigDecimal("current_stock"),
                rs.getBigDecimal("current_reserved"),
                rs.getBigDecimal("current_available")));
    }

    public long count(UUID warehouseId, UUID productId, Instant fromInclusive, Instant now) {
        Long total = jdbc.queryForObject(COUNT_SQL,
                parameters(warehouseId, productId, fromInclusive, fromInclusive, now),
                Long.class);
        return total == null ? 0L : total;
    }

    private MapSqlParameterSource parameters(UUID warehouseId, UUID productId, Instant fromInclusive,
                                              Instant toExclusive, Instant now) {
        return new MapSqlParameterSource()
                .addValue("warehouseId", warehouseId)
                .addValue("productId", productId, Types.OTHER)
                .addValue("fromInclusive", Timestamp.from(fromInclusive))
                .addValue("toExclusive", Timestamp.from(toExclusive))
                .addValue("now", Timestamp.from(now));
    }
}
