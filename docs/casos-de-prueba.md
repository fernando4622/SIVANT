# Casos de prueba · Fases 0 y 1

Casos derivados del diagrama de casos de uso, los RF11 a RF14 y los RNF08 (expiración de sesión) y RNF09 (auditoría).
El identificador aparece en el nombre de la prueba automatizada, así que se puede buscar en el código o en el reporte.

| Prefijo | Tipo | Entorno |
|---|---|---|
| `CP-nn` | Integración automatizada | API por HTTP con cookies y CSRF, PostgreSQL 18 nuevo en Testcontainers |
| `CP-Fnn` | Unitaria automatizada | Angular con la API simulada |
| `CP-Mnn` | Recorrido manual | Navegador, Angular, API y PostgreSQL 18 de Compose |

## Resultado de la ejecución

Fecha: 25 de noviembre de 2026. Java 21.0.9, Node 22.17.0, PostgreSQL 18.

| Conjunto | Comando | Casos | Ejecuciones | Aprobadas | Fallidas |
|---|---|---|---|---|---|
| Backend | `.\mvnw.cmd test` en `backend/` | 41 | 48 | 48 | 0 |
| Frontend | `npm test -- --watch=false` en `frontend/` | 17 | 25 | 25 | 0 |
| Navegador | Manual | 9 | 9 | 9 | 0 |

Algunos casos se ejecutan varias veces, una por rol o por valor. El recorrido manual se hizo antes de agregar la
auditoría, que no cambia pantallas ni respuestas de la API.

## Trazabilidad

| Caso de uso | Requisito | Casos |
|---|---|---|
| Soporte: arranque | Fase 0 | CP-01 a CP-03 |
| Iniciar y cerrar sesión (soporte) | RNF08 | CP-04 a CP-11, CP-F01, CP-F02, CP-F04, CP-F06 a CP-F09, CP-M01 a CP-M03, CP-M08 |
| Generalización de Empleado en cinco actores | Permisos | CP-12 a CP-15, CP-F03, CP-F05, CP-M07 |
| Registrar empleado | RF11 | CP-16 a CP-19, CP-F14, CP-F15, CP-M04 |
| Consultar empleado | RF12 | CP-20 a CP-23, CP-F10, CP-F13, CP-M09 |
| Editar empleado | RF13 | CP-24 a CP-29, CP-F16, CP-F17 |
| Desactivar empleado | RF14 | CP-30 a CP-34, CP-F11, CP-F12, CP-M05, CP-M06 |
| Auditoría de los anteriores | RNF09 | CP-35 a CP-41 |

## Precondiciones comunes

- **P1.** La base está migrada y contiene los cinco roles. Cada caso `CP` inicia con un solo administrador activo.
- **P2.** Hay una sesión de administrador iniciada con su token CSRF.
- **P3.** Existe un empleado activo distinto del administrador.

Los datos son ficticios. «Estado» es el resultado de la ejecución indicada arriba.

## Fase 0 · Arranque

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-01 | Base vacía | 1. Iniciar la API. 2. Consultar el historial de Flyway. 3. Migrar otra vez | PostgreSQL 18; migraciones 1, 2 y 3 aplicadas; ninguna pendiente; la repetición ejecuta 0 | Aprobado |
| CP-02 | P1 | Consultar la tabla `rol` | Exactamente ADMINISTRADOR, ALMACENISTA, CAJERO, COCINERO y MESERO | Aprobado |
| CP-03 | P1 | 1. Insertar por SQL `uno@sivant.test`. 2. Insertar el mismo, uno con mayúsculas, uno con espacio inicial y uno vacío. 3. Insertar estado `BAJA`. 4. Borrar el rol MESERO en uso | El paso 1 se acepta; PostgreSQL rechaza todos los demás | Aprobado |

## Iniciar y cerrar sesión

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-04 | P1 y un empleado de cada rol | Por cada rol: 1. Entrar con su correo en mayúsculas y con espacios. 2. Consultar `/auth/me` | 200 en ambos; perfil con `id` como cadena, `nombre` y `rol`, sin otros campos | Aprobado (5 roles) |
| CP-05 | P1 y un empleado inactivo | Entrar con: contraseña errónea, correo inexistente, empleado inactivo | 401 `CREDENCIALES_INVALIDAS` con cuerpo idéntico en los tres | Aprobado |
| CP-06 | P1 | Entrar con correo y contraseña vacíos | 400 con error en `correo` y en `contrasena` | Aprobado |
| CP-07 | P1 | Sin encabezado CSRF: 1. Login. 2. Con sesión, logout. 3. Con sesión, alta | 403 `CSRF_INVALIDO`; la sesión sigue abierta | Aprobado |
| CP-08 | P2 | Volver a iniciar sesión con la misma cookie | Cambian el ID de sesión y el token CSRF; la cookie es HttpOnly y SameSite=Lax; el logout funciona con el token nuevo | Aprobado |
| CP-09 | P2 | 1. Guardar la cookie. 2. Logout. 3. Reutilizar la cookie en `/auth/me` | Logout 204; después 401 `SESION_REQUERIDA` | Aprobado |
| CP-10 | P2 y reloj simulado | 1. Avanzar 44 min y consultar. 2. Avanzar otros 44 min y consultar. 3. Avanzar 45 min y consultar. 4. Entrar de nuevo | 200, 200, 401 `SESION_EXPIRADA`, 200 | Aprobado |
| CP-11 | P2 y reloj simulado | 1. A los 30 min, consultar con `X-Solicitud-Pasiva`. 2. A los 45 min, consultar | 200 y luego 401 `SESION_EXPIRADA`: el sondeo no renovó el plazo | Aprobado |
| CP-F01 | Sin sesión | Iniciar sesión | Se pide CSRF antes y después del login; se guarda el perfil | Aprobado |
| CP-F02 | Sin sesión | Abrir `/empleados` | Redirige a `/login?volver=%2Fempleados` | Aprobado |
| CP-F04 | Sesión de administrador | La API responde 401 `SESION_INVALIDADA` | Se borra el perfil y navega al login con el motivo y la ruta | Aprobado |
| CP-F06 | Sesión de cajero | Dejar pasar 60 s | Consulta `/auth/me` con `X-Solicitud-Pasiva: true` y actualiza el nombre | Aprobado |
| CP-F07 | Sesión de cajero | 1. Logout sin conexión. 2. Logout con conexión | 1. Conserva la sesión. 2. Borra el perfil y deja de consultar | Aprobado |
| CP-F08 | Pantalla de login | Enviar vacío | No llama a la API; muestra un error por campo | Aprobado |
| CP-F09 | Pantalla de login con `volver` | Entrar con `/empleados?page=1`, `//sitio.externo`, `/\sitio.externo`, `https://sitio.externo` y sin valor | Solo el primero se respeta; los demás van a `/inicio` | Aprobado (5 valores) |

## Actores · Autorización

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-12 | P1, P3 y sesión de MESERO, CAJERO, COCINERO o ALMACENISTA | Intentar alta, listado, detalle, edición y desactivación | 403 `ACCESO_DENEGADO` en las cinco; no cambia el total, el nombre ni el estado | Aprobado (4 roles) |
| CP-13 | P2 y P3 | Ejecutar las cinco operaciones | 201, 200, 200, 200, 200 | Aprobado |
| CP-14 | P3, sin sesión y con CSRF | Ejecutar las cinco operaciones y `/auth/me` | 401 `SESION_REQUERIDA` | Aprobado |
| CP-15 | P2 | Consultar `/ventas`, aún no declarada | 403 | Aprobado |
| CP-F03 | Sesión de cada rol | Abrir `/empleados` | Solo entra ADMINISTRADOR; los demás van a `/inicio` | Aprobado (5 roles) |
| CP-F05 | Sesión de mesero | La API responde 403 | La sesión se conserva | Aprobado |

## Registrar empleado · RF11

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-16 | P2 | 1. Alta con nombre, ` Laura.Gomez@Sivant.TEST `, contraseña y rol MESERO. 2. Entrar con esa cuenta | 201 con `Location`; correo normalizado; estado ACTIVO; hash BCrypt; ni contraseña ni hash en respuesta o log; login 200 | Aprobado |
| CP-17 | P2 y `repetido@sivant.test` registrado | Alta con `REPETIDO@Sivant.test` | 409 `CORREO_DUPLICADO` con error en `correo` | Aprobado |
| CP-18 | Cuatro sesiones de administrador | Cuatro altas simultáneas con el mismo correo | Una 201 y tres 409 | Aprobado |
| CP-19 | P2 | Alta vacía; nombre en blanco; correo sin arroba; contraseña corta; rol GERENTE; contraseña de más de 72 bytes; campo `estado` no permitido; JSON mal formado | 400 en todos, con error en el campo cuando aplica | Aprobado |
| CP-F14 | Formulario de alta | Enviar con correo inválido, contraseña corta y sin nombre ni rol | No llama a la API; muestra los cuatro errores | Aprobado |
| CP-F15 | Formulario completo | La API responde 409 en `correo` | Error junto al correo; se conservan los datos capturados | Aprobado |

## Consultar empleado · RF12

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-20 | P2 y tres empleados, uno inactivo | Listar sin filtros | Cuatro empleados ordenados por nombre, activos e inactivos, sin contraseñas, hashes ni versión de sesión | Aprobado |
| CP-21 | Igual que CP-20 | Filtrar por nombre, correo, rol, estado, rol con estado, `q` con rol, `q=ana_p`, `nombre=%`; rol y estado inexistentes | Solo las coincidencias; `%` y `_` se buscan literales; rol o estado inexistente 400 | Aprobado |
| CP-22 | Igual que CP-20 | `page=1&size=3`; `size=101`; `size=0`; `page=-1` | Segunda página con un empleado y dos páginas; 400 en los demás | Aprobado |
| CP-23 | P2 | Detalle existente, ID inexistente, ID `abc` | 200, 404, 400 | Aprobado |
| CP-F10 | Listado con un activo y un inactivo | Abrir con filtros en la URL | Consulta con esos filtros; solo el activo ofrece desactivar | Aprobado |
| CP-F13 | API sin conexión | 1. Abrir el listado. 2. Reintentar | 1. Mensaje de conexión. 2. Muestra los empleados | Aprobado |

## Editar empleado · RF13

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-24 | P2 y un cajero con sesión | Cambiar nombre y correo | 200 con datos normalizados; conserva el rol y la sesión del cajero | Aprobado |
| CP-25 | P2 y P3 | 1. Cambiar nombre y usar el correo del administrador. 2. Enviar su propio correo en mayúsculas | 1. 409 y el nombre no cambia. 2. 200 | Aprobado |
| CP-26 | P2 y un cajero con sesión | Cambiar su contraseña | Su sesión responde 401 `SESION_INVALIDADA`; la contraseña anterior se rechaza y la nueva entra | Aprobado |
| CP-27 | P2 y otro administrador con sesión | Cambiar su rol a MESERO | Su sesión responde 401; al entrar de nuevo recibe 403 en empleados | Aprobado |
| CP-28 | P2 y P3 | Edición vacía; nombre en blanco; correo vacío; contraseña corta; rol GERENTE; campo `estado`; ID inexistente | 400 en las seis primeras, 404 en la última | Aprobado |
| CP-29 | P2, único administrador | Cambiar su rol a MESERO | 409 `ULTIMO_ADMINISTRADOR`; conserva el acceso | Aprobado |
| CP-F16 | Formulario de edición | 1. Guardar sin cambios. 2. Cambiar solo el rol | 1. No llama a la API y avisa. 2. Envía solo `rol` y vuelve al listado | Aprobado |
| CP-F17 | ID inexistente | Abrir la edición | Muestra el error y no muestra el formulario | Aprobado |

## Desactivar empleado · RF14

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-30 | P2 y P3 | Desactivar con `confirmado=false` y sin el campo | 400 en ambos; sigue activo | Aprobado |
| CP-31 | P2 y otro administrador con sesión | 1. Desactivarlo. 2. Con su sesión, intentar un alta y consultar su perfil. 3. Entrar de nuevo | 200 INACTIVO; después 401 `SESION_INVALIDADA`, 401 y 401; el alta no se guarda | Aprobado |
| CP-32 | P2 y P3 | 1. Desactivar. 2. Consultar. 3. Desactivar otra vez. 4. Alta con su correo | 2. Conserva sus datos. 3. 409 `EMPLEADO_INACTIVO`. 4. 409 | Aprobado |
| CP-33 | P2, único administrador | Desactivarse | 409 `ULTIMO_ADMINISTRADOR`; sigue activo | Aprobado |
| CP-34 | Dos administradores con sesión | Se desactivan mutuamente a la vez, cinco repeticiones | Una sola operación 200, sin errores 500, y queda un activo | Aprobado |
| CP-F11 | Listado | 1. Desactivar y cancelar. 2. Desactivar y confirmar | 1. No llama a la API. 2. Llama, recarga y confirma con un mensaje | Aprobado |
| CP-F12 | Listado | La API rechaza con 409 | El diálogo muestra el motivo | Aprobado |

## Auditoría de cambios · RNF09

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-35 | P2 | Registrar un empleado | Un registro con responsable, fecha, acción, entidad, ID y datos, sin contraseña ni hash | Aprobado |
| CP-36 | P2 y P3 | Cambiar nombre, correo, rol y contraseña | Un registro con valor anterior y nuevo; de la contraseña solo `MODIFICADA` | Aprobado |
| CP-37 | P2 y P3 | Desactivar | Un registro con estado ACTIVO a INACTIVO | Aprobado |
| CP-38 | P2, P3 y sesión de mesero | Consultar; editar sin diferencias; correo repetido; desactivar sin confirmar; desactivar al último administrador; edición de un mesero | Ningún registro | Aprobado |
| CP-39 | P2, P3 y fallo simulado al auditar | Alta, edición y desactivación | 500 en las tres y ningún cambio guardado | Aprobado |
| CP-40 | Un registro de auditoría | Por SQL: modificarlo, eliminarlo y eliminar a su responsable | PostgreSQL rechaza las tres | Aprobado |
| CP-41 | Sin transacción | Registrar auditoría | Se rechaza y no se guarda | Aprobado |

## Recorrido en el navegador

| ID | Precondición | Pasos y datos | Resultado obtenido | Estado |
|---|---|---|---|---|
| CP-M01 | Sin sesión | Abrir `/empleados` y entrar como administrador | Pide iniciar sesión y regresa a `/empleados` | Aprobado |
| CP-M02 | Login | Entrar con contraseña incorrecta | «El correo o la contraseña no son correctos.» y conserva el correo | Aprobado |
| CP-M03 | Sesión de administrador | Abrir directamente otra ruta, con recarga completa | La sesión se conserva | Aprobado |
| CP-M04 | Sesión de administrador | 1. Alta con `MESERO@sivant.test`. 2. Corregir el correo y guardar | 1. Error en el correo y datos conservados. 2. Aparece en el listado | Aprobado |
| CP-M05 | Laura, administradora ficticia, con otra sesión abierta | Desactivarla desde la pantalla | Pasa a Inactivo; su sesión recibe 401, su login 401 y su registro se conserva | Aprobado |
| CP-M06 | Mesero ficticio con sesión en el navegador | Desactivarlo desde otra sesión y esperar 65 s | Regresa al login con «Tu acceso cambió» | Aprobado |
| CP-M07 | Sesión de mesero | Abrir `/empleados` | Regresa a `/inicio`; su menú solo muestra Inicio | Aprobado |
| CP-M08 | Sesión cerrada | Con el foco en el correo, capturar, pasar a la contraseña con Tab y enviar con Enter | Inicia sesión | Aprobado |
| CP-M09 | Sesión de administrador | Ver el listado a 360, 800 y 1000 px | Sin desplazamiento horizontal; texto de 16 px (12 pt) o más | Aprobado tras corregir |

### Defectos encontrados

| Caso | Defecto | Corrección |
|---|---|---|
| CP-M09 | A 360 px el encabezado oculto de la tabla ensanchaba la página a 402 px | Se ajustó el estilo y se repitió el caso |
| CP-M04 | El error de correo aparecía dos veces | El mensaje general indica revisar los datos marcados. No se repitió en el navegador; CP-F15 cubre el error por campo |

## Sin cubrir

- Los 45 minutos de inactividad en tiempo real: CP-10 y CP-11 usan un reloj simulado.
- Operación con HTTPS y en dispositivos físicos.
- Tiempos de respuesta y sesiones simultáneas (RNF01 a RNF03), que el ROADMAP ubica en la fase 7.

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
