package com.ecuapp.inventary.api;

import com.ecuapp.inventary.support.ApiTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class CatalogApiTest extends ApiTestSupport {

    @Test
    void categoriesEnforceCaseInsensitiveNamesSlugsAndActivation() {
        String admin = admin();
        String name = "Bebidas Frías " + suffix();
        JsonNode created = httpPost(admin, "/api/v1/catalog/categories", Map.of("name", name), 201);
        assertTrue(created.get("slug").asText().startsWith("bebidas-frias-"));
        problem(call(post("/api/v1/catalog/categories"), admin, Map.of("name", name.toUpperCase())), 409, "CONFLICT");
        String id = created.get("id").asText();
        JsonNode updated = httpPut(admin, "/api/v1/catalog/categories/" + id, Map.of("name", name + " X", "description", "d"), 200);
        assertEquals("d", updated.get("description").asText());

        httpPost(admin, "/api/v1/catalog/categories/" + id + "/deactivate", null, 204);
        assertFalse(httpGet(admin, "/api/v1/catalog/categories/" + id, 200).get("active").asBoolean());
        problem(call(post("/api/v1/catalog/products"), admin, productBody(id, "SKU-" + suffix())), 409, "CONFLICT");
        assertTrue(httpGet(admin, "/api/v1/catalog/categories?active=false&size=100&sort=createdAt,desc", 200).get("content").toString().contains(id));
        httpPost(admin, "/api/v1/catalog/categories/" + id + "/activate", null, 204);
        assertTrue(httpGet(admin, "/api/v1/catalog/categories/" + id, 200).get("active").asBoolean());
        problem(call(post("/api/v1/catalog/categories"), token("VENDEDOR"), Map.of("name", "x" + suffix())), 403, "ACCESS_DENIED");
        httpGet(token("VENDEDOR"), "/api/v1/catalog/categories", 200);
    }

    @Test
    void unitsAndConversionsAreValidated() {
        String admin = admin();
        String code = "KG" + suffix().substring(0, 6);
        JsonNode kg = httpPost(admin, "/api/v1/catalog/units", Map.of("code", code.toLowerCase(), "name", "Kilogramo", "dimension", "mass", "decimalScale", 3), 201);
        assertEquals(code, kg.get("code").asText());
        assertEquals("MASS", kg.get("dimension").asText());
        problem(call(post("/api/v1/catalog/units"), admin, Map.of("code", code, "name", "dup", "dimension", "MASS", "decimalScale", 3)), 409, "CONFLICT");
        JsonNode g = httpPost(admin, "/api/v1/catalog/units", Map.of("code", "G" + suffix().substring(0, 6), "name", "Gramo", "dimension", "MASS", "decimalScale", 0), 201);
        JsonNode l = httpPost(admin, "/api/v1/catalog/units", Map.of("code", "L" + suffix().substring(0, 6), "name", "Litro", "dimension", "VOLUME", "decimalScale", 3), 201);

        JsonNode conv = httpPost(admin, "/api/v1/catalog/unit-conversions", Map.of("fromUnitId", kg.get("id").asText(), "toUnitId", g.get("id").asText(), "factor", 1000), 201);
        assertAmount("1000", conv.get("factor"));
        problem(call(post("/api/v1/catalog/unit-conversions"), admin, Map.of("fromUnitId", kg.get("id").asText(), "toUnitId", g.get("id").asText(), "factor", 1000)),
            409, "CONFLICT");
        problem(call(post("/api/v1/catalog/unit-conversions"), admin, Map.of("fromUnitId", kg.get("id").asText(), "toUnitId", l.get("id").asText(), "factor", 1)),
            422, "BUSINESS_RULE_VIOLATION");
        assertTrue(httpGet(token("VENDEDOR"), "/api/v1/catalog/unit-conversions", 200).toString().contains(conv.get("id").asText()));

        JsonNode unusedUnit = httpPost(admin, "/api/v1/catalog/units",
            Map.of("code", "DEL" + suffix().substring(0, 6), "name", "Unidad temporal", "dimension", "LENGTH", "decimalScale", 3), 201);
        String unusedUnitId = unusedUnit.get("id").asText();
        httpPut(admin, "/api/v1/catalog/units/" + unusedUnitId, Map.of("name", "Unidad temporal", "decimalScale", 3, "active", false), 200);
        httpDelete(admin, "/api/v1/catalog/units/" + unusedUnitId, 204);
        assertFalse(httpGet(admin, "/api/v1/catalog/units?active=false", 200).toString().contains(unusedUnitId));


        problem(call(put("/api/v1/catalog/units/00000000-0000-0000-0000-000000000001"), admin, Map.of("name", "Unidad", "decimalScale", 3, "active", false)),
            422, "BUSINESS_RULE_VIOLATION");
        JsonNode cat = category();
        Map<String, Object> body = productBody(cat.get("id").asText(), "SKU-" + suffix());
        body.put("unitOfMeasureId", kg.get("id").asText());
        httpPost(admin, "/api/v1/catalog/products", body, 201);
        problem(call(put("/api/v1/catalog/units/" + kg.get("id").asText()), admin, Map.of("name", "Kilogramo", "decimalScale", 3, "active", false)), 409, "CONFLICT");
        assertFalse(httpPut(admin, "/api/v1/catalog/units/" + l.get("id").asText(), Map.of("name", "Litro", "decimalScale", 2, "active", false), 200).get("active").asBoolean());
    }

    @Test
    void attributeSchemasAreVersionedAndEnforcedOnProducts() {
        String admin = admin();
        String categoryId = category().get("id").asText();
        Map<String, Object> schema = Map.of("definitions", List.of(
            Map.of("key", "talla", "displayName", "Talla", "valueType", "ENUM", "required", true, "options", List.of("S", "M", "L")),
            Map.of("key", "peso_gr", "displayName", "Peso", "valueType", "NUMBER", "required", false, "constraints", Map.of("min", 1, "max", 5000)),
            Map.of("key", "colores", "displayName", "Colores", "valueType", "STRING_LIST", "required", false, "options", List.of("rojo", "azul")),
            Map.of("key", "vence", "displayName", "Vence", "valueType", "DATE", "required", false)));
        JsonNode v1 = httpPut(admin, "/api/v1/catalog/categories/" + categoryId + "/attributes", schema, 200);
        assertEquals(1, v1.get("schemaVersion").asInt());
        assertEquals(4, v1.get("definitions").size());
        problem(call(put("/api/v1/catalog/categories/" + categoryId + "/attributes"), admin,
            Map.of("definitions", List.of(Map.of("key", "Mala Clave", "displayName", "x", "valueType", "STRING", "required", false)))), 422, "BUSINESS_RULE_VIOLATION");
        problem(call(put("/api/v1/catalog/categories/" + categoryId + "/attributes"), admin,
            Map.of("definitions", List.of(Map.of("key", "tipo", "displayName", "x", "valueType", "ENUM", "required", false)))), 422, "BUSINESS_RULE_VIOLATION");

        Map<String, Object> ok = productBody(categoryId, "SKU-" + suffix());
        ok.put("attributes", Map.of("talla", "M", "peso_gr", 250, "colores", List.of("rojo"), "vence", "2027-01-31"));
        JsonNode product = httpPost(admin, "/api/v1/catalog/products", ok, 201);
        assertEquals("M", product.get("attributes").get("talla").asText());
        assertEquals(1, product.get("attributeSchemaVersion").asInt());

        for (Map<String, Object> bad : List.<Map<String, Object>>of(
            Map.of("peso_gr", 10),                                  // required talla missing
            Map.of("talla", "XL"),                                 // not an option
            Map.of("talla", "S", "peso_gr", 9999),                 // above max
            Map.of("talla", "S", "desconocido", "x"),              // unknown key
            Map.of("talla", "S", "vence", "31/01/2027"),           // invalid date
            Map.of("talla", "S", "colores", List.of("verde")))) {  // option not allowed
            Map<String, Object> body = productBody(categoryId, "SKU-" + suffix());
            body.put("attributes", bad);
            problem(call(post("/api/v1/catalog/products"), admin, body), 422, "BUSINESS_RULE_VIOLATION");
        }

        JsonNode v2 = httpPut(admin, "/api/v1/catalog/categories/" + categoryId + "/attributes", Map.of("definitions", List.of(
            Map.of("key", "talla", "displayName", "Talla", "valueType", "ENUM", "required", true, "options", List.of("S", "M", "L", "XL")))), 200);
        assertEquals(2, v2.get("schemaVersion").asInt());
        assertEquals(2, httpGet(token("VENDEDOR"), "/api/v1/catalog/categories/" + categoryId + "/attributes", 200).get("schemaVersion").asInt());
        // Updating the product revalidates stored attributes against the active version (peso_gr no longer exists).
        Map<String, Object> update = productBody(categoryId, product.get("sku").asText());
        problem(call(put("/api/v1/catalog/products/" + product.get("id").asText()), admin, update), 422, "BUSINESS_RULE_VIOLATION");
        update.put("attributes", Map.of("talla", "XL"));
        JsonNode updated = httpPut(admin, "/api/v1/catalog/products/" + product.get("id").asText(), update, 200);
        assertEquals(2, updated.get("attributeSchemaVersion").asInt());
        assertEquals(2, jdbc.queryForObject("select count(distinct schema_version) from category_attribute_definition where category_id=?::uuid", Integer.class, categoryId));
    }

    @Test
    void productsKeepSkuImmutableHideCostForSellersAndPersistDeactivation() {
        String admin = admin();
        String categoryId = category().get("id").asText();
        String sku = "sku-" + suffix().toLowerCase();
        Map<String, Object> body = productBody(categoryId, sku);
        JsonNode product = httpPost(admin, "/api/v1/catalog/products", body, 201);
        String id = product.get("id").asText();
        assertEquals(sku.toUpperCase(), product.get("sku").asText());
        assertAmount("3.10", product.get("costPrice"));
        problem(call(post("/api/v1/catalog/products"), admin, productBody(categoryId, sku.toUpperCase())), 409, "CONFLICT");

        JsonNode sellerView = httpGet(token("VENDEDOR"), "/api/v1/catalog/products/" + id, 200);
        assertFalse(sellerView.has("costPrice"), "sellers must not see cost");
        assertTrue(httpGet(token("MANAGER_ALMACEN"), "/api/v1/catalog/products/" + id, 200).has("costPrice"));

        Map<String, Object> renamed = productBody(categoryId, "OTHER-" + suffix());
        problem(call(put("/api/v1/catalog/products/" + id), admin, renamed), 409, "CONFLICT");
        Map<String, Object> badPrice = productBody(categoryId, sku);
        badPrice.put("salePrice", new BigDecimal("1.234"));
        problem(call(put("/api/v1/catalog/products/" + id), admin, badPrice), 400, "VALIDATION_FAILED");
        Map<String, Object> badUrl = productBody(categoryId, sku);
        badUrl.put("imageUrl", "http://insecure.example/img.png");
        problem(call(put("/api/v1/catalog/products/" + id), admin, badUrl), 400, "VALIDATION_FAILED");

        // Regression: deactivation used to run without a transaction and was never persisted.
        httpPost(admin, "/api/v1/catalog/products/" + id + "/deactivate", null, 204);
        assertFalse(httpGet(admin, "/api/v1/catalog/products/" + id, 200).get("active").asBoolean());
        assertFalse(jdbc.queryForObject("select active from product where id=?::uuid", Boolean.class, id));
        httpPost(admin, "/api/v1/catalog/products/" + id + "/activate", null, 204);
        assertTrue(jdbc.queryForObject("select active from product where id=?::uuid", Boolean.class, id));

        JsonNode filtered = httpGet(admin, "/api/v1/catalog/products?q=" + sku.substring(4, 10) + "&categoryId=" + categoryId + "&active=true&kind=STOCKED_GOOD", 200);
        assertEquals(1, filtered.get("page").get("totalElements").asInt());
        assertEquals(0, httpGet(admin, "/api/v1/catalog/products?q=%25_%25&categoryId=" + categoryId, 200).get("page").get("totalElements").asInt(),
            "LIKE wildcards supplied by clients are escaped");
        problem(call(get("/api/v1/catalog/products?sort=costPrice"), admin, null), 400, "INVALID_QUERY");
    }

    @Test
    void deactivationCannotStrandStockOrActiveProductsInAnInactiveCategory() {
        String admin = admin();
        JsonNode category = category();
        String categoryId = category.get("id").asText();
        JsonNode product = product(categoryId, "1.00", "2.00");
        String productId = product.get("id").asText();
        String warehouseId = warehouse().get("id").asText();
        stock(productId, warehouseId, "2", "1.00");

        problem(call(post("/api/v1/catalog/products/" + productId + "/deactivate"), admin, null), 409, "CONFLICT");
        assertTrue(httpGet(admin, "/api/v1/catalog/products/" + productId, 200).get("active").asBoolean());
        problem(call(post("/api/v1/catalog/categories/" + categoryId + "/deactivate"), admin, null), 409, "CONFLICT");
        assertTrue(httpGet(admin, "/api/v1/catalog/categories/" + categoryId, 200).get("active").asBoolean());

        adjust(productId, warehouseId, "DECREASE", "2", null);
        httpPost(admin, "/api/v1/catalog/products/" + productId + "/deactivate", null, 204);
        httpPost(admin, "/api/v1/catalog/categories/" + categoryId + "/deactivate", null, 204);
        assertFalse(httpGet(admin, "/api/v1/catalog/products/" + productId, 200).get("active").asBoolean());
        assertFalse(httpGet(admin, "/api/v1/catalog/categories/" + categoryId, 200).get("active").asBoolean());
    }

    @Test
    void physicalDeletionOnlyAllowsInactiveUnusedProductsAndCategories() {
        String admin = admin();

        JsonNode removableProduct = product("1.00", "2.00");
        String removableProductId = removableProduct.get("id").asText();
        httpPost(admin, "/api/v1/catalog/products/" + removableProductId + "/deactivate", null, 204);
        httpDelete(admin, "/api/v1/catalog/products/" + removableProductId, 204);
        problem(call(get("/api/v1/catalog/products/" + removableProductId), admin, null), 404, "RESOURCE_NOT_FOUND");

        JsonNode movedProduct = product("1.00", "2.00");
        String movedProductId = movedProduct.get("id").asText();
        String warehouseId = warehouse().get("id").asText();
        stock(movedProductId, warehouseId, "1", "1.00");
        adjust(movedProductId, warehouseId, "DECREASE", "1", null);
        httpPost(admin, "/api/v1/catalog/products/" + movedProductId + "/deactivate", null, 204);
        problem(call(delete("/api/v1/catalog/products/" + movedProductId), admin, null), 409, "CONFLICT");
        assertFalse(httpGet(admin, "/api/v1/catalog/products/" + movedProductId, 200).get("active").asBoolean());

        JsonNode removableCategory = category();
        String categoryId = removableCategory.get("id").asText();
        httpPost(admin, "/api/v1/catalog/categories/" + categoryId + "/deactivate", null, 204);
        httpDelete(admin, "/api/v1/catalog/categories/" + categoryId, 204);
        problem(call(get("/api/v1/catalog/categories/" + categoryId), admin, null), 404, "RESOURCE_NOT_FOUND");

        JsonNode usedCategory = category();
        String usedCategoryId = usedCategory.get("id").asText();
        product(usedCategoryId, "1.00", "2.00");
        httpPost(admin, "/api/v1/catalog/categories/" + usedCategoryId + "/deactivate", null, 204);
        problem(call(delete("/api/v1/catalog/categories/" + usedCategoryId), admin, null), 409, "CONFLICT");
    }

    @Test
    void productKindAndUnitCannotChangeOnceStockOrMovementsExist() {
        String admin = admin();
        String categoryId = category().get("id").asText();
        Map<String, Object> body = productBody(categoryId, "SKU-" + suffix());
        JsonNode product = httpPost(admin, "/api/v1/catalog/products", body, 201);
        String id = product.get("id").asText();
        stock(id, warehouse().get("id").asText(), "2", "1.00");

        Map<String, Object> service = new LinkedHashMap<>(body);
        service.put("kind", "SERVICE");
        problem(call(put("/api/v1/catalog/products/" + id), admin, service), 409, "CONFLICT");
        String unit = httpPost(admin, "/api/v1/catalog/units", Map.of("code", "CJ" + suffix().substring(0, 6), "name", "Caja", "dimension", "COUNT", "decimalScale", 0), 201)
            .get("id").asText();
        Map<String, Object> otherUnit = new LinkedHashMap<>(body);
        otherUnit.put("unitOfMeasureId", unit);
        problem(call(put("/api/v1/catalog/products/" + id), admin, otherUnit), 409, "CONFLICT");

        Map<String, Object> serviceBody = productBody(categoryId, "SRV-" + suffix());
        serviceBody.put("kind", "SERVICE");
        JsonNode serviceProduct = httpPost(admin, "/api/v1/catalog/products", serviceBody, 201);
        problem(call(post("/api/v1/inventory/adjustments"), admin, Map.of("productId", serviceProduct.get("id").asText(), "warehouseId", warehouse().get("id").asText(),
            "direction", "INCREASE", "quantity", 1, "reason", "x"), "Idempotency-Key", key()), 422, "BUSINESS_RULE_VIOLATION");
    }

    @Test
    void variantsHaveUniqueSkuBarcodeAndCombinationAndArePubliclyResolvable() {
        String admin = admin();
        String categoryId = category().get("id").asText();
        httpPut(admin, "/api/v1/catalog/categories/" + categoryId + "/attributes", Map.of("definitions", List.of(
            Map.of("key", "talla", "displayName", "Talla", "valueType", "ENUM", "required", false, "options", List.of("S", "M")),
            Map.of("key", "color", "displayName", "Color", "valueType", "STRING", "required", false))), 200);
        JsonNode product = httpPost(admin, "/api/v1/catalog/products", productBody(categoryId, "CAM-" + suffix()), 201);
        String productId = product.get("id").asText();
        String variantSku = "CAM-S-" + suffix();
        String barcode = "786" + suffix().replaceAll("[^0-9]", "1");
        JsonNode variant = httpPost(admin, "/api/v1/catalog/products/" + productId + "/variants",
            Map.of("sku", variantSku, "barcode", barcode, "optionValues", Map.of("talla", "S", "color", "rojo"), "salePrice", new BigDecimal("12.00")), 201);
        assertAmount("12.00", variant.get("effectivePrice"));
        problem(call(post("/api/v1/catalog/products/" + productId + "/variants"), admin,
            Map.of("sku", variantSku, "optionValues", Map.of("talla", "M"))), 409, "CONFLICT");
        problem(call(post("/api/v1/catalog/products/" + productId + "/variants"), admin,
            Map.of("sku", "CAM-X-" + suffix(), "optionValues", Map.of("talla", "S", "color", "rojo"))), 409, "CONFLICT");
        problem(call(post("/api/v1/catalog/products/" + productId + "/variants"), admin,
            Map.of("sku", "CAM-Y-" + suffix(), "barcode", barcode, "optionValues", Map.of("talla", "M"))), 409, "CONFLICT");
        problem(call(post("/api/v1/catalog/products/" + productId + "/variants"), admin,
            Map.of("sku", "CAM-Z-" + suffix(), "optionValues", Map.of("talla", "XXL"))), 422, "BUSINESS_RULE_VIOLATION");
        problem(call(post("/api/v1/catalog/products/" + productId + "/variants"), admin,
            Map.of("sku", product.get("sku").asText(), "optionValues", Map.of("talla", "M"))), 409, "CONFLICT");
        JsonNode m = httpPost(admin, "/api/v1/catalog/products/" + productId + "/variants", Map.of("sku", "CAM-M-" + suffix(), "optionValues", Map.of("talla", "M")), 201);
        assertAmount("9.90", m.get("effectivePrice"));

        JsonNode patched = httpPatch(admin, "/api/v1/catalog/variants/" + m.get("id").asText(), Map.of("optionValues", Map.of("talla", "M", "color", "azul"), "salePrice", 11), 200);
        assertEquals("azul", patched.get("optionValues").get("color").asText());
        assertEquals(2, httpGet(token("VENDEDOR"), "/api/v1/catalog/products/" + productId + "/variants", 200).size());

        JsonNode bySku = httpGet(null, "/api/v1/public/catalog/products/" + variantSku.toLowerCase(), 200);
        assertEquals(productId, bySku.get("id").asText());
        assertEquals(2, bySku.get("variants").size());
        assertEquals(productId, httpGet(null, "/api/v1/public/catalog/products/" + barcode, 200).get("id").asText());

        httpPost(admin, "/api/v1/catalog/variants/" + m.get("id").asText() + "/deactivate", null, 204);
        assertEquals(1, httpGet(null, "/api/v1/public/catalog/products/" + productId, 200).get("variants").size());
        problem(call(get("/api/v1/catalog/variants/" + UUID.randomUUID()), admin, null), 404, "RESOURCE_NOT_FOUND");
    }

    @Test
    void publicCatalogExposesOnlyActiveProductsAndNeverCosts() {
        String admin = admin();
        JsonNode cat = category();
        String categoryId = cat.get("id").asText();
        JsonNode visible = product(categoryId, "2.00", "5.00");
        JsonNode hidden = product(categoryId, "2.00", "5.00");
        httpPost(admin, "/api/v1/catalog/products/" + hidden.get("id").asText() + "/deactivate", null, 204);
        String warehouseId = warehouse().get("id").asText();
        stock(visible.get("id").asText(), warehouseId, "3", "2.00");

        JsonNode page = httpGet(null, "/api/v1/public/catalog/products?categoryId=" + categoryId + "&size=50", 200);
        assertEquals(1, page.get("page").get("totalElements").asInt());
        JsonNode item = page.get("content").get(0);
        assertEquals(visible.get("id").asText(), item.get("id").asText());
        assertTrue(item.get("available").asBoolean());
        String text = page.toString();
        assertFalse(text.contains("costPrice") || text.contains("cost"), "public catalog must never expose cost: " + text);

        problem(call(get("/api/v1/public/catalog/products/" + hidden.get("id").asText()), null, null), 404, "RESOURCE_NOT_FOUND");
        problem(call(get("/api/v1/public/catalog/products?sort=costPrice"), null, null), 400, "INVALID_QUERY");
        assertTrue(httpGet(null, "/api/v1/public/catalog/categories?size=100", 200).toString().contains(categoryId));
        // Deactivating a product evicts the public cache immediately.
        httpPost(admin, "/api/v1/catalog/products/" + visible.get("id").asText() + "/deactivate", null, 204);
        assertEquals(0, httpGet(null, "/api/v1/public/catalog/products?categoryId=" + categoryId, 200).get("page").get("totalElements").asInt());
        problem(call(post("/api/v1/public/catalog/products"), null, Map.of()), 401, null);
    }

    @Test
    void productImagesRequireHttpsKeepSinglePrimaryAndUnpublishIdempotently() {
        String admin = admin();
        JsonNode product = product("1.00", "2.00");
        String base = "/api/v1/catalog/products/" + product.get("id").asText() + "/images";
        JsonNode first = httpPost(admin, base, Map.of("imageUrl", "https://cdn.example.com/a.png", "altText", "A", "sortOrder", 1, "primaryImage", true), 201);
        JsonNode second = httpPost(admin, base, Map.of("imageUrl", "https://cdn.example.com/b.png", "sortOrder", 0, "primaryImage", true), 201);
        JsonNode list = httpGet(token("VENDEDOR"), base, 200);
        assertEquals(2, list.size());
        long primaries = 0;
        for (JsonNode image : list) if (image.get("primaryImage").asBoolean()) primaries++;
        assertEquals(1, primaries);
        assertEquals(second.get("id").asText(), list.get(0).get("id").asText(), "ordered by sortOrder");
        problem(call(post(base), admin, Map.of("imageUrl", "http://cdn.example.com/c.png", "sortOrder", 0, "primaryImage", false)), 400, "VALIDATION_FAILED");
        problem(call(post(base), admin, Map.of("imageUrl", "https://cdn.example.com/a.png", "sortOrder", 0, "primaryImage", false)), 409, "DATA_INTEGRITY_VIOLATION");
        httpPatch(admin, base + "/" + first.get("id").asText(), Map.of("altText", "nuevo", "sortOrder", 5, "primaryImage", true), 200);
        httpDelete(admin, base + "/" + second.get("id").asText(), 204);
        httpDelete(admin, base + "/" + second.get("id").asText(), 204);
        assertEquals(1, httpGet(admin, base, 200).size());
        JsonNode publicDetail = httpGet(null, "/api/v1/public/catalog/products/" + product.get("sku").asText(), 200);
        assertEquals(1, publicDetail.get("images").size());
        problem(call(delete(base + "/" + UUID.randomUUID()), admin, null), 404, "RESOURCE_NOT_FOUND");
        problem(call(post(base), token("VENDEDOR"), Map.of("imageUrl", "https://x.example/a.png", "sortOrder", 0, "primaryImage", false)), 403, "ACCESS_DENIED");
    }

    @Test
    void graphqlRequiresAuthenticationAndLimitsDepthAndComplexity() throws Exception {
        String query = "{\"query\":\"{ publicProducts(page:0,size:5){ items { sku price available } totalElements } }\"}";
        assertEquals(401, mvc.perform(post("/graphql").contentType(MediaType.APPLICATION_JSON).content(query)).andReturn().getResponse().getStatus());
        MvcResult ok = call(post("/graphql"), token("VENDEDOR"), query);
        JsonNode data = expect(ok, 200);
        assertTrue(data.get("data").get("publicProducts").has("items"));
        assertFalse(data.toString().contains("costPrice"));

        StringBuilder aliases = new StringBuilder("{");
        for (int i = 0; i < 60; i++) aliases.append("a").append(i).append(": publicProducts(size:100){ items { sku name price currency } } ");
        aliases.append("}");
        JsonNode tooComplex = expect(call(post("/graphql"), token("VENDEDOR"), json.writeValueAsString(Map.of("query", aliases.toString()))), 200);
        assertTrue(tooComplex.has("errors"), "complexity limit must reject alias fan-out: " + tooComplex);
        assertFalse(tooComplex.has("data") && tooComplex.get("data").has("a0") && !tooComplex.get("data").get("a0").isNull());
    }

    private Map<String, Object> productBody(String categoryId, String sku) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("sku", sku);
        body.put("name", "Producto " + suffix());
        body.put("categoryId", categoryId);
        body.put("costPrice", new BigDecimal("3.10"));
        body.put("salePrice", new BigDecimal("9.90"));
        return body;
    }
}
