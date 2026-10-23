package com.ecuapp.inventary.api;

import com.ecuapp.inventary.support.ApiTestSupport;
import com.ecuapp.inventary.whatsapp.WhatsAppCommerceService;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class WhatsAppApiTest extends ApiTestSupport {
    private static final String BASE = "/api/v1/integrations/whatsapp";
    @Autowired WhatsAppCommerceService commerce;

    private MvcResult wa(MockHttpServletRequestBuilder request, Object body, String... headers) {
        String[] all = new String[headers.length + 2];
        all[0] = "X-Integration-Key";
        all[1] = INTEGRATION_KEY;
        System.arraycopy(headers, 0, all, 2, headers.length);
        return call(request, null, body, all);
    }

    @Test
    void integrationKeyIsMandatory() {
        problem(call(get(BASE + "/catalog/products"), null, null), 401, "INVALID_CREDENTIALS");
        problem(call(get(BASE + "/catalog/products"), null, null, "X-Integration-Key", "wrong-key-wrong-key-wrong-key-123456"), 401, "INVALID_CREDENTIALS");
        // A user JWT is not an integration credential.
        problem(call(get(BASE + "/catalog/products"), admin(), null), 401, "INVALID_CREDENTIALS");
        expect(wa(get(BASE + "/catalog/products?size=5"), null), 200);
    }

    @Test
    void cartQuoteOrderReserveConfirmPaymentAndCreateSale() {
        String warehouseId = warehouse().get("id").asText();
        JsonNode product = product("2.00", "6.50");
        String productId = product.get("id").asText();
        stock(productId, warehouseId, "5", "2.00");
        String customer = "593990" + (100000 + new Random().nextInt(899999));

        JsonNode detail = expect(wa(get(BASE + "/catalog/products/" + product.get("sku").asText()), null), 200);
        assertTrue(detail.get("available").asBoolean());
        assertFalse(detail.toString().contains("cost"));

        JsonNode cart = expect(wa(post(BASE + "/carts"), Map.of("customerRef", customer)), 200);
        String cartId = cart.get("id").asText();
        assertEquals(cartId, expect(wa(post(BASE + "/carts"), Map.of("customerRef", customer)), 200).get("id").asText(), "open cart is reused");
        expect(wa(put(BASE + "/carts/" + cartId + "/items/" + productId), Map.of("quantity", 2)), 200);
        problem(wa(get(BASE + "/carts/" + cartId + "?customerRef=593000000000"), null), 404, "RESOURCE_NOT_FOUND");
        assertEquals(1, expect(wa(get(BASE + "/carts/" + cartId + "?customerRef=" + customer), null), 200).get("items").size());

        String quoteKey = key();
        JsonNode quote = expect(wa(post(BASE + "/carts/" + cartId + "/quotes"), Map.of("warehouseId", warehouseId), "Idempotency-Key", quoteKey), 201);
        assertAmount("13.00", quote.get("total"));
        assertEquals(quote.get("id").asText(), expect(wa(post(BASE + "/carts/" + cartId + "/quotes"), Map.of("warehouseId", warehouseId), "Idempotency-Key", quoteKey), 201)
            .get("id").asText());

        problem(wa(post(BASE + "/orders"), Map.of("quoteId", quote.get("id").asText(), "consentConfirmed", false), "Idempotency-Key", key()), 400, "VALIDATION_FAILED");
        String orderKey = key();
        JsonNode order = expect(wa(post(BASE + "/orders"), Map.of("quoteId", quote.get("id").asText(), "consentConfirmed", true), "Idempotency-Key", orderKey), 201);
        assertEquals("AWAITING_PAYMENT", order.get("status").asText());
        assertEquals(order.get("id").asText(), expect(wa(post(BASE + "/orders"), Map.of("quoteId", quote.get("id").asText(), "consentConfirmed", true),
            "Idempotency-Key", orderKey), 201).get("id").asText());
        JsonNode reserved = balance(productId, warehouseId);
        assertAmount("2", reserved.get("reserved"));
        assertAmount("3", reserved.get("available"));
        assertAmount("5", reserved.get("onHand"));

        // Staff confirms a verified payment: the reservation is consumed and a WHATSAPP sale is created.
        String orderId = order.get("id").asText();
        problem(call(post("/api/v1/whatsapp-orders/" + orderId + "/confirm-payment"), token("AUDITOR"), Map.of("paymentReference", "TRX-1", "paymentMethod", "TRANSFER"),
            "Idempotency-Key", key()), 403, "ACCESS_DENIED");
        String confirmKey = key();
        JsonNode confirmed = httpPost(token("VENDEDOR"), "/api/v1/whatsapp-orders/" + orderId + "/confirm-payment",
            Map.of("paymentReference", "TRX-" + suffix(), "paymentMethod", "TRANSFER"), 200, "Idempotency-Key", confirmKey);
        assertEquals("CONFIRMED", confirmed.get("status").asText());
        String saleId = confirmed.get("saleId").asText();
        JsonNode sale = httpGet(token("VENDEDOR"), "/api/v1/sales/" + saleId, 200);
        assertEquals("WHATSAPP", sale.get("channel").asText());
        assertAmount("13.00", sale.get("total"));
        JsonNode after = balance(productId, warehouseId);
        assertAmount("0", after.get("reserved"));
        assertAmount("3", after.get("onHand"));
        problem(call(post("/api/v1/whatsapp-orders/" + orderId + "/confirm-payment"), token("VENDEDOR"), Map.of("paymentReference", "OTRA-REF", "paymentMethod", "CASH"),
            "Idempotency-Key", key()), 409, "CONFLICT");
        problem(wa(post(BASE + "/orders/" + orderId + "/cancel"), null), 409, "CONFLICT");
        assertEquals("CONFIRMED", expect(wa(get(BASE + "/orders/" + orderId), null), 200).get("status").asText());
        assertEquals(1, httpGet(token("AUDITOR"), "/api/v1/whatsapp-orders?status=CONFIRMED&customerRef=" + customer.replace("+", "%2B"), 200)
            .get("page").get("totalElements").asInt());
    }

    @Test
    void cancellationReleasesReservationOnceAndCartCanBeReused() {
        String warehouseId = warehouse().get("id").asText();
        String productId = product("1.00", "2.00").get("id").asText();
        stock(productId, warehouseId, "5", "1.00");
        String customer = "wa:" + suffix();
        String cartId = expect(wa(post(BASE + "/carts"), Map.of("customerRef", customer)), 200).get("id").asText();
        expect(wa(put(BASE + "/carts/" + cartId + "/items/" + productId), Map.of("quantity", 2)), 200);
        String quoteId = expect(wa(post(BASE + "/carts/" + cartId + "/quotes"), Map.of("warehouseId", warehouseId), "Idempotency-Key", key()), 201).get("id").asText();
        String orderId = expect(wa(post(BASE + "/orders"), Map.of("quoteId", quoteId, "consentConfirmed", true), "Idempotency-Key", key()), 201).get("id").asText();
        problem(wa(put(BASE + "/carts/" + cartId + "/items/" + productId), Map.of("quantity", 3)), 409, "CONFLICT");

        assertEquals("CANCELLED", expect(wa(post(BASE + "/orders/" + orderId + "/cancel"), null), 200).get("status").asText());
        assertEquals("CANCELLED", expect(wa(post(BASE + "/orders/" + orderId + "/cancel"), null), 200).get("status").asText());
        JsonNode released = balance(productId, warehouseId);
        assertAmount("0", released.get("reserved"));
        assertAmount("5", released.get("available"));
        assertEquals(1, jdbc.queryForObject("select count(*) from stock_movement where reference_id=?::uuid and movement_type='RELEASE'", Integer.class, orderId));
        assertEquals("OPEN", expect(wa(get(BASE + "/carts/" + cartId), null), 200).get("status").asText());
        problem(call(post("/api/v1/whatsapp-orders/" + orderId + "/confirm-payment"), token("VENDEDOR"), Map.of("paymentReference", "TRX-9", "paymentMethod", "CASH"),
            "Idempotency-Key", key()), 409, "CONFLICT");
    }

    @Test
    void quoteRejectsPriceChangesStaleCartsAndExpiredQuotesAndExpiryReleasesReservations() {
        String warehouseId = warehouse().get("id").asText();
        JsonNode product = product("1.00", "2.00");
        String productId = product.get("id").asText();
        stock(productId, warehouseId, "5", "1.00");
        String cartId = expect(wa(post(BASE + "/carts"), Map.of("customerRef", "wa:" + suffix())), 200).get("id").asText();
        expect(wa(put(BASE + "/carts/" + cartId + "/items/" + productId), Map.of("quantity", 1)), 200);
        problem(wa(post(BASE + "/carts/" + cartId + "/quotes"), Map.of("warehouseId", warehouse().get("id").asText()), "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
        String quoteId = expect(wa(post(BASE + "/carts/" + cartId + "/quotes"), Map.of("warehouseId", warehouseId), "Idempotency-Key", key()), 201).get("id").asText();

        Map<String, Object> update = new LinkedHashMap<>();
        update.put("sku", product.get("sku").asText());
        update.put("name", product.get("name").asText());
        update.put("categoryId", product.get("categoryId").asText());
        update.put("costPrice", new BigDecimal("1.00"));
        update.put("salePrice", new BigDecimal("2.50"));
        httpPut(admin(), "/api/v1/catalog/products/" + productId, update, 200);
        problem(wa(post(BASE + "/orders"), Map.of("quoteId", quoteId, "consentConfirmed", true), "Idempotency-Key", key()), 409, "CONFLICT");

        String freshQuote = expect(wa(post(BASE + "/carts/" + cartId + "/quotes"), Map.of("warehouseId", warehouseId), "Idempotency-Key", key()), 201).get("id").asText();
        assertEquals("EXPIRED", expect(wa(get(BASE + "/quotes/" + quoteId), null), 200).get("status").asText());
        jdbc.update("update whatsapp_quote set expires_at=now() - interval '1 minute' where id=?::uuid", freshQuote);
        problem(wa(post(BASE + "/orders"), Map.of("quoteId", freshQuote, "consentConfirmed", true), "Idempotency-Key", key()), 409, "CONFLICT");
        assertEquals("EXPIRED", expect(wa(get(BASE + "/quotes/" + freshQuote), null), 200).get("status").asText(), "expiry is persisted even though the request failed");

        String quote3 = expect(wa(post(BASE + "/carts/" + cartId + "/quotes"), Map.of("warehouseId", warehouseId), "Idempotency-Key", key()), 201).get("id").asText();
        String orderId = expect(wa(post(BASE + "/orders"), Map.of("quoteId", quote3, "consentConfirmed", true), "Idempotency-Key", key()), 201).get("id").asText();
        assertAmount("1", balance(productId, warehouseId).get("reserved"));
        jdbc.update("update whatsapp_order set expires_at=now() - interval '1 minute' where id=?::uuid", orderId);
        problem(call(post("/api/v1/whatsapp-orders/" + orderId + "/confirm-payment"), token("VENDEDOR"), Map.of("paymentReference", "TRX-1", "paymentMethod", "CASH"),
            "Idempotency-Key", key()), 409, "CONFLICT");
        assertTrue(commerce.expireOrders() >= 1);
        assertEquals("EXPIRED", expect(wa(get(BASE + "/orders/" + orderId), null), 200).get("status").asText());
        assertAmount("0", balance(productId, warehouseId).get("reserved"));
        commerce.expireOrders();
        assertEquals(1, jdbc.queryForObject("select count(*) from stock_movement where reference_id=?::uuid and movement_type='RELEASE'", Integer.class, orderId),
            "a reservation is released exactly once");
    }

    @Test
    void twoCustomersCompetingForTheLastUnitsNeverOverReserve() throws Exception {
        String warehouseId = warehouse().get("id").asText();
        String productId = product("1.00", "2.00").get("id").asText();
        stock(productId, warehouseId, "3", "1.00");
        List<String> quotes = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String cartId = expect(wa(post(BASE + "/carts"), Map.of("customerRef", "wa:" + suffix())), 200).get("id").asText();
            expect(wa(put(BASE + "/carts/" + cartId + "/items/" + productId), Map.of("quantity", 2)), 200);
            quotes.add(expect(wa(post(BASE + "/carts/" + cartId + "/quotes"), Map.of("warehouseId", warehouseId), "Idempotency-Key", key()), 201).get("id").asText());
        }
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (String quoteId : quotes) results.add(pool.submit(() -> {
            start.await();
            return wa(post(BASE + "/orders"), Map.of("quoteId", quoteId, "consentConfirmed", true), "Idempotency-Key", key()).getResponse().getStatus();
        }));
        start.countDown();
        List<Integer> statuses = List.of(results.get(0).get(30, TimeUnit.SECONDS), results.get(1).get(30, TimeUnit.SECONDS));
        pool.shutdown();
        assertEquals(1, statuses.stream().filter(s -> s == 201).count(), statuses.toString());
        JsonNode balance = balance(productId, warehouseId);
        assertAmount("2", balance.get("reserved"));
        assertAmount("1", balance.get("available"));
    }
}
