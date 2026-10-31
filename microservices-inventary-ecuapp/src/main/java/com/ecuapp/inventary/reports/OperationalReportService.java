package com.ecuapp.inventary.reports;

import com.ecuapp.inventary.auth.WarehouseScopeService;
import com.ecuapp.inventary.catalog.Product;
import com.ecuapp.inventary.catalog.ProductRepository;
import com.ecuapp.inventary.common.InvalidQueryException;
import com.ecuapp.inventary.common.ResourceNotFoundException;
import com.ecuapp.inventary.inventory.InventoryCostingService;
import com.ecuapp.inventary.warehouse.Warehouse;
import com.ecuapp.inventary.warehouse.WarehouseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.ecuapp.inventary.reports.InventoryBalanceReportRepository.COGS_EXPRESSION;
import static com.ecuapp.inventary.reports.InventoryBalanceReportRepository.INBOUND_TYPES;
import static com.ecuapp.inventary.reports.InventoryBalanceReportRepository.OUTBOUND_TYPES;

/** Valuation, sales, purchases and kardex reports computed in PostgreSQL from documents and ledgers. */
@Service
public class OperationalReportService {
    private static final int MAX_KARDEX_LINES = 2000;
    private static final int MAX_PRODUCT_ROWS = 500;

    private static final String VALUATION_SQL = """
        SELECT p.id AS product_id, p.sku, p.name AS product_name, w.id AS warehouse_id, w.code AS warehouse_code, p.currency,
          sb.on_hand, COALESCE(cs.quantity, 0) AS valued_quantity, COALESCE(cs.total_value, 0) AS total_value
        FROM stock_balance sb
        JOIN product p ON p.id = sb.product_id
        JOIN warehouse w ON w.id = sb.warehouse_id
        LEFT JOIN inventory_cost_state cs ON cs.product_id = sb.product_id AND cs.location_id = sb.location_id
        WHERE (CAST(:warehouseId AS UUID) IS NULL OR sb.warehouse_id = CAST(:warehouseId AS UUID))
          AND (CAST(:productId AS UUID) IS NULL OR sb.product_id = CAST(:productId AS UUID))
          AND (:includeEmpty OR sb.on_hand > 0)
        """;

    private static final String SALES_SUMMARY_SQL = """
        SELECT
          (SELECT COUNT(*) FROM sale s WHERE s.sold_at >= :fromInclusive AND s.sold_at < :toExclusive
             AND (CAST(:warehouseId AS UUID) IS NULL OR s.warehouse_id = CAST(:warehouseId AS UUID))) AS sales_count,
          (SELECT COALESCE(SUM(s.total), 0) FROM sale s WHERE s.sold_at >= :fromInclusive AND s.sold_at < :toExclusive
             AND (CAST(:warehouseId AS UUID) IS NULL OR s.warehouse_id = CAST(:warehouseId AS UUID))) AS gross_sales,
          (SELECT COUNT(*) FROM sale s WHERE s.status = 'CANCELLED' AND s.cancellation_approved_at >= :fromInclusive
             AND s.cancellation_approved_at < :toExclusive
             AND (CAST(:warehouseId AS UUID) IS NULL OR s.warehouse_id = CAST(:warehouseId AS UUID))) AS cancelled_count,
          (SELECT COALESCE(SUM(s.total), 0) FROM sale s WHERE s.status = 'CANCELLED' AND s.cancellation_approved_at >= :fromInclusive
             AND s.cancellation_approved_at < :toExclusive
             AND (CAST(:warehouseId AS UUID) IS NULL OR s.warehouse_id = CAST(:warehouseId AS UUID))) AS cancellations,
          (SELECT COALESCE(SUM(srl.line_total), 0) FROM sales_return_line srl JOIN sales_return sr ON sr.id = srl.sales_return_id
             WHERE sr.status = 'POSTED' AND sr.created_at >= :fromInclusive AND sr.created_at < :toExclusive
             AND (CAST(:warehouseId AS UUID) IS NULL OR sr.warehouse_id = CAST(:warehouseId AS UUID))) AS returns,
          (SELECT COALESCE(SUM(%s), 0) FROM inventory_valuation_entry e JOIN stock_movement sm ON sm.id = e.stock_movement_id
             WHERE sm.occurred_at >= :fromInclusive AND sm.occurred_at < :toExclusive
             AND (CAST(:warehouseId AS UUID) IS NULL OR sm.warehouse_id = CAST(:warehouseId AS UUID))) AS cost_of_sales
        """.formatted(COGS_EXPRESSION);

    private static final String SALES_DAILY_SQL = """
        SELECT CAST(s.sold_at AT TIME ZONE :zone AS DATE) AS day, COUNT(*) AS sales_count, COALESCE(SUM(s.total), 0) AS gross_sales
        FROM sale s
        WHERE s.sold_at >= :fromInclusive AND s.sold_at < :toExclusive
          AND (CAST(:warehouseId AS UUID) IS NULL OR s.warehouse_id = CAST(:warehouseId AS UUID))
        GROUP BY 1 ORDER BY 1
        """;

    private static final String SALES_PRODUCTS_SQL = """
        WITH events AS (
          SELECT si.product_id, si.quantity, si.line_total AS amount FROM sale_item si JOIN sale s ON s.id = si.sale_id
          WHERE s.sold_at >= :fromInclusive AND s.sold_at < :toExclusive
            AND (CAST(:warehouseId AS UUID) IS NULL OR s.warehouse_id = CAST(:warehouseId AS UUID))
          UNION ALL
          SELECT si.product_id, -si.quantity, -si.line_total FROM sale_item si JOIN sale s ON s.id = si.sale_id
          WHERE s.status = 'CANCELLED' AND s.cancellation_approved_at >= :fromInclusive AND s.cancellation_approved_at < :toExclusive
            AND (CAST(:warehouseId AS UUID) IS NULL OR s.warehouse_id = CAST(:warehouseId AS UUID))
          UNION ALL
          SELECT srl.product_id, -srl.quantity, -srl.line_total FROM sales_return_line srl JOIN sales_return sr ON sr.id = srl.sales_return_id
          WHERE sr.status = 'POSTED' AND sr.created_at >= :fromInclusive AND sr.created_at < :toExclusive
            AND (CAST(:warehouseId AS UUID) IS NULL OR sr.warehouse_id = CAST(:warehouseId AS UUID))
        ),
        totals AS (SELECT product_id, SUM(quantity) AS quantity, SUM(amount) AS amount FROM events GROUP BY product_id),
        cogs AS (
          SELECT sm.product_id, SUM(%s) AS cost FROM inventory_valuation_entry e JOIN stock_movement sm ON sm.id = e.stock_movement_id
          WHERE sm.occurred_at >= :fromInclusive AND sm.occurred_at < :toExclusive
            AND (CAST(:warehouseId AS UUID) IS NULL OR sm.warehouse_id = CAST(:warehouseId AS UUID))
          GROUP BY sm.product_id
        )
        SELECT p.id AS product_id, p.sku, p.name AS product_name, p.currency, t.quantity, t.amount,
          ROUND(COALESCE(c.cost, 0), 2) AS cost, ROUND(t.amount - COALESCE(c.cost, 0), 2) AS profit
        FROM totals t JOIN product p ON p.id = t.product_id LEFT JOIN cogs c ON c.product_id = t.product_id
        ORDER BY t.amount DESC, p.sku ASC
        LIMIT :limit
        """.formatted(COGS_EXPRESSION);

    private static final String PURCHASES_SQL = """
        WITH received AS (
          SELECT po.supplier_id, grl.product_id, grl.quantity, grl.line_total AS amount, 0 AS returned_quantity, 0 AS returned_amount
          FROM goods_receipt_line grl JOIN goods_receipt gr ON gr.id = grl.receipt_id JOIN purchase_order po ON po.id = gr.purchase_order_id
          WHERE gr.status = 'POSTED' AND gr.received_at >= :fromInclusive AND gr.received_at < :toExclusive
            AND (CAST(:warehouseId AS UUID) IS NULL OR gr.warehouse_id = CAST(:warehouseId AS UUID))
            AND (CAST(:supplierId AS UUID) IS NULL OR po.supplier_id = CAST(:supplierId AS UUID))
          UNION ALL
          SELECT po.supplier_id, prl.product_id, 0, 0, prl.quantity, prl.line_total
          FROM purchase_return_line prl JOIN purchase_return pr ON pr.id = prl.purchase_return_id JOIN purchase_order po ON po.id = pr.purchase_order_id
          WHERE pr.status = 'POSTED' AND pr.created_at >= :fromInclusive AND pr.created_at < :toExclusive
            AND (CAST(:warehouseId AS UUID) IS NULL OR pr.warehouse_id = CAST(:warehouseId AS UUID))
            AND (CAST(:supplierId AS UUID) IS NULL OR po.supplier_id = CAST(:supplierId AS UUID))
        )
        SELECT r.supplier_id, bp.name AS supplier_name, r.product_id, p.sku, p.name AS product_name, p.currency,
          SUM(r.quantity) AS received_quantity, SUM(r.amount) AS received_amount,
          SUM(r.returned_quantity) AS returned_quantity, SUM(r.returned_amount) AS returned_amount
        FROM received r JOIN business_partner bp ON bp.id = r.supplier_id JOIN product p ON p.id = r.product_id
        GROUP BY r.supplier_id, bp.name, r.product_id, p.sku, p.name, p.currency
        ORDER BY bp.name, p.sku
        LIMIT :limit
        """;

    private static final String PURCHASE_COUNTS_SQL = """
        SELECT
          (SELECT COUNT(*) FROM goods_receipt gr JOIN purchase_order po ON po.id = gr.purchase_order_id
             WHERE gr.status = 'POSTED' AND gr.received_at >= :fromInclusive AND gr.received_at < :toExclusive
             AND (CAST(:warehouseId AS UUID) IS NULL OR gr.warehouse_id = CAST(:warehouseId AS UUID))
             AND (CAST(:supplierId AS UUID) IS NULL OR po.supplier_id = CAST(:supplierId AS UUID))) AS receipts,
          (SELECT COUNT(*) FROM purchase_return pr JOIN purchase_order po ON po.id = pr.purchase_order_id
             WHERE pr.status = 'POSTED' AND pr.created_at >= :fromInclusive AND pr.created_at < :toExclusive
             AND (CAST(:warehouseId AS UUID) IS NULL OR pr.warehouse_id = CAST(:warehouseId AS UUID))
             AND (CAST(:supplierId AS UUID) IS NULL OR po.supplier_id = CAST(:supplierId AS UUID))) AS returns
        """;

    private static final String PURCHASE_ROW_COUNT_SQL = """
        WITH purchase_pairs AS (
          SELECT po.supplier_id, grl.product_id
          FROM goods_receipt_line grl
          JOIN goods_receipt gr ON gr.id = grl.receipt_id
          JOIN purchase_order po ON po.id = gr.purchase_order_id
          WHERE gr.status = 'POSTED' AND gr.received_at >= :fromInclusive AND gr.received_at < :toExclusive
            AND (CAST(:warehouseId AS UUID) IS NULL OR gr.warehouse_id = CAST(:warehouseId AS UUID))
            AND (CAST(:supplierId AS UUID) IS NULL OR po.supplier_id = CAST(:supplierId AS UUID))
          UNION
          SELECT po.supplier_id, prl.product_id
          FROM purchase_return_line prl
          JOIN purchase_return pr ON pr.id = prl.purchase_return_id
          JOIN purchase_order po ON po.id = pr.purchase_order_id
          WHERE pr.status = 'POSTED' AND pr.created_at >= :fromInclusive AND pr.created_at < :toExclusive
            AND (CAST(:warehouseId AS UUID) IS NULL OR pr.warehouse_id = CAST(:warehouseId AS UUID))
            AND (CAST(:supplierId AS UUID) IS NULL OR po.supplier_id = CAST(:supplierId AS UUID))
        )
        SELECT COUNT(*) FROM purchase_pairs
        """;

    private static final String SALES_PRODUCT_COUNT_SQL = """
        WITH product_events AS (
          SELECT si.product_id FROM sale_item si JOIN sale s ON s.id = si.sale_id
          WHERE s.sold_at >= :fromInclusive AND s.sold_at < :toExclusive
            AND (CAST(:warehouseId AS UUID) IS NULL OR s.warehouse_id = CAST(:warehouseId AS UUID))
          UNION
          SELECT si.product_id FROM sale_item si JOIN sale s ON s.id = si.sale_id
          WHERE s.status = 'CANCELLED' AND s.cancellation_approved_at >= :fromInclusive AND s.cancellation_approved_at < :toExclusive
            AND (CAST(:warehouseId AS UUID) IS NULL OR s.warehouse_id = CAST(:warehouseId AS UUID))
          UNION
          SELECT srl.product_id FROM sales_return_line srl JOIN sales_return sr ON sr.id = srl.sales_return_id
          WHERE sr.status = 'POSTED' AND sr.created_at >= :fromInclusive AND sr.created_at < :toExclusive
            AND (CAST(:warehouseId AS UUID) IS NULL OR sr.warehouse_id = CAST(:warehouseId AS UUID))
        )
        SELECT COUNT(*) FROM product_events
        """;

    private static final String KARDEX_OPENING_SQL = """
        SELECT
          COALESCE((SELECT SUM(CASE WHEN movement_type IN (%1$s) THEN quantity WHEN movement_type IN (%2$s) THEN -quantity ELSE 0 END)
             FROM stock_movement WHERE product_id = :productId AND warehouse_id = :warehouseId AND occurred_at >= :fromInclusive), 0) AS since_from,
          COALESCE((SELECT SUM(CASE WHEN sm.movement_type IN (%1$s) THEN e.total_cost WHEN sm.movement_type IN (%2$s) THEN -e.total_cost ELSE 0 END)
             FROM inventory_valuation_entry e JOIN stock_movement sm ON sm.id = e.stock_movement_id
             WHERE sm.product_id = :productId AND sm.warehouse_id = :warehouseId AND sm.occurred_at < :fromInclusive), 0) AS opening_value,
          COALESCE((SELECT on_hand FROM stock_balance WHERE product_id = :productId AND warehouse_id = :warehouseId), 0) AS on_hand
        """.formatted(INBOUND_TYPES, OUTBOUND_TYPES);

    private static final String KARDEX_LINES_SQL = """
        SELECT sm.id, sm.occurred_at, sm.movement_type, sm.reference_type, sm.reference_id, sm.notes, sm.created_by, sm.quantity,
          CASE WHEN sm.movement_type IN (%1$s) THEN 1 WHEN sm.movement_type IN (%2$s) THEN -1 ELSE 0 END AS direction,
          COALESCE((SELECT SUM(e.total_cost) FROM inventory_valuation_entry e WHERE e.stock_movement_id = sm.id), 0) AS value
        FROM stock_movement sm
        WHERE sm.product_id = :productId AND sm.warehouse_id = :warehouseId
          AND sm.occurred_at >= :fromInclusive AND sm.occurred_at < :toExclusive
        ORDER BY sm.occurred_at ASC, sm.id ASC
        LIMIT :limit
        """.formatted(INBOUND_TYPES, OUTBOUND_TYPES);

    private static final String PURCHASE_AMOUNTS_SQL = """
        WITH amounts AS (
          SELECT grl.line_total AS received_amount, 0::numeric AS returned_amount
          FROM goods_receipt_line grl
          JOIN goods_receipt gr ON gr.id = grl.receipt_id
          WHERE gr.status = 'POSTED' AND gr.received_at >= :fromInclusive AND gr.received_at < :toExclusive
            AND (CAST(:warehouseId AS UUID) IS NULL OR gr.warehouse_id = CAST(:warehouseId AS UUID))
            AND (CAST(:supplierId AS UUID) IS NULL OR EXISTS (
              SELECT 1 FROM purchase_order po WHERE po.id = gr.purchase_order_id AND po.supplier_id = CAST(:supplierId AS UUID)
            ))
          UNION ALL
          SELECT 0::numeric AS received_amount, prl.line_total AS returned_amount
          FROM purchase_return_line prl
          JOIN purchase_return pr ON pr.id = prl.purchase_return_id
          JOIN purchase_order po ON po.id = pr.purchase_order_id
          WHERE pr.status = 'POSTED' AND pr.created_at >= :fromInclusive AND pr.created_at < :toExclusive
            AND (CAST(:warehouseId AS UUID) IS NULL OR pr.warehouse_id = CAST(:warehouseId AS UUID))
            AND (CAST(:supplierId AS UUID) IS NULL OR po.supplier_id = CAST(:supplierId AS UUID))
        )
        SELECT COALESCE(SUM(received_amount), 0) AS received_amount,
               COALESCE(SUM(returned_amount), 0) AS returned_amount
        FROM amounts
        """;

    private final NamedParameterJdbcTemplate jdbc;
    private final InventoryCostingService costing;
    private final ProductRepository products;
    private final WarehouseRepository warehouses;
    private final ZoneId defaultZone;
    private final WarehouseScopeService scopes;

    public OperationalReportService(NamedParameterJdbcTemplate jdbc, InventoryCostingService costing, ProductRepository products,
                                    WarehouseRepository warehouses, WarehouseScopeService scopes,
                                    @Value("${app.reports.time-zone:America/Guayaquil}") String defaultZone) {
        this.jdbc = jdbc;
        this.costing = costing;
        this.products = products;
        this.warehouses = warehouses;
        this.scopes = scopes;
        this.defaultZone = ZoneId.of(defaultZone);
    }

    @Transactional(readOnly = true)
    public ReportDtos.ValuationReport valuation(UUID warehouseId, UUID productId, boolean includeEmpty, Pageable pageable,String actor) {
        warehouseId=resolveWarehouseScope(warehouseId,actor);
        if (pageable.getPageNumber() < 0) throw new InvalidQueryException("El número de página no puede ser negativo.");
        int size = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        MapSqlParameterSource params = new MapSqlParameterSource()
            .addValue("warehouseId", warehouseId, Types.OTHER).addValue("productId", productId, Types.OTHER)
            .addValue("includeEmpty", includeEmpty).addValue("limit", size).addValue("offset", (long) pageable.getPageNumber() * size);
        Long total = jdbc.queryForObject("SELECT COUNT(*) FROM (" + VALUATION_SQL + ") v", params, Long.class);
        List<ReportDtos.ValuationRow> rows = jdbc.query(VALUATION_SQL + " ORDER BY w.code, p.sku LIMIT :limit OFFSET :offset", params, (rs, i) -> {
            BigDecimal onHand = rs.getBigDecimal("on_hand");
            BigDecimal valuedQuantity = rs.getBigDecimal("valued_quantity");
            BigDecimal value = rs.getBigDecimal("total_value").setScale(2, RoundingMode.HALF_UP);
            BigDecimal average = valuedQuantity.signum() == 0 ? null : rs.getBigDecimal("total_value").divide(valuedQuantity, 4, RoundingMode.HALF_UP);
            return new ReportDtos.ValuationRow(rs.getObject("product_id", UUID.class), rs.getString("sku"), rs.getString("product_name"),
                rs.getObject("warehouse_id", UUID.class), rs.getString("warehouse_code"), rs.getString("currency"), onHand, valuedQuantity,
                average, value, onHand.compareTo(valuedQuantity) == 0);
        });
        List<ReportDtos.CurrencyTotal> totals = jdbc.query("SELECT currency, SUM(on_hand) AS on_hand, ROUND(SUM(total_value), 2) AS total_value FROM ("
            + VALUATION_SQL + ") v GROUP BY currency ORDER BY currency", params,
            (rs, i) -> new ReportDtos.CurrencyTotal(rs.getString("currency"), rs.getBigDecimal("on_hand"), rs.getBigDecimal("total_value")));
        long count = total == null ? 0 : total;
        int pages = count == 0 ? 0 : (int) ((count + size - 1) / size);
        return new ReportDtos.ValuationReport(costing.policy().getMethod(), Instant.now(), pageable.getPageNumber(), size, count, pages, totals, rows);
    }

    @Transactional(readOnly = true)
    public ReportDtos.SalesReport sales(LocalDate from, LocalDate to, UUID warehouseId, String zone,String actor) {
        warehouseId=resolveWarehouseScope(warehouseId,actor);
        ReportPeriod period = ReportPeriod.of(from, to, zone, defaultZone);
        MapSqlParameterSource params = periodParams(period).addValue("warehouseId", warehouseId, Types.OTHER)
            .addValue("zone", period.zone().getId()).addValue("limit", MAX_PRODUCT_ROWS);
        ReportDtos.SalesSummary summary = jdbc.queryForObject(SALES_SUMMARY_SQL, params, (rs, i) -> {
            BigDecimal gross = rs.getBigDecimal("gross_sales");
            BigDecimal cancellations = rs.getBigDecimal("cancellations");
            BigDecimal returns = rs.getBigDecimal("returns");
            BigDecimal net = gross.subtract(cancellations).subtract(returns);
            BigDecimal cost = rs.getBigDecimal("cost_of_sales").setScale(2, RoundingMode.HALF_UP);
            return new ReportDtos.SalesSummary(rs.getLong("sales_count"), rs.getLong("cancelled_count"), gross, cancellations, returns,
                net, cost, net.subtract(cost));
        });
        List<ReportDtos.DailySales> daily = jdbc.query(SALES_DAILY_SQL, params, (rs, i) ->
            new ReportDtos.DailySales(rs.getDate("day").toLocalDate(), rs.getLong("sales_count"), rs.getBigDecimal("gross_sales")));
        List<ReportDtos.SalesProductRow> productRows = jdbc.query(SALES_PRODUCTS_SQL, params, (rs, i) ->
            new ReportDtos.SalesProductRow(rs.getObject("product_id", UUID.class), rs.getString("sku"), rs.getString("product_name"),
                rs.getString("currency"), rs.getBigDecimal("quantity"), rs.getBigDecimal("amount"), rs.getBigDecimal("cost"), rs.getBigDecimal("profit")));
        Long totalProductsValue = jdbc.queryForObject(SALES_PRODUCT_COUNT_SQL, params, Long.class);
        long totalProducts = totalProductsValue == null ? 0L : totalProductsValue;
        return new ReportDtos.SalesReport(period.from(), period.to(), period.zone().getId(), warehouseId, Instant.now(), summary, daily,
            productRows, totalProducts, totalProducts > productRows.size());
    }

    @Transactional(readOnly = true)
    public ReportDtos.PurchasesReport purchases(LocalDate from, LocalDate to, UUID warehouseId, UUID supplierId, String zone,String actor) {
        warehouseId=resolveWarehouseScope(warehouseId,actor);
        ReportPeriod period = ReportPeriod.of(from, to, zone, defaultZone);
        MapSqlParameterSource params = periodParams(period).addValue("warehouseId", warehouseId, Types.OTHER)
            .addValue("supplierId", supplierId, Types.OTHER).addValue("limit", MAX_PRODUCT_ROWS);
        List<ReportDtos.PurchasesRow> rows = jdbc.query(PURCHASES_SQL, params, (rs, i) -> new ReportDtos.PurchasesRow(
            rs.getObject("supplier_id", UUID.class), rs.getString("supplier_name"), rs.getObject("product_id", UUID.class), rs.getString("sku"),
            rs.getString("product_name"), rs.getString("currency"), rs.getBigDecimal("received_quantity"), rs.getBigDecimal("received_amount"),
            rs.getBigDecimal("returned_quantity"), rs.getBigDecimal("returned_amount")));
        long[] counts = jdbc.queryForObject(PURCHASE_COUNTS_SQL, params, (rs, i) -> new long[]{rs.getLong("receipts"), rs.getLong("returns")});
        BigDecimal[] amounts = jdbc.queryForObject(PURCHASE_AMOUNTS_SQL, params, (rs, i) -> new BigDecimal[]{
            rs.getBigDecimal("received_amount"), rs.getBigDecimal("returned_amount")});
        BigDecimal received = amounts == null || amounts[0] == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : amounts[0].setScale(2, RoundingMode.HALF_UP);
        BigDecimal returned = amounts == null || amounts[1] == null ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            : amounts[1].setScale(2, RoundingMode.HALF_UP);
        ReportDtos.PurchasesSummary summary = new ReportDtos.PurchasesSummary(counts[0], counts[1], received, returned, received.subtract(returned));
        Long totalItemsValue = jdbc.queryForObject(PURCHASE_ROW_COUNT_SQL, params, Long.class);
        long totalItems = totalItemsValue == null ? 0L : totalItemsValue;
        return new ReportDtos.PurchasesReport(period.from(), period.to(), period.zone().getId(), warehouseId, supplierId, Instant.now(),
            summary, rows, totalItems, totalItems > rows.size());
    }

    @Transactional(readOnly = true)
    public ReportDtos.KardexReport kardex(UUID productId, UUID warehouseId, LocalDate from, LocalDate to, String zone,String actor) {
        final UUID scopedWarehouseId = resolveWarehouseScope(warehouseId,actor);
        ReportPeriod period = ReportPeriod.of(from, to, zone, defaultZone);
        Product product = products.findById(productId).orElseThrow(() -> new ResourceNotFoundException("Producto", productId));
        Warehouse warehouse = warehouses.findById(scopedWarehouseId).orElseThrow(() -> new ResourceNotFoundException("Almacén", scopedWarehouseId));
        MapSqlParameterSource params = periodParams(period).addValue("productId", productId).addValue("scopedWarehouseId", scopedWarehouseId)
            .addValue("limit", MAX_KARDEX_LINES + 1);
        BigDecimal[] opening = jdbc.queryForObject(KARDEX_OPENING_SQL, params, (rs, i) -> new BigDecimal[]{
            rs.getBigDecimal("on_hand").subtract(rs.getBigDecimal("since_from")), rs.getBigDecimal("opening_value")});
        List<Object[]> raw = jdbc.query(KARDEX_LINES_SQL, params, (rs, i) -> new Object[]{
            rs.getObject("id", UUID.class), rs.getTimestamp("occurred_at").toInstant(), rs.getString("movement_type"), rs.getString("reference_type"),
            rs.getObject("reference_id", UUID.class), rs.getString("notes"), rs.getString("created_by"), rs.getBigDecimal("quantity"),
            rs.getInt("direction"), rs.getBigDecimal("value")});
        if (raw.size() > MAX_KARDEX_LINES)
            throw new InvalidQueryException("El kardex supera " + MAX_KARDEX_LINES + " movimientos; reduzca el rango de fechas.");
        BigDecimal quantity = opening[0].setScale(3, RoundingMode.HALF_UP);
        BigDecimal value = opening[1].setScale(4, RoundingMode.HALF_UP);
        BigDecimal zeroQ = BigDecimal.ZERO.setScale(3);
        BigDecimal zeroV = BigDecimal.ZERO.setScale(2);
        List<ReportDtos.KardexLine> lines = new ArrayList<>();
        for (Object[] r : raw) {
            int direction = (Integer) r[8];
            BigDecimal qty = (BigDecimal) r[7];
            BigDecimal amount = ((BigDecimal) r[9]).setScale(4, RoundingMode.HALF_UP);
            if (direction > 0) { quantity = quantity.add(qty); value = value.add(amount); }
            else if (direction < 0) { quantity = quantity.subtract(qty); value = value.subtract(amount); }
            lines.add(new ReportDtos.KardexLine((UUID) r[0], (Instant) r[1], (String) r[2], (String) r[3], (UUID) r[4], (String) r[5],
                direction > 0 ? qty : zeroQ, direction < 0 ? qty : zeroQ,
                direction > 0 ? amount.setScale(2, RoundingMode.HALF_UP) : zeroV, direction < 0 ? amount.setScale(2, RoundingMode.HALF_UP) : zeroV,
                quantity, value.setScale(2, RoundingMode.HALF_UP), (String) r[6]));
        }
        return new ReportDtos.KardexReport(product.getId(), product.getSku(), product.getName(), warehouse.getId(), warehouse.getCode(),
            period.from(), period.to(), period.zone().getId(), opening[0].setScale(3, RoundingMode.HALF_UP), opening[1].setScale(2, RoundingMode.HALF_UP),
            quantity, value.setScale(2, RoundingMode.HALF_UP), lines);
    }

    /**
     * Financial/operational reports must never aggregate across a limited user's invisible warehouses.
     * A single assigned warehouse is a safe default; several require an explicit filter.
     */
    private UUID resolveWarehouseScope(UUID requested,String actor) {
        var allowed=scopes.accessibleWarehouseIds(actor);
        if(allowed==null)return requested;
        if(requested!=null) {
            scopes.requireWarehouseAccess(actor,requested);
            return requested;
        }
        if(allowed.isEmpty())throw new ResourceNotFoundException("Almacén","sin ámbito asignado");
        if(allowed.size()>1)throw new InvalidQueryException("Seleccione warehouseId para consultar un informe con varias bodegas asignadas.");
        return allowed.iterator().next();
    }

    private MapSqlParameterSource periodParams(ReportPeriod period) {
        return new MapSqlParameterSource()
            .addValue("fromInclusive", Timestamp.from(period.fromInclusive()))
            .addValue("toExclusive", Timestamp.from(period.toExclusive()));
    }
}
