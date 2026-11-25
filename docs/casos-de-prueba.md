# Casos de prueba por caso de uso

Cada caso tiene el mismo identificador en el nombre de su prueba automatizada, así que se puede buscar en el código
o en el reporte. Los casos `CP-nn` se ejecutan contra la API por HTTP con PostgreSQL 18 (Testcontainers). Los casos
`CP-Fnn` son pruebas unitarias de Angular. Los casos `CP-Mnn` se recorrieron en el navegador con la API real.

Última ejecución: 25 de noviembre de 2026. Java 21.0.9, Node 22.17.0, PostgreSQL 18.

| Conjunto | Comando | Resultado |
|---|---|---|
| Backend | `./mvnw test` en `backend/` | 41 de 41 correctas |
| Frontend | `npm test -- --watch=false` en `frontend/` | 25 de 25 correctas |
| Navegador | Recorrido manual | 9 de 9 correctos |

Hay más ejecuciones que casos porque algunos se repiten por rol o por valor.

## Fase 0 · Arranque

| ID | Verifica | Prueba |
|---|---|---|
| CP-01 | Flyway parte de una base vacía y repetir la migración no cambia nada | `MigracionesTest` |
| CP-02 | Existen los cinco roles | `MigracionesTest` |
| CP-03 | La base rechaza correos repetidos, sin normalizar o vacíos, y estados desconocidos | `MigracionesTest` |

## Iniciar y cerrar sesión

| ID | Verifica | Prueba |
|---|---|---|
| CP-04 | Los cinco roles inician sesión y consultan su perfil | `SesionTest` |
| CP-05 | Contraseña errónea, correo inexistente y empleado inactivo reciben la misma respuesta | `SesionTest` |
| CP-06 | Los campos vacíos se informan por campo | `SesionTest` |
| CP-07 | Sin token CSRF se rechazan login, logout y operaciones | `SesionTest` |
| CP-08 | El login cambia el ID de sesión, renueva CSRF y protege la cookie | `SesionTest` |
| CP-09 | El logout invalida la sesión aunque se reutilice la cookie | `SesionTest` |
| CP-10 | La sesión expira a los 45 minutos sin actividad (RNF08) | `SesionTest` |
| CP-11 | El sondeo no cuenta como actividad | `SesionTest` |
| CP-F01 | Angular renueva CSRF y guarda el perfil | `sesion.spec.ts` |
| CP-F02 | Sin sesión, la guarda lleva al login y recuerda la ruta | `sesion.spec.ts` |
| CP-F04 | Un 401 lleva al login con su motivo | `sesion.spec.ts` |
| CP-F06 | La vigilancia de sesión no cuenta como actividad | `sesion.spec.ts` |
| CP-F07 | Logout correcto y logout sin conexión | `sesion.spec.ts` |
| CP-F08 | El login valida campos vacíos | `login.spec.ts` |
| CP-F09 | Tras entrar solo se navega a rutas internas | `login.spec.ts` |

## Actores · Autorización

| ID | Verifica | Prueba |
|---|---|---|
| CP-12 | Mesero, cajero, cocinero y almacenista no pueden ejecutar ninguna operación de empleados y nada cambia | `PermisosTest` |
| CP-13 | El administrador ejecuta las cinco operaciones | `PermisosTest` |
| CP-14 | Sin sesión toda operación responde 401 | `PermisosTest` |
| CP-15 | Una ruta no declarada se rechaza | `PermisosTest` |
| CP-F03 | Angular solo permite entrar a empleados al administrador | `sesion.spec.ts` |
| CP-F05 | Un 403 conserva la sesión | `sesion.spec.ts` |

## Gestionar empleados

### Registrar empleado · RF11

| ID | Verifica | Prueba |
|---|---|---|
| CP-16 | Alta válida: correo normalizado, hash BCrypt, sin secretos en respuesta ni log, acceso inmediato | `GestionEmpleadosTest` |
| CP-17 | Correo repetido con otras mayúsculas | `GestionEmpleadosTest` |
| CP-18 | Cuatro altas simultáneas con el mismo correo: se guarda una | `GestionEmpleadosTest` |
| CP-19 | Datos inválidos con error por campo | `GestionEmpleadosTest` |
| CP-F14 | El formulario valida antes de enviar | `empleados.spec.ts` |
| CP-F15 | Un 409 marca el correo y conserva lo capturado | `empleados.spec.ts` |

### Consultar empleado · RF12

| ID | Verifica | Prueba |
|---|---|---|
| CP-20 | Incluye activos e inactivos, sin secretos y en orden estable | `GestionEmpleadosTest` |
| CP-21 | Filtros por nombre, correo, rol y estado | `GestionEmpleadosTest` |
| CP-22 | Paginación y límite de tamaño | `GestionEmpleadosTest` |
| CP-23 | Detalle, inexistente e ID inválido | `GestionEmpleadosTest` |
| CP-F10 | El listado usa los filtros de la URL | `empleados.spec.ts` |
| CP-F13 | Error de carga y reintento | `empleados.spec.ts` |

### Editar empleado · RF13

| ID | Verifica | Prueba |
|---|---|---|
| CP-24 | Edición parcial que conserva lo no enviado | `GestionEmpleadosTest` |
| CP-25 | Correo de otro empleado, sin cambios parciales | `GestionEmpleadosTest` |
| CP-26 | Cambiar la contraseña cierra la sesión abierta | `GestionEmpleadosTest` |
| CP-27 | Cambiar el rol retira los permisos de la sesión abierta | `GestionEmpleadosTest` |
| CP-28 | Edición vacía, inválida o de un inexistente | `GestionEmpleadosTest` |
| CP-29 | El último administrador conserva su rol | `GestionEmpleadosTest` |
| CP-F16 | Se envía solo lo modificado | `empleados.spec.ts` |
| CP-F17 | Empleado inexistente | `empleados.spec.ts` |

### Desactivar empleado · RF14

| ID | Verifica | Prueba |
|---|---|---|
| CP-30 | Sin confirmación no hay cambios | `GestionEmpleadosTest` |
| CP-31 | Pierde acceso con la sesión abierta y no vuelve a entrar | `GestionEmpleadosTest` |
| CP-32 | Conserva el registro y rechaza repetir | `GestionEmpleadosTest` |
| CP-33 | El último administrador no puede desactivarse | `GestionEmpleadosTest` |
| CP-34 | Dos administradores que se desactivan a la vez: queda uno | `GestionEmpleadosTest` |
| CP-F11 | El diálogo exige confirmar y permite cancelar | `empleados.spec.ts` |
| CP-F12 | Un rechazo de la API se muestra en el diálogo | `empleados.spec.ts` |

## Recorrido en el navegador

| ID | Verifica |
|---|---|
| CP-M01 | `/empleados` sin sesión lleva al login y, tras entrar, regresa a `/empleados` |
| CP-M02 | Contraseña incorrecta muestra el error y conserva el correo |
| CP-M03 | Recargar la página conserva la sesión |
| CP-M04 | Alta con correo repetido, corrección y registro |
| CP-M05 | Desactivar desde la pantalla: la sesión abierta de esa persona responde 401 y el login se rechaza |
| CP-M06 | Un mesero desactivado desde otra sesión es llevado al login en un minuto, con el motivo |
| CP-M07 | Un mesero que abre `/empleados` regresa a su inicio y su menú no muestra esa opción |
| CP-M08 | Cerrar sesión e iniciar otra usando el teclado |
| CP-M09 | A 360, 800 y 1000 px el listado no tiene desplazamiento horizontal, y el texto mide 16 px (12 pt) o más |

## Casos de uso de fases posteriores

Sus casos de prueba se escriben al implementar cada fase.

| Caso de uso | Fase | RF |
|---|---|---|
| Registrar, consultar, modificar y desactivar producto | 2 | RF07 a RF10 |
| Consultar inventario, registrar movimiento, ajustar y autorizar ajuste | 2 | RF15 a RF18 |
| Registrar, modificar y cancelar pedido | 3 | RF01 a RF03 |
| Consultar cola, marcar en preparación y marcar listo | 3 | RF19 a RF21 |
| Marcar pedido servido | 4 | RF23, RF24 |
| Cobrar pedido, autorizar pago con tarjeta, consultar y cancelar venta | 5 | RF04 a RF06, RF22 |
| Registrar entrega de efectivo y entregar fondo de cambio | 6 | RF25, RF26 |
