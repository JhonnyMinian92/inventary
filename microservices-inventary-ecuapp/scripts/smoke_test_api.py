#!/usr/bin/env python3
"""
End-to-end smoke test of every Inventary REST operation over real HTTP.

It drives a complete business cycle (security, catalog, purchasing, inventory, transfers, sales, returns,
WhatsApp, reports, audit, outbox) against a running API, asserts status codes and key figures, and finally
compares the exercised operations with /v3/api-docs to prove that no documented operation was skipped.

Usage (API started with SWAGGER_ENABLED=true so /v3/api-docs is reachable):
  python scripts/smoke_test_api.py --base-url http://localhost:8080 \
      --admin-user admin --admin-password '<bootstrap password>' --integration-key '<WHATSAPP_INTEGRATION_API_KEY>'

All data is created with random business keys (prefix SMK); nothing is deleted, as the ledger is append-only.
Login uses the application-layer encryption the browser uses (requires: pip install cryptography).
Exit code 0 only when every check passes and coverage is complete.
"""
import argparse
import base64
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid
from datetime import date, datetime, timezone, timedelta

RESULTS = []
COVERED = set()


class Api:
    def __init__(self, base):
        self.base = base.rstrip("/")

    def call(self, method, template, expect, token=None, body=None, path=None, query=None, headers=None, raw=None):
        url_path = template
        for key, value in (path or {}).items():
            url_path = url_path.replace("{" + key + "}", str(value))
        url = self.base + url_path + (("?" + urllib.parse.quote(query, safe="=&,:")) if query else "")
        data = None
        hdrs = {"Accept": "application/json"}
        if token:
            hdrs["Authorization"] = "Bearer " + token
        if body is not None:
            data = json.dumps(body).encode("utf-8")
            hdrs["Content-Type"] = "application/json"
        if raw is not None:
            data, hdrs["Content-Type"] = raw
        hdrs.update(headers or {})
        request = urllib.request.Request(url, data=data, method=method, headers=hdrs)
        started = time.perf_counter()
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                status, payload, response_headers = response.status, response.read(), dict(response.headers)
        except urllib.error.HTTPError as error:
            status, payload, response_headers = error.code, error.read(), dict(error.headers)
        elapsed = (time.perf_counter() - started) * 1000
        text = payload.decode("utf-8") if payload else ""
        try:
            parsed = json.loads(text) if text else None
        except json.JSONDecodeError:
            parsed = text
        expected = expect if isinstance(expect, (list, tuple, set)) else [expect]
        ok = status in expected
        if template.startswith("/api/") or template == "/graphql":
            COVERED.add((method, template))
        RESULTS.append({"method": method, "path": template, "status": status, "expected": list(expected), "ok": ok,
                        "ms": round(elapsed, 1), "detail": "" if ok else text[:300]})
        mark = "OK " if ok else "ERR"
        print(f"[{mark}] {method:6} {template:62} -> {status} ({elapsed:.0f} ms)" + ("" if ok else f"  expected {expected}: {text[:200]}"))
        return parsed, status, response_headers


def check(condition, message):
    RESULTS.append({"method": "CHECK", "path": message, "status": 0, "expected": [], "ok": bool(condition), "ms": 0, "detail": ""})
    print(("[OK ] " if condition else "[ERR] ") + "CHECK " + message)


def num(value):
    return round(float(value), 4)


def key():
    return "smk-" + str(uuid.uuid4())


def encrypted_credentials(api, username, password):
    """Client half of ECDH-ES+HKDF-SHA256+A256GCM over a single-use challenge (same contract as the web client)."""
    from cryptography.hazmat.primitives import hashes, serialization
    from cryptography.hazmat.primitives.asymmetric import ec
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    from cryptography.hazmat.primitives.kdf.hkdf import HKDF

    challenge, _, _ = api.call("POST", "/api/v1/auth/login-challenge", 200)
    assert challenge["algorithm"] == "ECDH-ES+HKDF-SHA256+A256GCM", challenge
    server_key = serialization.load_der_public_key(base64.b64decode(challenge["publicKey"]))
    client_key = ec.generate_private_key(ec.SECP256R1())
    shared = client_key.exchange(ec.ECDH(), server_key)
    context = challenge["challengeId"].encode("utf-8")
    key = HKDF(algorithm=hashes.SHA256(), length=32, salt=context, info=b"inventary/login/v1").derive(shared)
    iv = os.urandom(12)
    ciphertext = AESGCM(key).encrypt(iv, json.dumps({"username": username, "password": password}).encode("utf-8"), context)
    public_der = client_key.public_key().public_bytes(serialization.Encoding.DER, serialization.PublicFormat.SubjectPublicKeyInfo)
    return {"challengeId": challenge["challengeId"], "publicKey": base64.b64encode(public_der).decode(),
            "iv": base64.b64encode(iv).decode(), "ciphertext": base64.b64encode(ciphertext).decode()}


def login_call(api, username, password, expect):
    return api.call("POST", "/api/v1/auth/login", expect, body={"encrypted": encrypted_credentials(api, username, password)})


def login(api, username, password):
    """Logs in honoring Retry-After: the login endpoint is rate limited per client address."""
    while True:
        body, status, headers = login_call(api, username, password, [200, 429])
        if status == 200:
            return body["accessToken"]
        wait = int(headers.get("Retry-After", "5"))
        print(f"      rate limited (429), waiting {wait}s as instructed by Retry-After")
        time.sleep(wait)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://localhost:8080")
    parser.add_argument("--admin-user", required=True)
    parser.add_argument("--admin-password", required=True)
    parser.add_argument("--integration-key", required=True)
    parser.add_argument("--report", default=None, help="optional path to write a JSON report")
    args = parser.parse_args()
    api = Api(args.base_url)
    sfx = uuid.uuid4().hex[:8].upper()
    password = "Smoke-Clave-26!"
    today = date.today().isoformat()

    # ------------------------------------------------------------------ health and security basics
    health, _, _ = api.call("GET", "/actuator/health", 200)
    check(health.get("status") == "UP" and "components" not in health, "health is UP without details")
    api.call("GET", "/actuator/info", 401)
    api.call("GET", "/api/v1/sales", 401)
    # 429 is also correct here when a previous run consumed the per-address login window.
    login_call(api, "nadie-" + sfx, "Mala-Clave-123!", [401, 429])
    api.call("POST", "/api/v1/auth/login", [400, 429], body={"username": "nadie-" + sfx, "password": "Mala-Clave-123!"})
    admin = login(api, args.admin_user, args.admin_password)
    me, _, _ = api.call("GET", "/api/v1/auth/me", 200, token=admin)
    check(me["role"] == "SUPER_ADMIN" and "security:manage" in me["permissions"], "admin identity and permissions")

    # ------------------------------------------------------------------ administration
    perms, _, _ = api.call("GET", "/api/v1/admin/permissions", 200, token=admin)
    check(len(perms) >= 20, "permission catalog")
    api.call("GET", "/api/v1/admin/roles", 200, token=admin)
    role_code = "SMK_LECTOR_" + sfx
    api.call("POST", "/api/v1/admin/roles", 201, token=admin, body={"code": role_code, "displayName": "Lector smoke", "permissionCodes": ["catalog:read"]})
    api.call("GET", "/api/v1/admin/roles/{code}", 200, token=admin, path={"code": role_code})
    api.call("PUT", "/api/v1/admin/roles/{code}/permissions", 200, token=admin, path={"code": role_code}, body={"permissionCodes": ["catalog:read", "inventory:read"]})
    api.call("PATCH", "/api/v1/admin/roles/{code}", 200, token=admin, path={"code": role_code}, body={"displayName": "Lector smoke v2", "active": True})

    tokens = {}
    for role in ["MANAGER_ALMACEN", "VENDEDOR", "AUDITOR", "SOPORTE_TI", role_code]:
        username = ("smk." + role.lower().replace("_", "") + "." + sfx).lower()[:80]
        api.call("POST", "/api/v1/admin/users", 201, token=admin, body={"username": username, "password": password, "role": role})
        tokens[role] = login(api, username, password)
    manager, seller, auditor, support = tokens["MANAGER_ALMACEN"], tokens["VENDEDOR"], tokens["AUDITOR"], tokens["SOPORTE_TI"]
    api.call("GET", "/api/v1/catalog/products", 200, token=tokens[role_code])
    api.call("GET", "/api/v1/sales", 403, token=tokens[role_code])
    users, _, _ = api.call("GET", "/api/v1/admin/users", 200, token=admin, query="size=50&sort=createdAt,desc")
    throwaway_name = "smk.tmp." + sfx.lower()
    throwaway, _, _ = api.call("POST", "/api/v1/admin/users", 201, token=admin, body={"username": throwaway_name, "password": password, "role": "VENDEDOR"})
    api.call("GET", "/api/v1/admin/users/{id}", 200, token=admin, path={"id": throwaway["id"]})
    api.call("PUT", "/api/v1/admin/users/{id}/password", 204, token=admin, path={"id": throwaway["id"]}, body={"newPassword": "Reset-Clave-26!"})
    tmp_token = login(api, throwaway_name, "Reset-Clave-26!")
    api.call("PUT", "/api/v1/auth/me/password", 204, token=tmp_token, body={"currentPassword": "Reset-Clave-26!", "newPassword": "Propia-Clave26!"})
    tmp_token = login(api, throwaway_name, "Propia-Clave26!")
    api.call("POST", "/api/v1/auth/logout", 204, token=tmp_token)
    api.call("GET", "/api/v1/auth/me", 401, token=tmp_token)
    api.call("PATCH", "/api/v1/admin/users/{id}", 200, token=admin, path={"id": throwaway["id"]}, body={"role": "VENDEDOR", "active": False})
    api.call("GET", "/api/v1/admin/users", 403, token=seller)

    # ------------------------------------------------------------------ catalog structure
    api.call("GET", "/api/v1/catalog/units", 200, token=seller)
    box, _, _ = api.call("POST", "/api/v1/catalog/units", 201, token=admin, body={"code": "CJ" + sfx[:6], "name": "Caja", "dimension": "COUNT", "decimalScale": 0})
    api.call("PUT", "/api/v1/catalog/units/{id}", 200, token=admin, path={"id": box["id"]}, body={"name": "Caja x12", "decimalScale": 0, "active": True})
    api.call("POST", "/api/v1/catalog/unit-conversions", 201, token=admin,
             body={"fromUnitId": box["id"], "toUnitId": "00000000-0000-0000-0000-000000000001", "factor": 12})
    api.call("GET", "/api/v1/catalog/unit-conversions", 200, token=seller)

    category, _, _ = api.call("POST", "/api/v1/catalog/categories", 201, token=manager, body={"name": "Smoke Ropa " + sfx, "description": "Prendas"})
    cat_id = category["id"]
    api.call("GET", "/api/v1/catalog/categories", 200, token=seller, query="size=5&sort=name")
    api.call("GET", "/api/v1/catalog/categories/{id}", 200, token=seller, path={"id": cat_id})
    api.call("PUT", "/api/v1/catalog/categories/{id}", 200, token=manager, path={"id": cat_id}, body={"name": "Smoke Ropa " + sfx, "description": "Prendas de vestir"})
    api.call("PUT", "/api/v1/catalog/categories/{id}/attributes", 200, token=manager, path={"id": cat_id}, body={"definitions": [
        {"key": "talla", "displayName": "Talla", "valueType": "ENUM", "required": True, "options": ["S", "M", "L"]},
        {"key": "material", "displayName": "Material", "valueType": "STRING", "required": False, "constraints": {"maxLength": 60}}]})
    schema, _, _ = api.call("GET", "/api/v1/catalog/categories/{id}/attributes", 200, token=seller, path={"id": cat_id})
    check(schema["schemaVersion"] == 1 and len(schema["definitions"]) == 2, "attribute schema version 1 published")
    side_cat, _, _ = api.call("POST", "/api/v1/catalog/categories", 201, token=manager, body={"name": "Smoke Temporal " + sfx})
    api.call("POST", "/api/v1/catalog/categories/{id}/deactivate", 204, token=manager, path={"id": side_cat["id"]})
    api.call("POST", "/api/v1/catalog/categories/{id}/activate", 204, token=manager, path={"id": side_cat["id"]})

    product_body = {"sku": "SMK-CAM-" + sfx, "name": "Camiseta smoke", "categoryId": cat_id, "costPrice": 3.00, "salePrice": 10.00,
                    "attributes": {"talla": "M", "material": "algodón"}}
    product, _, _ = api.call("POST", "/api/v1/catalog/products", 201, token=manager, body=product_body)
    pid = product["id"]
    api.call("POST", "/api/v1/catalog/products", 422, token=manager, body={**product_body, "sku": "SMK-BAD-" + sfx, "attributes": {"talla": "XXL"}})
    api.call("POST", "/api/v1/catalog/products", 409, token=manager, body=product_body)
    api.call("GET", "/api/v1/catalog/products", 200, token=seller, query="q=SMK-CAM&categoryId=" + cat_id)
    seller_view, _, _ = api.call("GET", "/api/v1/catalog/products/{id}", 200, token=seller, path={"id": pid})
    check("costPrice" not in seller_view, "seller does not see product cost")
    api.call("PUT", "/api/v1/catalog/products/{id}", 200, token=manager, path={"id": pid}, body={**product_body, "description": "100% algodón"})
    extra, _, _ = api.call("POST", "/api/v1/catalog/products", 201, token=manager, body={**product_body, "sku": "SMK-EXT-" + sfx, "attributes": {"talla": "S"}})
    api.call("POST", "/api/v1/catalog/products/{id}/deactivate", 204, token=manager, path={"id": extra["id"]})
    api.call("POST", "/api/v1/catalog/products/{id}/activate", 204, token=manager, path={"id": extra["id"]})

    image, _, _ = api.call("POST", "/api/v1/catalog/products/{productId}/images", 201, token=manager, path={"productId": pid},
                           body={"imageUrl": "https://cdn.example.com/smoke/" + sfx + ".png", "altText": "Frente", "sortOrder": 0, "primaryImage": True})
    api.call("GET", "/api/v1/catalog/products/{productId}/images", 200, token=seller, path={"productId": pid})
    api.call("PATCH", "/api/v1/catalog/products/{productId}/images/{imageId}", 200, token=manager, path={"productId": pid, "imageId": image["id"]},
             body={"altText": "Vista frontal", "sortOrder": 1, "primaryImage": True})
    second_image, _, _ = api.call("POST", "/api/v1/catalog/products/{productId}/images", 201, token=manager, path={"productId": pid},
                                  body={"imageUrl": "https://cdn.example.com/smoke/" + sfx + "-b.png", "sortOrder": 2, "primaryImage": False})
    api.call("DELETE", "/api/v1/catalog/products/{productId}/images/{imageId}", 204, token=manager, path={"productId": pid, "imageId": second_image["id"]})

    variant, _, _ = api.call("POST", "/api/v1/catalog/products/{productId}/variants", 201, token=manager, path={"productId": pid},
                             body={"sku": "SMK-CAM-L-" + sfx, "barcode": "786" + str(uuid.uuid4().int)[:10], "optionValues": {"talla": "L"}, "salePrice": 11.50})
    api.call("GET", "/api/v1/catalog/products/{productId}/variants", 200, token=seller, path={"productId": pid})
    api.call("GET", "/api/v1/catalog/variants/{variantId}", 200, token=seller, path={"variantId": variant["id"]})
    api.call("PUT", "/api/v1/catalog/variants/{variantId}", 200, token=manager, path={"variantId": variant["id"]}, body={"optionValues": {"talla": "L"}, "salePrice": 12})
    api.call("PATCH", "/api/v1/catalog/variants/{variantId}", 200, token=manager, path={"variantId": variant["id"]}, body={"optionValues": {"talla": "L", "material": "lino"}})
    variant_s, _, _ = api.call("POST", "/api/v1/catalog/products/{productId}/variants", 201, token=manager, path={"productId": pid},
                               body={"sku": "SMK-CAM-S-" + sfx, "optionValues": {"talla": "S"}})
    api.call("POST", "/api/v1/catalog/variants/{variantId}/deactivate", 204, token=manager, path={"variantId": variant_s["id"]})

    # ------------------------------------------------------------------ warehouses and partners
    main_wh, _, _ = api.call("POST", "/api/v1/warehouses", 201, token=manager, body={"code": "SMK-MAIN-" + sfx, "name": "Bodega principal smoke", "address": "Quito"})
    branch_wh, _, _ = api.call("POST", "/api/v1/warehouses", 201, token=manager, body={"code": "SMK-SUC-" + sfx, "name": "Sucursal smoke"})
    wid, bid = main_wh["id"], branch_wh["id"]
    api.call("GET", "/api/v1/warehouses", 200, token=seller, query="active=true&size=10")
    api.call("GET", "/api/v1/warehouses/{id}", 200, token=seller, path={"id": wid})
    api.call("PUT", "/api/v1/warehouses/{id}", 200, token=manager, path={"id": wid}, body={"code": "SMK-MAIN-" + sfx, "name": "Bodega principal", "address": "Quito norte"})
    api.call("POST", "/api/v1/warehouses/{id}/locations", 201, token=manager, path={"id": wid}, body={"code": "A-01", "name": "Pasillo A", "locationType": "PICKING"})
    api.call("GET", "/api/v1/warehouses/{id}/locations", 200, token=seller, path={"id": wid})
    empty_wh, _, _ = api.call("POST", "/api/v1/warehouses", 201, token=manager, body={"code": "SMK-TMP-" + sfx, "name": "Temporal"})
    api.call("POST", "/api/v1/warehouses/{id}/deactivate", 204, token=manager, path={"id": empty_wh["id"]})
    api.call("POST", "/api/v1/warehouses/{id}/activate", 204, token=manager, path={"id": empty_wh["id"]})

    supplier, _, _ = api.call("POST", "/api/v1/suppliers", 201, token=manager, body={"code": "SMK-SUP-" + sfx, "name": "Textiles Andinos", "taxId": "179" + sfx[:7], "email": "ventas@textiles.ec"})
    sid = supplier["id"]
    api.call("GET", "/api/v1/suppliers", 200, token=auditor, query="q=Andinos")
    api.call("GET", "/api/v1/suppliers/{id}", 200, token=auditor, path={"id": sid})
    api.call("PUT", "/api/v1/suppliers/{id}", 200, token=manager, path={"id": sid}, body={"code": "SMK-SUP-" + sfx, "name": "Textiles Andinos S.A.", "phone": "022000000"})
    api.call("POST", "/api/v1/suppliers/{id}/deactivate", 204, token=manager, path={"id": sid})
    api.call("POST", "/api/v1/suppliers/{id}/activate", 204, token=manager, path={"id": sid})
    customer, _, _ = api.call("POST", "/api/v1/customers", 201, token=seller, body={"code": "SMK-CLI-" + sfx, "name": "María Pérez", "phone": "0991234567"})
    cid = customer["id"]
    api.call("GET", "/api/v1/customers", 200, token=seller, query="q=Pérez")
    api.call("GET", "/api/v1/customers/{id}", 200, token=seller, path={"id": cid})
    api.call("PUT", "/api/v1/customers/{id}", 200, token=seller, path={"id": cid}, body={"code": "SMK-CLI-" + sfx, "name": "María Pérez", "email": "maria@example.com"})
    api.call("POST", "/api/v1/customers/{id}/deactivate", 204, token=seller, path={"id": cid})
    api.call("POST", "/api/v1/customers/{id}/activate", 204, token=seller, path={"id": cid})

    # ------------------------------------------------------------------ purchasing (PENDIENTES.md scenario: buy 10, sell all, buy 15)
    def approved_order(quantity, unit_cost):
        order, _, _ = api.call("POST", "/api/v1/purchase-orders", 201, token=manager, headers={"Idempotency-Key": key()},
                               body={"supplierId": sid, "warehouseId": wid, "items": [{"productId": pid, "quantity": quantity, "unitCost": unit_cost}]})
        api.call("POST", "/api/v1/purchase-orders/{id}/submit", 200, token=manager, path={"id": order["id"]})
        api.call("POST", "/api/v1/purchase-orders/{id}/approve", 403, token=manager, path={"id": order["id"]})
        return api.call("POST", "/api/v1/purchase-orders/{id}/approve", 200, token=admin, path={"id": order["id"]})[0]

    order1 = approved_order(10, 4.00)
    receipt1, _, _ = api.call("POST", "/api/v1/purchase-orders/{id}/receipts", 201, token=manager, path={"id": order1["id"]}, headers={"Idempotency-Key": key()},
                              body={"items": [{"purchaseOrderLineId": order1["items"][0]["id"], "quantity": 10}]})
    api.call("GET", "/api/v1/purchase-orders/{id}", 200, token=auditor, path={"id": order1["id"]})
    api.call("GET", "/api/v1/purchase-orders", 200, token=auditor, query="supplierId=" + sid + "&status=RECEIVED")
    api.call("GET", "/api/v1/goods-receipts", 200, token=auditor, query="purchaseOrderId=" + order1["id"])
    api.call("GET", "/api/v1/goods-receipts/{id}", 200, token=auditor, path={"id": receipt1["id"]})
    draft, _, _ = api.call("POST", "/api/v1/purchase-orders", 201, token=manager, headers={"Idempotency-Key": key()},
                           body={"supplierId": sid, "warehouseId": wid, "items": [{"productId": pid, "quantity": 1, "unitCost": 4}]})
    api.call("POST", "/api/v1/purchase-orders/{id}/cancel", 200, token=manager, path={"id": draft["id"]})

    sale1, _, _ = api.call("POST", "/api/v1/sales", 201, token=seller, headers={"Idempotency-Key": key()},
                           body={"warehouseId": wid, "channel": "POS", "customerId": cid, "items": [{"productId": pid, "quantity": 10}]})
    bal, _, _ = api.call("GET", "/api/v1/inventory/balances/{productId}/{warehouseId}", 200, token=seller, path={"productId": pid, "warehouseId": wid})
    check(num(bal["onHand"]) == 0, "stock is 0 after selling everything")
    api.call("POST", "/api/v1/sales", 422, token=seller, headers={"Idempotency-Key": key()},
             body={"warehouseId": wid, "channel": "POS", "items": [{"productId": pid, "quantity": 1}]})
    order2 = approved_order(15, 5.00)
    receipt2, _, _ = api.call("POST", "/api/v1/purchase-orders/{id}/receipts", 201, token=manager, path={"id": order2["id"]}, headers={"Idempotency-Key": key()},
                              body={"items": [{"purchaseOrderLineId": order2["items"][0]["id"], "quantity": 15}]})
    bal, _, _ = api.call("GET", "/api/v1/inventory/balances/{productId}/{warehouseId}", 200, token=seller, path={"productId": pid, "warehouseId": wid})
    check(num(bal["onHand"]) == 15, "the same product is restocked with 15 units")
    preturn, _, _ = api.call("POST", "/api/v1/purchase-returns", 201, token=manager, headers={"Idempotency-Key": key()},
                             body={"goodsReceiptId": receipt2["id"], "reason": "Costura defectuosa", "items": [{"receiptLineId": receipt2["items"][0]["id"], "quantity": 1}]})
    api.call("GET", "/api/v1/purchase-returns", 200, token=auditor, query="goodsReceiptId=" + receipt2["id"])
    api.call("GET", "/api/v1/purchase-returns/{id}", 200, token=auditor, path={"id": preturn["id"]})

    # ------------------------------------------------------------------ inventory operations
    adj_key = key()
    adj_body = {"productId": pid, "warehouseId": wid, "direction": "INCREASE", "quantity": 2, "reason": "Hallazgo en bodega", "unitCost": 5}
    adj, _, _ = api.call("POST", "/api/v1/inventory/adjustments", 201, token=manager, headers={"Idempotency-Key": adj_key}, body=adj_body)
    replay, _, _ = api.call("POST", "/api/v1/inventory/adjustments", 201, token=manager, headers={"Idempotency-Key": adj_key}, body=adj_body)
    check(adj["id"] == replay["id"], "adjustment replay returns the same movement")
    api.call("POST", "/api/v1/inventory/adjustments", 409, token=manager, headers={"Idempotency-Key": adj_key}, body={**adj_body, "quantity": 3})
    api.call("GET", "/api/v1/inventory/balances", 200, token=seller, query="warehouseId=" + wid)
    avail, _, _ = api.call("GET", "/api/v1/inventory/availability", 200, token=seller, query="productId=" + pid + "&warehouseId=" + wid + "&quantity=5")
    check(avail["sufficient"] is True, "availability check")
    movements, _, _ = api.call("GET", "/api/v1/inventory/movements", 200, token=manager, query="productId=" + pid + "&warehouseId=" + wid + "&size=50")
    api.call("GET", "/api/v1/inventory/movements/{id}", 200, token=auditor, path={"id": adj["id"]})
    count, _, _ = api.call("POST", "/api/v1/inventory/counts", 201, token=manager, headers={"Idempotency-Key": key()},
                           body={"warehouseId": wid, "items": [{"productId": pid, "countedQuantity": 15}]})
    api.call("GET", "/api/v1/inventory/counts", 200, token=auditor, query="warehouseId=" + wid)
    api.call("GET", "/api/v1/inventory/counts/{id}", 200, token=auditor, path={"id": count["id"]})
    api.call("POST", "/api/v1/inventory/counts/{id}/post", 200, token=manager, path={"id": count["id"]})
    count2, _, _ = api.call("POST", "/api/v1/inventory/counts", 201, token=manager, headers={"Idempotency-Key": key()},
                            body={"warehouseId": wid, "items": [{"productId": pid, "countedQuantity": 1}]})
    api.call("POST", "/api/v1/inventory/counts/{id}/cancel", 200, token=manager, path={"id": count2["id"]})
    rule, _, _ = api.call("POST", "/api/v1/inventory/reorder-rules", 201, token=manager,
                          body={"productId": pid, "warehouseId": wid, "minimumQuantity": 20, "targetQuantity": 40, "leadTimeDays": 5, "preferredSupplierId": sid})
    api.call("GET", "/api/v1/inventory/reorder-rules", 200, token=auditor, query="warehouseId=" + wid)
    api.call("PUT", "/api/v1/inventory/reorder-rules/{id}", 200, token=manager, path={"id": rule["id"]},
             body={"productId": pid, "warehouseId": wid, "minimumQuantity": 20, "targetQuantity": 50, "leadTimeDays": 3, "preferredSupplierId": sid})
    suggestions, _, _ = api.call("GET", "/api/v1/inventory/replenishment-suggestions", 200, token=auditor)
    check(any(s["productId"] == pid for s in suggestions), "replenishment suggestion for low stock")
    api.call("GET", "/api/v1/reports/low-stock", 200, token=admin, query="warehouseId=" + wid)
    api.call("POST", "/api/v1/inventory/reorder-rules/{id}/deactivate", 204, token=manager, path={"id": rule["id"]})

    # ------------------------------------------------------------------ transfers
    transfer, _, _ = api.call("POST", "/api/v1/inventory/transfers", 201, token=manager, headers={"Idempotency-Key": key()},
                              body={"sourceWarehouseId": wid, "destinationWarehouseId": bid, "items": [{"productId": pid, "quantity": 3}]})
    api.call("GET", "/api/v1/inventory/transfers", 200, token=auditor, query="sourceWarehouseId=" + wid)
    api.call("GET", "/api/v1/inventory/transfers/{id}", 200, token=auditor, path={"id": transfer["id"]})
    api.call("POST", "/api/v1/inventory/transfers/{id}/dispatch", 200, token=manager, path={"id": transfer["id"]}, headers={"Idempotency-Key": key()})
    api.call("POST", "/api/v1/inventory/transfers/{id}/receive", 200, token=manager, path={"id": transfer["id"]}, headers={"Idempotency-Key": key()})
    api.call("POST", "/api/v1/inventory/transfers/{id}/reverse", 200, token=manager, path={"id": transfer["id"]}, headers={"Idempotency-Key": key()},
             body={"reason": "Destino incorrecto"})
    transfer2, _, _ = api.call("POST", "/api/v1/inventory/transfers", 201, token=manager, headers={"Idempotency-Key": key()},
                               body={"sourceWarehouseId": wid, "destinationWarehouseId": bid, "items": [{"productId": pid, "quantity": 1}]})
    api.call("POST", "/api/v1/inventory/transfers/{id}/dispatch", 200, token=manager, path={"id": transfer2["id"]}, headers={"Idempotency-Key": key()})
    api.call("POST", "/api/v1/inventory/transfers/{id}/cancel", 200, token=manager, path={"id": transfer2["id"]}, headers={"Idempotency-Key": key()},
             body={"reason": "Vehículo no disponible"})
    bal, _, _ = api.call("GET", "/api/v1/inventory/balances/{productId}/{warehouseId}", 200, token=seller, path={"productId": pid, "warehouseId": wid})
    check(num(bal["onHand"]) == 15, "transfers reversed/cancelled keep the source stock intact")

    # ------------------------------------------------------------------ sales, cancellation, returns
    sale2, _, _ = api.call("POST", "/api/v1/sales", 201, token=seller, headers={"Idempotency-Key": key()},
                           body={"warehouseId": wid, "channel": "WEB", "customerName": "Cliente web", "items": [{"productId": pid, "quantity": 2}]})
    api.call("GET", "/api/v1/sales", 200, token=auditor, query="warehouseId=" + wid + "&status=COMPLETED")
    api.call("GET", "/api/v1/sales/{id}", 200, token=auditor, path={"id": sale2["id"]})
    api.call("POST", "/api/v1/sales/{id}/request-cancellation", 200, token=seller, path={"id": sale2["id"]}, headers={"Idempotency-Key": key()},
             body={"reason": "Pago rechazado"})
    api.call("POST", "/api/v1/sales/{id}/approve-cancellation", 403, token=seller, path={"id": sale2["id"]}, headers={"Idempotency-Key": key()})
    cancelled, _, _ = api.call("POST", "/api/v1/sales/{id}/approve-cancellation", 200, token=admin, path={"id": sale2["id"]}, headers={"Idempotency-Key": key()})
    check(cancelled["status"] == "CANCELLED", "sale cancelled by a distinct approver")
    sreturn, _, _ = api.call("POST", "/api/v1/sales-returns", 201, token=seller, headers={"Idempotency-Key": key()},
                             body={"sourceSaleId": sale1["id"], "reason": "Talla incorrecta", "items": [{"saleItemId": sale1["items"][0]["id"], "quantity": 2, "disposition": "RESTOCK"}]})
    api.call("GET", "/api/v1/sales-returns", 200, token=auditor, query="sourceSaleId=" + sale1["id"])
    api.call("GET", "/api/v1/sales-returns/{id}", 200, token=auditor, path={"id": sreturn["id"]})

    # ------------------------------------------------------------------ public catalog, GraphQL and WhatsApp
    pub, _, _ = api.call("GET", "/api/v1/public/catalog/products", 200, query="categoryId=" + cat_id)
    check("cost" not in json.dumps(pub).lower(), "public catalog never exposes cost")
    detail, _, _ = api.call("GET", "/api/v1/public/catalog/products/{identifier}", 200, path={"identifier": product_body["sku"]})
    check(len(detail["variants"]) == 1 and len(detail["images"]) == 1, "public detail lists active variants and published images")
    api.call("GET", "/api/v1/public/catalog/categories", 200)
    gql, _, _ = api.call("POST", "/graphql", 200, token=seller, body={"query": "{ publicProducts(page:0,size:3){ items { sku price available } totalElements } }"})
    check("errors" not in gql, "GraphQL public catalog query")

    wa = {"X-Integration-Key": args.integration_key}
    api.call("GET", "/api/v1/integrations/whatsapp/catalog/products", 401)
    api.call("GET", "/api/v1/integrations/whatsapp/catalog/products", 200, headers=wa, query="q=SMK-CAM")
    api.call("GET", "/api/v1/integrations/whatsapp/catalog/products/{identifier}", 200, headers=wa, path={"identifier": pid})
    customer_ref = "59399" + str(uuid.uuid4().int)[:7]
    cart, _, _ = api.call("POST", "/api/v1/integrations/whatsapp/carts", 200, headers=wa, body={"customerRef": customer_ref})
    api.call("PUT", "/api/v1/integrations/whatsapp/carts/{cartId}/items/{productId}", 200, headers=wa, path={"cartId": cart["id"], "productId": pid}, body={"quantity": 3})
    api.call("PUT", "/api/v1/integrations/whatsapp/carts/{cartId}/items/{productId}", 200, headers=wa, path={"cartId": cart["id"], "productId": extra["id"]}, body={"quantity": 1})
    api.call("DELETE", "/api/v1/integrations/whatsapp/carts/{cartId}/items/{productId}", 200, headers=wa, path={"cartId": cart["id"], "productId": extra["id"]})
    api.call("GET", "/api/v1/integrations/whatsapp/carts/{cartId}", 200, headers=wa, path={"cartId": cart["id"]}, query="customerRef=" + customer_ref)
    quote, _, _ = api.call("POST", "/api/v1/integrations/whatsapp/carts/{cartId}/quotes", 201, headers={**wa, "Idempotency-Key": key()}, path={"cartId": cart["id"]},
                           body={"warehouseId": wid})
    api.call("GET", "/api/v1/integrations/whatsapp/quotes/{quoteId}", 200, headers=wa, path={"quoteId": quote["id"]})
    order, _, _ = api.call("POST", "/api/v1/integrations/whatsapp/orders", 201, headers={**wa, "Idempotency-Key": key()}, body={"quoteId": quote["id"], "consentConfirmed": True})
    api.call("GET", "/api/v1/integrations/whatsapp/orders/{orderId}", 200, headers=wa, path={"orderId": order["id"]})
    api.call("POST", "/api/v1/integrations/whatsapp/orders/{orderId}/cancel", 200, headers=wa, path={"orderId": order["id"]})
    quote2, _, _ = api.call("POST", "/api/v1/integrations/whatsapp/carts/{cartId}/quotes", 201, headers={**wa, "Idempotency-Key": key()}, path={"cartId": cart["id"]},
                            body={"warehouseId": wid})
    order2, _, _ = api.call("POST", "/api/v1/integrations/whatsapp/orders", 201, headers={**wa, "Idempotency-Key": key()}, body={"quoteId": quote2["id"], "consentConfirmed": True})
    api.call("GET", "/api/v1/whatsapp-orders", 200, token=auditor, query="customerRef=" + customer_ref)
    api.call("GET", "/api/v1/whatsapp-orders/{id}", 200, token=auditor, path={"id": order2["id"]})
    confirmed, _, _ = api.call("POST", "/api/v1/whatsapp-orders/{id}/confirm-payment", 200, token=seller, path={"id": order2["id"]}, headers={"Idempotency-Key": key()},
                               body={"paymentReference": "TRX-" + sfx, "paymentMethod": "TRANSFER"})
    check(confirmed["status"] == "CONFIRMED" and confirmed.get("saleId"), "WhatsApp order confirmed into a sale")

    # ------------------------------------------------------------------ reports, costing, audit, operations
    report, _, _ = api.call("GET", "/api/v1/reports/inventory-balance", 200, token=admin, query="from=" + today + "&to=" + today + "&warehouseId=" + wid)
    row = next(r for r in report["items"] if r["productId"] == pid)
    print("      inventory-balance row:", json.dumps(row, ensure_ascii=False))
    check(num(row["openingStock"]) == 0 and num(row["purchasedQuantity"]) == 24 and num(row["closingStock"]) == num(row["currentStock"]),
          "consolidated report: opening 0, purchased 25-1 returned, closing = current")
    check(num(row["closingValue"]) > 0 and num(row["grossProfit"]) > 0, "consolidated report carries valuation and gross profit")
    api.call("GET", "/api/v1/reports/valuation", 200, token=admin, query="warehouseId=" + wid)
    api.call("GET", "/api/v1/reports/sales", 200, token=admin, query="from=" + today + "&to=" + today + "&warehouseId=" + wid)
    api.call("GET", "/api/v1/reports/purchases", 200, token=admin, query="from=" + today + "&to=" + today + "&supplierId=" + sid)
    kardex, _, _ = api.call("GET", "/api/v1/reports/kardex", 200, token=admin, query="productId=" + pid + "&warehouseId=" + wid + "&from=" + today + "&to=" + today)
    check(num(kardex["closingQuantity"]) == num(row["currentStock"]), "kardex closing quantity matches stock")
    api.call("GET", "/api/v1/reports/sales", 403, token=seller, query="from=" + today + "&to=" + today)
    policy, _, _ = api.call("GET", "/api/v1/costing/policy", 200, token=admin)
    api.call("PUT", "/api/v1/costing/policy", 200, token=admin, body={"method": policy["method"]})

    events, _, _ = api.call("GET", "/api/v1/audit-events", 200, token=auditor, query="resourceType=SALE&size=5")
    if events["content"]:
        api.call("GET", "/api/v1/audit-events/{id}", 200, token=auditor, path={"id": events["content"][0]["id"]})
    stats, _, _ = api.call("GET", "/api/v1/operations/outbox/stats", 200, token=support)
    outbox, _, _ = api.call("GET", "/api/v1/operations/outbox", 200, token=support, query="aggregateType=SALE&aggregateId=" + sale1["id"])
    if outbox["content"]:
        event_id = outbox["content"][0]["id"]
        api.call("GET", "/api/v1/operations/outbox/{id}", 200, token=support, path={"id": event_id})
        api.call("POST", "/api/v1/operations/outbox/{id}/retry", [200, 409], token=support, path={"id": event_id})
    api.call("GET", "/actuator/info", 200, token=support)
    api.call("GET", "/actuator/metrics/inventary.outbox.events", 200, token=support)

    # ------------------------------------------------------------------ coverage against OpenAPI
    docs, status, _ = api.call("GET", "/v3/api-docs", 200)
    documented = set()
    for path_template, item in (docs or {}).get("paths", {}).items():
        for method in item:
            documented.add((method.upper(), path_template))
    missing = sorted(documented - COVERED)
    failures = [r for r in RESULTS if not r["ok"]]
    calls = [r for r in RESULTS if r["method"] != "CHECK"]
    print("\n================ SUMMARY ================")
    print(f"HTTP calls: {len(calls)}  checks: {len(RESULTS) - len(calls)}  failures: {len(failures)}")
    print(f"Documented operations: {len(documented)}  covered: {len(documented & COVERED)}  missing: {len(missing)}")
    for m in missing:
        print("  NOT COVERED:", m)
    for f in failures:
        print("  FAILED:", f["method"], f["path"], f["status"], f["detail"])
    if args.report:
        with open(args.report, "w", encoding="utf-8") as fh:
            json.dump({"generatedAt": datetime.now(timezone.utc).isoformat(), "baseUrl": args.base_url, "documented": len(documented),
                       "covered": len(documented & COVERED), "missing": missing, "results": RESULTS}, fh, ensure_ascii=False, indent=1)
    return 0 if not failures and not missing else 1


if __name__ == "__main__":
    sys.exit(main())
