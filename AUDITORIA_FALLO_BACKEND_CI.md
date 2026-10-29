# Diagnóstico: por qué falla el backend CI mientras Docker local funciona

**Fecha de revisión:** 28 de octubre de 2026 (zona America/Guayaquil)  
**Repositorio:** JhonnyMinian92/inventary  
**Rama y SHA inspeccionados:** master · 23c806d6ed5d06d1ff55e5a72be405167a79150f  
**Alcance:** diagnóstico de solo lectura. No se modificó código ni configuración.

## Conclusión

El workflow de backend falla por una **contraseña de bootstrap de CI que infringe la política de contraseñas de la aplicación**. No falla la fase Maven: el trabajo de GitHub Actions muestra que Maven verify con PostgreSQL 16 y la validación de configuración normal y Kafka de Docker Compose terminan correctamente. El fallo ocurre en el paso «Smoke test Docker Compose from an empty PostgreSQL volume», cuando el servicio API intenta arrancar con una base recién creada.

- [Ejecución fallida del backend en el SHA actual](https://github.com/JhonnyMinian92/inventary/actions/runs/40809131253)
- [Ejecución del frontend en el mismo SHA, correcta](https://github.com/JhonnyMinian92/inventary/actions/runs/40809131337)
- [Workflow backend-ci](https://github.com/JhonnyMinian92/inventary/blob/23c806d6ed5d06d1ff55e5a72be405167a79150f/.github/workflows/backend-ci.yml)
- [Política de contraseñas](https://github.com/JhonnyMinian92/inventary/blob/23c806d6ed5d06d1ff55e5a72be405167a79150f/microservices-inventary-ecuapp/src/main/java/com/ecuapp/inventary/auth/PasswordPolicy.java)

## Evidencia exacta

La política central en PasswordPolicy.java define:

- Longitud mínima: 8 caracteres.
- Longitud máxima: 15 caracteres.
- Debe incluir minúsculas, mayúsculas, números y símbolos.
- No debe contener el nombre de usuario cuando se valida para ese usuario.

El workflow backend-ci.yml define para el smoke test:

**BOOTSTRAP_ADMIN_PASSWORD = Ci-Only-Bootstrap-Password-123!**

Esa contraseña supera el máximo de 15 caracteres. En el log del contenedor aparecen repetidamente estos mensajes:

- «BOOTSTRAP_ADMIN_PASSWORD no cumple la política de contraseña inicial.»
- «La contraseña debe tener entre 8 y 15 caracteres e incluir mayúscula, minúscula, número y símbolo.»

El registro completo se puede consultar en los logs del job de la ejecución [40809131253](https://github.com/JhonnyMinian92/inventary/actions/runs/40809131253). El fallo se repite porque el contenedor se reinicia y vuelve a ejecutar el mismo bootstrap inválido. No se observa una excepción de compilación como causa principal.

## Por qué el Docker local puede funcionar

Hay dos diferencias fundamentales entre la prueba de CI y un entorno local que lleva tiempo funcionando:

1. **CI arranca una base de datos vacía.** El objetivo del smoke test es comprobar que Flyway crea el esquema, que la aplicación inicializa sus componentes y que el administrador inicial puede crearse desde cero. Para ello se usa un volumen nuevo y el bootstrap se ejecuta con las variables que proporciona el workflow.
2. **El Docker local suele utilizar una base persistente ya inicializada.** El README raíz especifica que el usuario inicial se crea solo si todavía no existen usuarios. Si el volumen local ya contiene usuarios y un administrador creado anteriormente, la contraseña de bootstrap deja de ser el paso que determina el arranque. Una variable de entorno de bootstrap inválida puede quedar sin utilizar mientras la base persista con los usuarios existentes.

Por eso «el contenedor local está arriba» no contradice el resultado rojo de CI. Ambos entornos pueden estar ejecutando el mismo código con distinto estado inicial de la base de datos. El smoke test está encontrando un problema real de configuración que una instalación persistente no necesariamente vuelve a mostrar.

Esta explicación se refiere a la configuración observada. No significa que se haya inspeccionado el volumen privado de tu máquina local; no tengo acceso a su contenido.

## Inconsistencia relacionada: ejemplo de entorno del backend

El archivo [microservices-inventary-ecuapp/.env.example](https://github.com/JhonnyMinian92/inventary/blob/23c806d6ed5d06d1ff55e5a72be405167a79150f/microservices-inventary-ecuapp/.env.example) también usa una contraseña de bootstrap de más de 15 caracteres: LocalOnly-StartSecurePassword-123!.

En cambio, el ejemplo raíz usa Cambiar-Esta1!, que sí se ajusta al límite de longitud y contiene los tipos de caracteres requeridos. Esta diferencia aumenta la probabilidad de que el arranque desde cero falle cuando se sigue el README del backend de forma independiente.

## Qué debería considerarse una corrección válida

Este informe no cambia el código. Cuando se autorice una corrección, la validación debe cubrir lo siguiente:

1. **Unificar las muestras de configuración con la política real.** Todos los ejemplos y la contraseña exclusiva de CI deben cumplir la misma política de 8–15 caracteres, mayúscula, minúscula, número y símbolo.
2. **No relajar la política solo para hacer pasar CI.** La causa inmediata es el valor de prueba, no una evidencia de que la regla de seguridad sea incorrecta.
3. **Preservar el smoke test en base vacía.** No se debe eliminar ni omitir la prueba; es la que detectó la diferencia de comportamiento entre una instalación limpia y otra persistente.
4. **Repetir el workflow completo en un nuevo SHA.** Debe quedar verde Maven verify con PostgreSQL 16, Compose normal, Compose con perfil Kafka y el smoke test de arranque con volumen vacío.
5. **Hacer una prueba local con una base de datos aislada recién creada.** No borrar ni limpiar la base local de trabajo para intentar reproducir el fallo. Debe utilizarse una base/volumen desechable y credenciales de prueba que cumplan la política.

## Resultado por fase del workflow actual

| Fase | Resultado en SHA 23c806d |
|---|---|
| Inicialización de contenedor PostgreSQL 16 | Correcta |
| Checkout y configuración de Java 21 | Correctos |
| Maven verify | Correcto |
| docker compose config --quiet | Correcto |
| docker compose --profile kafka config --quiet | Correcto |
| Smoke test con base/volumen vacío | **Fallido**: contraseña de bootstrap inválida |
| Limpieza de los recursos de smoke test | Correcta |

La conclusión más precisa es: **el backend supera las pruebas Maven del workflow, pero la entrega de backend CI no está aprobada porque falla el escenario de instalación limpia**. No debe marcarse como resuelto hasta que una nueva ejecución ligada al SHA corregido termine completamente en verde.
