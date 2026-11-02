# 28 — Plan de cierre, riesgos y Definition of Done

**Corte de código:** SHA dc028a346ccf7b94cfbb60b4fb6cd5493518b481, rama implementacion_propuestas.

## 1. Estado de validación

- Backend CI 41385132612: Maven Verify sobre PostgreSQL 16; 119 pruebas sin fallos; configuración Compose backend normal/Kafka; smoke de API/backend desde volumen PostgreSQL vacío.
- Frontend Build 41385132616: 26 pruebas, build de producción y smoke Chromium de login a 360/768/1280 px.
- No existe en estos workflows aceptación E2E autenticada de todas las pantallas.
- El Compose raíz (frontend + API + n8n contra PostgreSQL del anfitrión) no forma parte del smoke de backend actual.

## 2. Bloqueos de prioridad

| Prioridad | Condición | Acción requerida |
|---|---|---|
| P0 | /api/v1/tax/** y /api/v1/refunds/** tienen permitAll y controladores sin autorización por método | Restringir rutas, validar scopes y probar JWT ausente/rol permitido/rol denegado por HTTP. |
| P1 | Reembolsos sin ciclo financiero completo | Libro de pagos liquidados, acumulado reembolsable, aprobador distinto, audit trail y gateway/confirmación manual verificable. |
| P1 | Snapshot fiscal insuficiente | Persistir tarifa, base, tratamiento, monto y contexto tributario por documento/línea; probar cambios de tarifa sin alterar historia. |
| P1 | API tax acepta date sin usarlo como filtro | Corregir contrato/implementación y añadir prueba para vigencia histórica. |
| P1 | Operaciones/webhooks V28 sin worker externo completo | Dispatcher, firma, retry/backoff, resultado, idempotencia, redrive y conciliación; no etiquetar QUEUED como completado. |
| P1 | Faltan E2E autenticados | Browser journeys para roles y flujos comerciales completos. |
| P2 | Compose raíz sin smoke CI | Validar frontend + API + n8n + PostgreSQL externo/local con configuración limpia. |
| P2 | Rate limit local por instancia | Gateway/WAF o limitador distribuido en topología multiinstancia. |
| P2 | Observabilidad y recuperación | Alertas, backup/restore probado, runbook, redrive y comprobación de versiones/configuración. |
| P2 | Integridad de scopes | Cubrir cada controlador sensible y recurso por almacén/contribuyente. |

## 3. Funcionalidades no entregadas

- Emisión, firma, consulta, autorización, anulación y nota de crédito SRI real.
- Pasarela de pagos y conciliación bancaria.
- Cuentas por pagar/facturas de proveedor.
- Fulfillment/envíos, picking y WMS multi-bin.
- Multiempresa/tenant.
- Consumidores Kafka de proyección analítica.
- Motor de recomendaciones/experimentos y agentes A0–A4.
- Worker de operaciones externas y webhook delivery.
- E2E autenticado completo de la SPA.

## 4. Escenarios funcionales que deben quedar cubiertos

1. Recibir stock, vender todo, recomprar el mismo producto y consultar balance por periodos antes/después de reposición.
2. Repetir compra/recepción con la misma Idempotency-Key y confirmar que el stock no se duplica.
3. Devolver parcialmente unidades de una recepción y demostrar que el servidor bloquea una sobredevolución concurrente.
4. Vender/cancelar con maker-checker, comprobar reposición exacta y auditar actores/movimientos.
5. Realizar devolución física y mantener separado el eventual reembolso.
6. Cambiar una tarifa fiscal y demostrar que el detalle fiscal de una venta anterior no cambia.
7. Reintentar operación externa tras timeout ambiguo sin duplicar side effects.
8. Restaurar un backup en un entorno aislado y ejecutar migraciones/validaciones.

## 5. Definition of Done por API

- Método, ruta, DTO, validaciones, códigos y errores documentados.
- Permiso y scope definidos; pruebas de acceso permitido y denegado.
- Prueba de persistencia/efecto de negocio y de idempotencia donde aplique.
- Paginación/filtros/orden documentados y acotados.
- Cliente frontend/integración actualizado.
- Resultado de prueba ligado al SHA y entorno.
- No exponer secretos, payloads sensibles ni trazas internas.

## 6. Definition of Done del repositorio

- [ ] Cero rutas sensibles sin autenticación y autorización por método.
- [ ] Todos los controles de permisos/scope cubiertos por pruebas HTTP.
- [ ] Pagos, reembolsos e impuestos tienen contratos claros y snapshots/auditoría.
- [ ] Migraciones V1–V28 y Hibernate validation pasan desde base vacía y upgrade representativo.
- [ ] Suite backend, concurrencia y tests de integración pasan.
- [ ] Frontend build/unit tests pasan.
- [ ] E2E browser autenticado pasa para roles y recorridos principales.
- [ ] Compose raíz validado junto con servicios reales que declara.
- [ ] Backups/restores, secretos, TLS, rate limiting, logs/alertas y runbooks revisados.
- [ ] README, contrato API y matriz frontend coinciden con el código.
- [ ] La versión liberable se asocia a un SHA exacto y a todos sus checks.

## 7. Estado de las diez fases

El avance por fase se mantiene en [Plan maestro](../../PLAN_EJECUCION_PROPUESTAS_INVENTARY.md). La Fase 1 está validada; Fases 2 y 4–7 continúan abiertas; el frontend tiene su estructura y smoke responsive, pero no E2E autenticado completo; las Fases 8–10 conservan alcance parcial/objetivo. No declarar producción por el hecho de que CI compile o pase tests.