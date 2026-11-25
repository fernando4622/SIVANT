# ROADMAP de implementación de SIVANT

## Objetivo y fuentes

Implementar los 26 requisitos funcionales de SIVANT en Angular con TypeScript, Spring Boot con Java 21 y PostgreSQL 18. Cada fase entrega un flujo verificable a través de la interfaz, la API y la base de datos cuando corresponda.

- [arquitectura-sivant.md](https://chatgpt.com/library?file_id=libfile_f76ff624b8e4819186b134ce3f949813&account_id=personal) define módulos, contratos, permisos y reglas transaccionales.
- [modelo-datos-logico-relacional.md](https://chatgpt.com/library?file_id=libfile_b0c34b1abe5c8191805432290df11b12&account_id=personal) aporta las entidades y relaciones. Aplicar las extensiones de la sección 6 de arquitectura al preparar las migraciones y reflejarlas en el ER.
- [AGENTS.md](https://chatgpt.com/library?file_id=libfile_b0c15a412ea08191b2f9aa804125905f&account_id=personal) contiene convenciones y referencias de interfaz. Actualizar sus reglas de estados, roles e inventario donde describen el diseño anterior. La trazabilidad de este plan usa los RF vigentes. Cuando un CU antiguo difiera, registrar la diferencia y usar el RF vigente y la arquitectura para implementar.

Reglas que deben verse en la primera demostración operativa. Una mesa tiene como máximo una cuenta abierta. Una cuenta agrupa varias comandas y puede generar una sola venta con un solo pago. Cocina opera sobre la comanda. El inventario se descuenta al marcarla servida. El historial conserva pagos, cancelaciones y movimientos.

## Orden de implementación

| Fase | Depende de | Entregable | Trazabilidad |
|---|---|---|---|
| 0. Entorno y contrato base | — | Monorepo, arranque reproducible y convenciones de API | Soporte transversal |
| 1. Identidad y empleados | 0 | Sesión y cinco roles, gestión de empleados | RF11, RF12, RF13, RF14 |
| 2. Catálogo y operación de inventario | 1 | Productos, recetas, existencias, movimientos y ajustes | RF07, RF08, RF09, RF10, RF15, RF16, RF17, RF18 |
| 3. Mesas, cuentas y cocina | 1 y catálogo de 2 | Registro y cambios de comandas, preparación, cancelación y avisos | RF01, RF02, RF03, RF19, RF20, RF21 |
| 4. Servicio y consumo | 2 y 3 | Entrega de comanda y descuento transaccional | RF23, RF24 |
| 5. Cobro e historial | 3 y 4 | Cierre de cuenta, pago, tarjeta, historial y cancelación de venta | RF04, RF05, RF06, RF22 |
| 6. Caja y fondo de cambio | 5 | Rendición de pagos en efectivo y ciclo del fondo | RF25, RF26 |
| 7. Aceptación y entrega | 1 a 6 | Demostración por rol, pruebas y documentación consolidada | RF01 a RF26 |

Cocina puede empezar cuando el catálogo operativo esté disponible. Aplazar solo la parte de cada fase que dependa de una decisión pendiente. Mantener pruebas y migraciones incrementales por fase.

## Fase 0 · Entorno y contrato base

- [ ] Crear o reutilizar `frontend/`, `backend/`, `infra/` y `docs/`. Fijar versiones compatibles de Angular, Node, Spring Boot, JDK 21 y dependencias. Usar Maven Wrapper.
- [ ] Configurar Compose con PostgreSQL 18 y el esquema `sivant`. Flyway administra migraciones y Hibernate valida el esquema. Preparar variables de entorno, perfiles y datos ficticios para desarrollo.
- [ ] Configurar el proxy de mismo origen y el prefijo `/api/v1`. Crear el esqueleto de `docs/openapi.yaml` y documentar tipos de ID, decimales, fechas, paginación y errores. Completar operaciones y esquemas de cada módulo antes de enlazar sus pantallas.
- [ ] Revisar `AGENTS.md` y el modelo junto con la arquitectura. Registrar qué reglas históricas quedaron sustituidas. Preparar la primera migración de identidad. Precisar las extensiones del modelo antes de crear las migraciones que las usen.

**Salida** · Desde un clon limpio se inicia Angular, Spring Boot y PostgreSQL. Se verifica conexión a la API mediante el proxy, aplicación de Flyway y arranque con una base vacía. El README indica comandos y perfiles.

## Fase 1 · Identidad y empleados · RF11–RF14

- [ ] Implementar login, logout, perfil activo, sesión de servidor y protección CSRF. Incorporar guardas Angular y autorización del lado servidor. Cargar los cinco roles con datos ficticios y documentar el aprovisionamiento del primer administrador.
- [ ] Implementar alta, búsqueda, edición y desactivación. Normalizar el correo, exigir unicidad y guardar la contraseña como hash. Invalidar sesión al desactivar o cambiar contraseña. Aplicar la política documentada de inactividad de 45 minutos sin que el sondeo cuente como actividad.
- [ ] Vincular la regla sobre cuentas a cargo cuando se habilite la operación de cuentas. El cambio de rol usa permisos vigentes en solicitudes posteriores.

**Salida** · Cada rol inicia sesión y accede a su interfaz. La API rechaza operaciones de otro rol, correos duplicados y sesiones invalidadas. Los DTO y logs excluyen secretos.

## Fase 2 · Catálogo e inventario · RF07–RF10 y RF15–RF18

- [ ] Migrar artículos, subtipos, recetas, existencias, movimientos y ajustes. El alta de preparado registra receta y la de reventa crea existencia en la misma transacción. Preparar insumos por carga autorizada y ofrecerlos para selección en recetas.
- [ ] Conectar alta, consulta, modificación y desactivación de productos. El mesero consulta el catálogo activo. El precio aplicado se guardará en el detalle de comanda.
- [ ] Implementar existencias y mínimos, entradas y salidas manuales, historial y solicitud de saldo objetivo. Autorizar ajuste una sola vez. Si cambió la versión de la existencia, devolver conflicto y conservar el ajuste pendiente para rechazo y nueva solicitud.

**Salida** · La desactivación excluye el producto de altas de comandas. Una salida superior al disponible deja el saldo intacto. Rechazar un ajuste conserva el saldo y aprobarlo registra un movimiento auditable.

## Fase 3 · Mesas, cuentas y cocina · RF01–RF03 y RF19–RF21

- [ ] Migrar mesas, cuentas, comandas, detalles, historial, cancelaciones y notificaciones. Impedir dos cuentas abiertas en una mesa y guardar precios aplicados y responsables.
- [ ] Conectar apertura, alta de comanda y edición. El mesero opera sus cuentas. El cajero cancela comandas elegibles con justificación. Verificar las reglas de cierre sin cobro y acceso del mesero antes de completar el ciclo de cuentas.
- [ ] Implementar cola ordenada por envío y transiciones `ENVIADA → EN_PREPARACION → LISTA`. En preparación, un cambio genera aviso de cocina. Marcar lista avisa al mesero responsable con mesa y cuenta. La lectura se registra por usuario y el aviso de pedido listo está disponible desde otras pantallas autenticadas.

**Salida** · Mesero, cocinero y cajero completan sus operaciones respectivas. Si dos meseros intentan abrir la misma mesa, solo uno lo logra. Una transición inválida se rechaza. Los avisos persisten tras recargar y conservan su estado de lectura por usuario.

## Fase 4 · Servicio y consumo · RF23–RF24

- [ ] Registrar `LISTA → SERVIDA` con responsable y fecha. Para preparados, calcular consumo con receta. Para reventa, descontar su existencia. Registrar un movimiento por detalle y existencia.
- [ ] Aplicar transición, historial, movimientos y alertas en una transacción. Con saldo insuficiente, permitir el servicio y generar alerta para almacén según la arquitectura.

**Salida** · Dos solicitudes de servicio generan un solo descuento. Un saldo insuficiente genera alerta y permite terminar el servicio. Un error de persistencia revierte la transición y sus movimientos.

## Fase 5 · Cobro, historial y cancelación · RF04–RF06 y RF22

- [ ] Registrar un pago por cuenta con método único, total recalculado y versión validada. Guardar importes históricos. Incorporar referencia de tarjeta y el procedimiento de recuperación de cargos externos ya aprobados.
- [ ] Conectar el historial de ventas por filtros y detalle dentro de la ventana operativa de 24 meses. Validar los permisos de consulta y cancelación.
- [ ] Implementar cancelación de venta por administrador con confirmación, justificación y evidencia de devolución según el método. Conservar el pago original y los consumos. Aplicar la decisión acordada sobre comandas sin servir.

**Salida** · Dos solicitudes de cobro producen una sola venta con un solo pago. Recuperar el registro tras un fallo local sin repetir el cargo externo. Una cancelación autorizada conserva importe, pago, devolución, auditoría y movimientos de inventario.

## Fase 6 · Caja y fondo de cambio · RF25–RF26

- [ ] Conectar listado de pagos en efectivo pendientes y recepción agrupada por mesero. Validar el monto recibido y prevenir que un pago figure en dos entregas.
- [ ] Registrar el fondo pendiente antes del pago. Asociarlo al pago en efectivo dentro del cierre de cuenta y documentar en OpenAPI la anulación del fondo devuelto antes del cobro. Probar la conciliación que se acuerde para el fondo aplicado.
- [ ] Ajustar el flujo de cobro de la fase 5 para validar y asociar el fondo pendiente en la misma transacción.

**Salida** · Una rendición rechaza pagos duplicados, tarjeta y transferencia. Se puede seguir la entrega del fondo hasta su aplicación y liquidación, o hasta su anulación documentada. Los importes cuadran con el efectivo físico.

## Decisiones que condicionan la implementación

| Decisión | Afecta | Resultado que debe quedar escrito |
|---|---|---|
| Modelo físico de fondo y avisos | 3 y 6 | Cuenta vinculada al fondo, pago opcional hasta aplicación, lectura por empleado e índices asociados. Reflejar las extensiones de arquitectura en el ER y las migraciones correspondientes. |
| Cierre sin cobro | 3 | Actor autorizado, condición sin comandas vigentes, liberación de mesa, auditoría y tratamiento del fondo pendiente. |
| Responsable de cuentas al desactivar o cambiar rol | Integración 1 y 3 | Regla elegida para conservar la operación de cuentas y comandas pendientes. |
| Cobro anticipado, mesa y devolución | 5 | Cuándo se libera la mesa y qué se hace con comandas pendientes al cancelar una venta. La arquitectura actual admite cobro anticipado y conserva el ciclo de cocina. Validar esa política para el flujo final. |
| Cargo aprobado con error local | 5 | Qué identifica de forma única el cargo externo según la TPV utilizada, cómo reintentar el registro y cómo conciliar un cargo si ya cambió la cuenta. Aplicar también un procedimiento verificable para devoluciones. |
| Conciliación del fondo aplicado | 6 | Quién lo liquida, cuándo, qué importe se registra y cómo se separa del total de las ventas rendidas. |

Las decisiones funcionales pendientes se registran con su regla final en la arquitectura y en el modelo afectado. El agente puede preparar pruebas y avanzar en los flujos independientes mientras se resuelven.

## Fase 7 · Criterios de aceptación final

- [ ] Demostración reproducible con los cinco roles. Abrir cuenta, enviar, editar y cancelar una comanda elegible, preparar otra, servirla, cobrar, consultar y cancelar venta. Incluir rendición, fondo y ajuste de inventario.
- [ ] Pruebas de integración con PostgreSQL 18 y Testcontainers. Verificar permisos por rol y propietario, bloqueos, versiones, idempotencia, reintentos y restricciones. Simular doble apertura, doble servicio, doble cobro, doble rendición y doble resolución de ajuste.
- [ ] Verificar historial de 24 meses, exclusión de secretos, inactividad, invalidación de sesión, accesibilidad y revisión de pantallas contra las maquetas disponibles.
- [ ] Conservar los criterios medibles del plan previo. Medir CRUD con meta de 1.5 s e historial con meta de 2 s, usando datos representativos. Probar tres usuarios simultáneos y un escenario de cuatro con cocina y almacén. Revisar escritorio y dispositivo móvil cercano a 5 pulgadas, navegación por teclado, contraste, tipografía base de 12 pt equivalentes y ausencia de desplazamiento horizontal en flujos principales.
- [ ] Registrar evidencia de disponibilidad cuando exista un entorno observado. Meta documentada del 98 % en horario local de 08:00 a 17:00. Reportar periodo, método y resultado de la observación. La compilación y las pruebas locales acreditan el arranque y el funcionamiento probado.
- [ ] Actualizar README, OpenAPI, modelo y diagramas. Entregar comandos de arranque, preparación de datos ficticios y guion de demostración. Marcar una fase completa cuando compile, tenga migraciones aplicables, pase las verificaciones de salida y documente cualquier caso aún pendiente.

## Primera instrucción para el agente

> Inspecciona el repositorio y lee ROADMAP.md, arquitectura-sivant.md, modelo-datos-logico-relacional.md y AGENTS.md. Implementa primero las fases 0 y 1, reutilizando lo que ya funcione. Deja una demo de login y permisos por rol, alta y desactivación de empleados. Entrega comandos de arranque, migraciones aplicadas y resultados de pruebas. Continúa con el catálogo y sus existencias; usa las decisiones pendientes de este plan para delimitar los flujos que necesiten definición.
