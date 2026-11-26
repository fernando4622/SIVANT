# Aceptación final · Fase 7

Evidencia de cada criterio de la fase 7 del [ROADMAP](ROADMAP.md). Fecha: 25 de noviembre de 2026. Los casos están
detallados en [casos-de-prueba.md](casos-de-prueba.md).

| Criterio | Evidencia | Resultado |
|---|---|---|
| Demostración reproducible con los cinco roles, incluida rendición, fondo y ajuste | CP-167 (por la API, en Testcontainers); CP-M49 (clon limpio, base vacía, [guion](demostracion.md) automatizado en 19 pasos) | Cumple |
| Pruebas de integración con PostgreSQL 18 y Testcontainers | 234 ejecuciones del backend, todas aprobadas | Cumple |
| Permisos por rol y propietario | CP-12 a CP-15, CP-112, CP-126, CP-140, CP-146, CP-154, CP-164, CP-F83, CP-M42 | Cumple |
| Bloqueos, versiones, idempotencia, reintentos y restricciones | CP-62, CP-63, CP-78, CP-117, CP-118, CP-128, CP-130, CP-150, CP-151, CP-163, CP-165, CP-166 | Cumple |
| Doble apertura, doble servicio, doble cobro, doble rendición y doble resolución de ajuste | CP-74, CP-116, CP-128, CP-163 y CP-69 | Cumple |
| Historial de 24 meses | CP-138 | Cumple |
| Exclusión de secretos | CP-20 (sin secretos en las respuestas), CP-35 y CP-36 (sin contraseña en la auditoría), CP-132 (sin número de tarjeta) | Cumple |
| Inactividad e invalidación de sesión | CP-10, CP-11, CP-30 a CP-34 (reloj simulado) y CP-M40 (45 minutos reales) | Cumple |
| Accesibilidad: teclado, contraste, tipografía base de 12 pt y sin desplazamiento horizontal | CP-M46 (36 pantallas a 360, 768 y 1024 px) y CP-M47 (teclado) | Cumple tras corregir el cobro a 1024 px |
| Revisión de pantallas contra las maquetas | CP-M48; diferencias en [decisiones.md](decisiones.md) | Cumple |
| CRUD con meta de 1.5 s | CP-169 y CP-170: p95 máximo de 699 ms | Cumple |
| Historial con meta de 2 s | CP-168: p95 máximo de 411 ms con 50 000 ventas y 100 000 movimientos | Cumple |
| Tres usuarios simultáneos y escenario de cuatro con cocina y almacén | CP-169 y CP-170, sin errores | Cumple |
| Escritorio y móvil cercano a 5 pulgadas | CP-M46 a 360 px en el navegador; no se probó en un teléfono físico | Cumple en el navegador |
| Disponibilidad del 98 % de 08:00 a 17:00 | No hay un entorno observado. La compilación y las pruebas locales acreditan el arranque y el funcionamiento probado | Sin evidencia de operación |
| README, OpenAPI, modelo y diagramas | [README](../README.md), [openapi.yaml](openapi.yaml) 1.0.0 con 49 operaciones, [modelo físico](modelo-fisico.md) | Cumple |

## Rendimiento

Medido del lado del cliente por HTTP, con la API y PostgreSQL 18 (Testcontainers) en la misma máquina: Intel Core
i7-1255U, 16 GB de RAM, Windows 11. Ejecutar con:

```powershell
cd backend; .\mvnw.cmd test "-Dexcluir=ninguna" "-Dgroups=rendimiento"
```

La historia de 50 000 ventas equivale a unas diez veces lo esperado en 24 meses (unas 200 ventas al mes).

**Tres usuarios simultáneos (límite p95 1500 ms)**

| Operación | n | p50 ms | p95 ms | máx ms |
|---|---|---|---|---|
| administrador: alta de empleado | 15 | 192 | 312 | 312 |
| administrador: alta de producto | 15 | 46 | 61 | 61 |
| administrador: consultar empleados | 15 | 29 | 69 | 69 |
| administrador: editar producto | 15 | 48 | 139 | 139 |
| almacenista: existencias | 15 | 32 | 64 | 64 |
| almacenista: historial de la existencia | 15 | 40 | 91 | 91 |
| almacenista: registrar entrada | 15 | 56 | 126 | 126 |
| mesero: catálogo | 15 | 32 | 122 | 122 |
| mesero: consultar cuenta | 15 | 33 | 71 | 71 |
| mesero: editar comanda | 15 | 62 | 120 | 120 |
| mesero: enviar comanda | 15 | 59 | 155 | 155 |

**Cuatro usuarios: mesero, cocinero, almacenista y cajero (límite p95 1500 ms)**

| Operación | n | p50 ms | p95 ms | máx ms |
|---|---|---|---|---|
| almacenista: existencias | 15 | 29 | 75 | 75 |
| almacenista: movimientos | 15 | 250 | 699 | 699 |
| almacenista: registrar entrada | 15 | 49 | 117 | 117 |
| cajero: efectivo por entregar | 15 | 172 | 606 | 606 |
| cajero: fondos | 15 | 26 | 84 | 84 |
| cajero: ventas | 15 | 121 | 301 | 301 |
| cocinero: cola | 15 | 34 | 93 | 93 |
| cocinero: iniciar preparación | 15 | 76 | 138 | 138 |
| cocinero: marcar lista | 15 | 66 | 121 | 121 |
| mesero: consultar cuenta | 15 | 43 | 80 | 80 |
| mesero: enviar comanda | 15 | 101 | 209 | 209 |
| mesero: mesas | 15 | 30 | 57 | 57 |

**Historial con 50000 ventas y 100000 movimientos (límite p95 2000 ms)**

| Operación | n | p50 ms | p95 ms | máx ms |
|---|---|---|---|---|
| detalle de venta | 20 | 19 | 29 | 42 |
| movimientos de inventario | 20 | 134 | 411 | 476 |
| movimientos por día | 20 | 43 | 71 | 85 |
| opciones de filtro | 20 | 155 | 202 | 274 |
| ventas del último mes | 20 | 28 | 38 | 66 |
| ventas por mesero y método | 20 | 35 | 42 | 87 |
| ventas sin filtros | 20 | 63 | 89 | 90 |
| ventas última página | 20 | 149 | 214 | 218 |

El resultado depende del equipo. Un servidor de producción con otra carga puede dar otros tiempos.

## Riesgos que quedan

- «Efectivo por entregar» devuelve todos los pagos pendientes sin paginar. Con unos 8 000 pendientes de un mesero
  tarda 606 ms (p95). En la operación normal se entregan a diario, pero conviene paginarlo si se acumulan.
- La reconexión del WebSocket y los tiempos se midieron en una sola máquina, sin red de por medio.
