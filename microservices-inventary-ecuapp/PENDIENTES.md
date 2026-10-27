# Pendientes vigentes: balance, valoración y cierre

**Actualizado:** 2026-10-26. La versión histórica trataba el balance compras/ventas/saldo como necesidad por implementar. Esa afirmación está obsoleta: el código actual contiene GET /api/v1/reports/inventory-balance y el frontend consume ese endpoint. Este archivo conserva el escenario de negocio como regresión obligatoria.

## 1. Comportamiento presente en código

El reporte requiere from, to y warehouseId; admite productId y zone; entrega páginas con saldos/valores de apertura/cierre, compras, ventas netas, costo de ventas, utilidad y margen según el DTO. Ver [25 — Referencia actual](docs/25-referencia-apis.md).

El backend también incluye costeo FIFO/promedio, capas y ledger de valoración conectados a entradas/salidas, cancelaciones, devoluciones y transferencias. La migración V14 crea capas de apertura para existencias anteriores usando product.costPrice como estimación, lo cual no reconstruye el costo histórico real.

## 2. Escenario obligatorio: agotar y reponer el mismo producto

1. Recibir 10 unidades.
2. Vender las 10 unidades; saldo queda en cero.
3. Recibir 15 unidades del mismo producto.
4. Comprobar el reporte en rangos anteriores y posteriores a la reposición.
5. Registrar devolución de cliente y comprobar ventas netas, stock físico, costo y kardex.
6. Repetir con cancelación aprobada y movimientos fuera del periodo.

El informe histórico de 2026-10-23 documenta 25 unidades compradas, 8 vendidas netas de devolución y 17 unidades al cierre. Ejecutar de nuevo en el SHA de release; no asumir que los datos previos certifican el HEAD.

## 3. Pendientes para declarar validado

- Ejecutar Java 21 + PostgreSQL 16, mvnw verify y pruebas HTTP/RBAC en el HEAD exacto.
- Validar Flyway V1–V14 desde base vacía y upgrade V13→V14.
- Ejecutar escenario agotamiento → compra posterior del mismo SKU → devolución → reporte.
- Ejecutar build frontend y añadir pruebas de componentes/E2E.
- Ejecutar Docker Compose de forma real.
- Automatizar CI en push/pull_request, exigir checks verdes y proteger master.
- Conciliar costo de apertura cuando la base ya contenía stock antes de V14.

## 4. Límites conocidos

- Periodo de balance limitado (366 días) y size máximo efectivo 100.
- Stock actual puede diferir del cierre histórico por movimientos posteriores.
- Costo de apertura de existencias previas a V14 es estimado.
- No cambiar método de valoración después de movimientos valorados sin migración planificada.
- No incluye pasarela/reembolso, SRI/impuestos completos, multiempresa, stock por variante, multi-bin, consumidores Kafka o rate limit distribuido.

## 5. Documentación autoritativa

[Estado actual](docs/24-estado-actual-repositorio.md) · [Referencia API](docs/25-referencia-apis.md) · [Guía de consumo](docs/27-guia-consumo-apis.md) · [Arquitectura actual](docs/26-arquitectura-y-patrones-actuales.md) · [Plan de cierre](docs/28-plan-cierre-y-validacion.md) · [Evidencia histórica](docs/RESULTADO_AUDITORIA_EJECUCION.md)
