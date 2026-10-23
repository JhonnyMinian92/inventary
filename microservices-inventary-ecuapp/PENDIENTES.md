> **Estado al 2026-10-23: implementado.** El reporte consolidado `GET /api/v1/reports/inventory-balance` incluye ahora valor inicial y final del inventario, costo de ventas y utilidad bruta (motor de costeo FIFO/promedio integrado); los filtros por fecha, producto y almacén existen en movimientos, ventas, órdenes de compra y recepciones; y se añadieron los reportes de valoración, ventas, compras, kardex y bajo stock. El escenario descrito abajo (vender todo y reponer el mismo producto) está cubierto por una prueba automatizada con todas sus cifras. Evidencia: `docs/RESULTADO_AUDITORIA_EJECUCION.md` §5. El análisis original se conserva a continuación.

Parcialmente. En la rama `master` de Inventary ya existen las APIs y los movimientos de inventario necesarios para construir ese balance, y el backend contempla que un producto se venda por completo y se vuelva a comprar después. Sin embargo, no encontré implementada una API que reúna compras, ventas y existencias históricas en un solo reporte filtrado por rango de fechas.

## 1. ¿Qué APIs existen actualmente?

| API                               | Qué permite consultar                                           | Limitación actual                                                     |
| --------------------------------- | --------------------------------------------------------------- | --------------------------------------------------------------------- |
| `GET /api/v1/inventory/balances`  | Existencias actuales, cantidades reservadas y disponibles.      | No permite consultar el saldo histórico por fecha.                    |
| `GET /api/v1/inventory/movements` | Movimientos de inventario con producto, cantidad, tipo y fecha. | No tiene filtros específicos por fecha, producto o almacén.           |
| `GET /api/v1/sales`               | Listado paginado de ventas.                                     | No implementa filtros por rango de fechas.                            |
| `GET /api/v1/purchase-orders`     | Listado paginado de órdenes de compra.                          | No ofrece un reporte de cantidades realmente recibidas en un periodo. |

Puedes revisar los [controladores de inventario](https://github.com/JhonnyMinian92/inventary/blob/master/microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/inventory/InventoryController.java?utm_source=chatgpt.com), [ventas](https://github.com/JhonnyMinian92/inventary/blob/master/microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/SalesController.java?utm_source=chatgpt.com) y [compras](https://github.com/JhonnyMinian92/inventary/blob/master/microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/purchasing/PurchaseOrderController.java?utm_source=chatgpt.com).

La documentación sí propone endpoints de reportes como `/reports/stock`, `/reports/purchases`, `/reports/sales` y `/reports/valuation`, pero eso no significa que estén implementados en el código actual. Consulta el [catálogo de APIs](https://github.com/JhonnyMinian92/inventary/blob/master/microservices-inventary-ecuapp/docs/05-catalogo-apis.md?utm_source=chatgpt.com).

## 2. ¿Qué ocurre cuando se vende todo y después se repone el mismo producto?

El flujo actual contempla ese escenario.

Por ejemplo, para un mismo producto y almacén:

1\. Compra inicial

Se reciben 10 unidades.

## 10

2\. Se venden todas

La salida de inventario deja el saldo en cero.

## 0

3\. Nueva compra del mismo producto

Se reciben otras 15 unidades usando el mismo producto.

## 15

4\. Nuevo saldo

El producto vuelve a estar disponible para venderse.

## 15

Esto está respaldado por el código: las ventas generan movimientos `SALE` y reducen el saldo; las recepciones generan movimientos `RECEIPT` y aumentan el saldo. Si no existe una fila de saldo, el servicio de recepción puede crearla. Por tanto, la reposición no necesita crear un producto nuevo: debe utilizar el mismo identificador de producto.

Referencias: [SaleService.java](https://github.com/JhonnyMinian92/inventary/blob/master/microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/sales/SaleService.java?utm_source=chatgpt.com) y [PurchaseOrderService.java](https://github.com/JhonnyMinian92/inventary/blob/master/microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/purchasing/PurchaseOrderService.java?utm_source=chatgpt.com).

## 3. Qué reporte hace falta implementar

Recomiendo crear un endpoint combinado como este:

`GET /api/v1/reports/inventory-balance?from=2026-10-01&to=2026-10-23&warehouseId={uuid}`

Con filtros opcionales por producto y almacén, debería devolver por producto:

| Campo               | Qué representa                                                                                                     |
| ------------------- | ------------------------------------------------------------------------------------------------------------------ |
| `openingStock`      | Existencias al inicio del periodo.                                                                                 |
| `purchasedQuantity` | Unidades recibidas durante el periodo, descontando las devoluciones a proveedores.                                 |
| `purchaseAmount`    | Importe de las recepciones, usando el costo registrado en cada recepción.                                          |
| `soldQuantity`      | Unidades vendidas durante el periodo, con cancelaciones y devoluciones tratadas correctamente.                     |
| `salesAmount`       | Importe de las ventas válidas del periodo, considerando cancelaciones y devoluciones según la política de negocio. |
| `closingStock`      | Existencias al finalizar el periodo.                                                                               |
| `currentStock`      | Existencias actuales al realizar la consulta, si también se requiere mostrar el estado de hoy.                     |

La distinción entre `closingStock` y `currentStock` es importante: si consultas un periodo pasado, el saldo de cierre debe reflejar el inventario que había al final de ese periodo, no el de hoy.

### La regla fundamental

El saldo de cierre no debe calcularse simplemente como compras menos ventas. Debe considerar:

Saldo inicial + entradas − salidas, incluyendo recepciones, ventas, devoluciones, cancelaciones, ajustes y transferencias según el almacén consultado.

Además, las compras deben contarse al recibir mercancía, no simplemente al crear una orden de compra. El backend ya registra esas recepciones como movimientos históricos, por lo que existe una base para construir el reporte.

## 4. Una limitación financiera que hay que resolver

El reporte puede diseñarse para mostrar cantidades, ventas e importes de compra. No obstante, para presentar con fiabilidad el valor monetario del inventario restante y la utilidad real, hace falta completar y verificar la valoración de costos: la auditoría del backend identificó que el cálculo FIFO/costo promedio todavía no está integrado completamente en los flujos de recepción y venta.

Por tanto, no sería correcto calcular la utilidad como ventas menos compras del periodo: parte de lo comprado puede seguir en inventario y parte de lo vendido puede proceder de compras anteriores.

Conclusión: el backend ya conserva buena parte de los datos necesarios y admite reponer el mismo producto después de agotarlo. Lo que falta es implementar el reporte consolidado, añadir filtros por fecha y establecer las reglas de cálculo y valoración. Esta conclusión proviene de una revisión estática del código; no se ejecutaron pruebas ni compilaciones.