# Casos de prueba · Fases 0 a 7

Casos derivados del diagrama de casos de uso, los RF implementados y los RNF08 (expiración de sesión), RNF09 (auditoría) y RNF10 (tiempo real).
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
| Backend | `.\mvnw.cmd test` en `backend/` | 169 | 229 | 229 | 0 |
| Rendimiento | `.\mvnw.cmd test "-Dexcluir=ninguna" "-Dgroups=rendimiento"` en `backend/` | 3 | 3 | 3 | 0 |
| Frontend | `npm test -- --watch=false` en `frontend/` | 89 | 104 | 104 | 0 |
| Navegador | Manual | 45 | 45 | 45 | 0 |

Fecha de la última ejecución: 26 de noviembre de 2026, con las migraciones 1 a 14 y 0 pruebas omitidas. Algunos casos se ejecutan varias
veces, una por rol o por valor. CP-M01 a CP-M09 se recorrieron en la fase 1 con la pantalla de acceso anterior;
CP-M16 y CP-M17 cubren la actual.

## Trazabilidad

| Caso de uso | Requisito | Casos |
|---|---|---|
| Soporte: arranque | Fase 0 | CP-01 a CP-03 |
| Iniciar y cerrar sesión (soporte) | RNF08 | CP-04 a CP-11, CP-F01, CP-F02, CP-F04, CP-F06 a CP-F09, CP-F34 a CP-F37, CP-M01 a CP-M03, CP-M08, CP-M16, CP-M17, CP-M40 |
| Generalización de Empleado en cinco actores | Permisos | CP-12 a CP-15, CP-F03, CP-F05, CP-F83, CP-M07, CP-M42 |
| Registrar empleado | RF11 | CP-16 a CP-19, CP-F14, CP-F15, CP-M04 |
| Consultar empleado | RF12 | CP-20 a CP-23, CP-F10, CP-F13, CP-M09 |
| Editar empleado | RF13 | CP-24 a CP-29, CP-F16, CP-F17 |
| Desactivar empleado | RF14 | CP-30 a CP-34, CP-F11, CP-F12, CP-M05, CP-M06 |
| Auditoría de los anteriores | RNF09 | CP-35 a CP-41 |
| Registrar producto | RF07 | CP-42 a CP-46, CP-171, CP-F20, CP-F21, CP-M13 |
| Consultar producto | RF08 | CP-47 a CP-49, CP-F18, CP-F19, CP-M15 |
| Modificar producto | RF09 | CP-50, CP-51, CP-F22, CP-F23, CP-F84, CP-M14 |
| Imagen del producto | RF07, RF08 y RF09 | CP-174 a CP-178, CP-F88 a CP-F91, CP-M52 |
| Desactivar producto | RF10 | CP-52, CP-53, CP-M14 |
| Consultar inventario | RF16 | CP-54 a CP-56, CP-173, CP-F24 a CP-F26, CP-F86, CP-M10, CP-M50 |
| Registrar movimiento de inventario | RF17 | CP-57 a CP-63, CP-72, CP-172, CP-F27, CP-F28, CP-F85, CP-F87, CP-M11, CP-M50 |
| Ajustar inventario | RF18 | CP-64, CP-65, CP-F29, CP-F33, CP-M11 |
| Autorizar ajuste de inventario | RF15 | CP-66 a CP-71, CP-F30 a CP-F32, CP-M12 |
| Registrar pedido | RF01 | CP-73 a CP-79, CP-F42 a CP-F45, CP-F47, CP-F51, CP-M18 |
| Modificar pedido | RF02 | CP-80 a CP-84, CP-F48, CP-F49, CP-M21 |
| Cancelar pedido | RF03 | CP-85 a CP-87, CP-F50, CP-M22 |
| Cierre sin cobro y empleados con cuentas | Decisiones de la fase 3 | CP-88 a CP-91, CP-F46, CP-M23 |
| Consultar cola de pedidos | RF19 | CP-92, CP-93, CP-F52, CP-F55, CP-M20 |
| Marcar pedido en preparación | RF20 | CP-94 a CP-97, CP-F53, CP-F54, CP-M20 |
| Marcar pedido listo y avisos | RF21 | CP-98 a CP-101, CP-F56 a CP-F58, CP-M19 |
| Tiempo real | RNF10 | CP-102 a CP-108, CP-121, CP-F38 a CP-F41, CP-M19, CP-M20, CP-M41 |
| Marcar pedido servido | RF23 | CP-109 a CP-112, CP-F59, CP-F60, CP-M25 |
| Descontar inventario (automático) | RF24 | CP-113 a CP-120, CP-F61 a CP-F65, CP-M26, CP-M27 |
| Cobrar pedido | RF04 | CP-122 a CP-130, CP-150, CP-151, CP-F66, CP-F67, CP-F69, CP-F71, CP-M28, CP-M33 |
| Autorizar pago con tarjeta (extiende Cobrar pedido) | RF05 | CP-131 a CP-135, CP-F68, CP-F70, CP-M29, CP-M30 |
| Consultar historial de ventas | RF06 | CP-136 a CP-141, CP-F72, CP-F73, CP-M31, CP-M33 |
| Cancelar venta | RF22 | CP-142 a CP-149, CP-150, CP-F74, CP-F75, CP-M32, CP-M33 |
| Registrar entrega de efectivo | RF25 | CP-152 a CP-157, CP-159, CP-160, CP-F77 a CP-F80, CP-M51 |
| Entregar fondo de cambio (extiende Cobrar pedido) | RF26 | CP-152, CP-153, CP-158, CP-160, CP-F76, CP-F78, CP-F79, CP-M51 |
| Aceptación: demostración con los cinco roles | Fase 7 | CP-167, CP-M49 |
| Rendimiento y sesiones simultáneas | RNF01 a RNF03 | CP-168 a CP-170 |
| Accesibilidad, móvil y maquetas | Fase 7 | CP-M46 a CP-M48 |

## Precondiciones comunes

- **P1.** La base está migrada y contiene los cinco roles. Cada caso `CP` inicia con un solo administrador activo.
- **P2.** Hay una sesión de administrador iniciada con su token CSRF.
- **P3.** Existe un empleado activo distinto del administrador.

Los datos son ficticios. «Estado» es el resultado de la ejecución indicada arriba.

## Fase 0 · Arranque

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-01 | Base vacía | 1. Iniciar la API. 2. Consultar el historial de Flyway. 3. Migrar otra vez | PostgreSQL 18; migraciones 1 a 12 aplicadas; ninguna pendiente; la repetición ejecuta 0 | Aprobado |
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
| CP-F34 | Login sin correo recordado | Marcar «Recordarme», entrar y volver a abrir | Guarda solo el correo, nunca la contraseña, y lo propone | Aprobado |
| CP-F35 | Correo recordado | 1. Entrar sin «Recordarme». 2. Marcarlo con un login rechazado | 1. Olvida el correo. 2. No guarda nada | Aprobado |
| CP-F36 | Login | Elegir «¿Olvidaste tu contraseña?» | Indica pedirla al administrador; no llama a la API | Aprobado |
| CP-F37 | Login | Usar el botón del ojo dos veces | Muestra y oculta la contraseña | Aprobado |

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

## Registrar producto · RF07

Precondición común de la fase 2: P1 y P2, con los insumos «Café en grano» y «Leche» preparados por carga autorizada.

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-42 | Insumos | Alta de un preparado con dos insumos | 201 con `Location`; nombre sin espacios; precio como cadena `55.00`; receta guardada; sin existencia propia | Aprobado |
| CP-43 | P2 | Alta de reventa con nivel mínimo 5 | 201; existencia creada en 0 con mínimo 5; sin receta | Aprobado |
| CP-44 | Insumos | Alta vacía; ocho precios inválidos, incluidos `0` y `0.00`; preparado sin receta, con insumo repetido, inexistente o en cero, o con nivel mínimo; reventa sin mínimo o con receta | 400 con error en el campo; no se guarda nada | Aprobado |
| CP-45 | Insumos | Por SQL: preparado sin receta, reventa sin existencia, artículo sin subtipo, existencia sin artículo, borrar una receta completa o una existencia de insumo | PostgreSQL rechaza todos | Aprobado |
| CP-46 | Insumos | Consultar insumos | Lista los disponibles | Aprobado |
| CP-171 | Base con V10 y producto de reventa | Cambiar por SQL el precio del producto a `0`; consultar el producto y la definición de `ck_detalle_precio` | PostgreSQL rechaza el cambio; el precio se conserva y el `CHECK` del detalle exige un precio unitario mayor que cero | Aprobado |
| CP-F20 | Formulario de alta | Enviar vacío; cantidad 0; insumo repetido; datos correctos con coma decimal | Muestra cada error sin llamar a la API; al final envía `60.5` y `18.5` | Aprobado |
| CP-F21 | Formulario de alta | Elegir Reventa y enviar sin nivel mínimo; después con 5 | Pide el nivel mínimo; luego envía sin receta y vuelve al listado | Aprobado |

## Consultar producto · RF08

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-47 | Dos preparados activos y una reventa inactiva | Filtrar por estado, tipo y nombre; buscar `%`; paginar; tipo inexistente; `size=101` | Solo coincidencias y en orden; `%` literal; 400 en los inválidos | Aprobado |
| CP-48 | Igual y sesión de MESERO | Pedir inactivos; detalle de un inactivo; detalle de un activo | Solo activos; 404; sin receta ni existencia | Aprobado |
| CP-49 | P2 | ID inexistente y `abc` | 404 y 400 | Aprobado |
| CP-F18 | Un activo y un inactivo | Abrir con filtros en la URL | Consulta con esos filtros; precio `$1,055.00`; solo el activo ofrece desactivar | Aprobado |
| CP-F19 | Sesión de mesero | Abrir el catálogo e intentar la edición | Sin acciones; regresa a inicio | Aprobado |

## Modificar producto · RF09

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-50 | Un preparado | Cambiar solo el precio a `60.5` | 200 con `60.50`; conserva nombre y receta; queda en auditoría | Aprobado |
| CP-51 | Un preparado | Edición vacía; nombre en blanco; precio negativo o `0.00`; campo `activo`; ID inexistente | 400 en las entradas inválidas, con error en `precioVenta` para ambos precios; 404 para el ID inexistente | Aprobado |
| CP-F22 | Formulario de edición | Guardar sin cambios; cambiar solo el precio | No llama a la API; después envía solo el precio | Aprobado |
| CP-F23 | Formulario de edición | La API responde 400 en `nombre` | Marca el campo y conserva lo capturado | Aprobado |
| CP-F84 | Formulario de edición | Escribir `0.00` como precio de venta y enviar | Marca el campo con el mensaje de precio mayor que cero y no llama a la API | Aprobado |

## Imagen del producto · RF07 a RF09

Precondición: un administrador y el producto de reventa «Agua embotellada». Las pruebas usan los primeros bytes de
cada formato y una carpeta de imágenes propia (`target/imagenes-prueba`).

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-174 | Producto sin imagen | Subir un PNG; consultarla; reemplazarla por un JPG y después por un WebP; consultar como mesero | 200 con `imagen` versionada; la base guarda `productos/<UUID>.png` y el archivo existe; se sirve con `image/png`, caché privada y `nosniff`; al reemplazar cambia la ruta y la URL y se borra el archivo anterior; tres auditorías; el mesero ve la URL en el listado y la imagen | Aprobado |
| CP-175 | Producto sin imagen | Subir texto con nombre `.png`; archivo vacío; 2 MB + 1 byte; otra parte en lugar de `archivo`; producto inexistente | 400 «Usa una imagen JPG, PNG o WebP.» y «Selecciona una imagen.»; 413 «La imagen pesa más de 2 MB.»; 400; 404; no queda ruta ni archivo nuevo | Aprobado |
| CP-176 | Producto con imagen y sesión de cada otro rol | Subir, quitar sin y con CSRF, consultar | 403 en subir y quitar; la imagen sigue; el mesero la consulta (200) y los demás reciben 403 | Aprobado (4 roles) |
| CP-177 | Producto sin imagen | Quitar; subir; desactivarlo y consultarla como mesero y administrador; quitarla | 409 `SIN_IMAGEN`; 404 para el mesero y 200 para el administrador; al quitarla, sin `imagen`, ruta nula, archivo borrado, 404 al consultarla y auditoría | Aprobado |
| CP-178 | Producto | Por SQL: rutas `../secreto.png`, `productos/foto.png`, una URL externa, extensión `.svg` y ruta absoluta | PostgreSQL rechaza cada una | Aprobado (5 rutas) |
| CP-F88 | Un producto con imagen y otro sin ella | Abrir el listado; simular que la imagen no carga | Muestra la imagen solo en el primero; si no carga, muestra el marcador | Aprobado |
| CP-F89 | Edición de un producto sin imagen | Elegir un SVG; un PNG de 2 MB + 1 byte; un WebP; guardar | Avisa del formato y del tamaño sin llamar a la API; muestra la vista previa y «Cambiar imagen»; al guardar solo sube la imagen y vuelve al listado | Aprobado |
| CP-F90 | Edición de un producto con imagen | Quitar imagen y guardar | Deja de mostrar la vista previa y llama a quitarla | Aprobado |
| CP-F91 | Alta de reventa con imagen; la API rechaza la imagen | Guardar; guardar otra vez | Sube la imagen al producto creado; avisa «El producto se registró, pero no se guardó la imagen…» con enlace a su edición y no lo registra de nuevo | Aprobado |

## Desactivar producto · RF10

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-52 | Una reventa | 1. Sin confirmar. 2. Confirmar. 3. Consultar. 4. Repetir | 1. 400 y sigue activo. 2. Inactivo. 3. Conserva datos. 4. 409 `PRODUCTO_INACTIVO` | Aprobado |
| CP-53 | Una reventa y sesión de cada rol no administrador | Alta, edición, desactivación e insumos; consultar productos | 403 en las cuatro sin cambios; consultar solo lo permite al mesero | Aprobado (4 roles) |

## Consultar inventario · RF16

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-54 | Dos insumos y una reventa | Listar; buscar sin distinguir mayúsculas; filtrar por tipo y nivel bajo; buscar `%`; ID inexistente | Unidad, existencia, mínimo y nivel correctos; igual al mínimo cuenta como bajo; 404 | Aprobado |
| CP-55 | Tres movimientos | Historial por existencia, artículo, tipo y fechas; rango invertido; fecha inválida | Más reciente primero, con cambio, saldos y responsable; 400 en los inválidos | Aprobado |
| CP-56 | Movimiento a las 05:59:59Z del 11 de marzo | Filtrar el 10 y el 11 de marzo | Aparece solo el 10: se usa la zona de Ciudad de México | Aprobado |
| CP-F24 | Existencia de 1200.5 g | Abrir con filtro de nivel bajo | Muestra `1,200.5 g` y «Bajo»; no pierde precisión en cantidades grandes | Aprobado |
| CP-F25 | Un ajuste rechazado | Abrir movimientos | Cantidad tachada y «Sin cambio» en el saldo | Aprobado |
| CP-F26 | Sesión de administrador | Abrir el detalle de una existencia | Consulta sin formularios | Aprobado |

## Registrar movimiento de inventario · RF17

Una salida manual registra una causa conocida (merma, consumo del personal, devolución a proveedor u otra). Un faltante
sin causa se corrige con un ajuste (RF18), que fija el saldo contado y requiere autorización.

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-57 | Sesión de ALMACENISTA | Entrada y salida de 100.5 | 201; saldo, versión, historial y auditoría actualizados | Aprobado |
| CP-58 | Existencia de 5 | Salida de 5.000001 | 409 `EXISTENCIA_INSUFICIENTE`; saldo 5 y sin movimiento | Aprobado |
| CP-59 | Existencia de 10 | Seis salidas simultáneas de 4 | Dos 201 y cuatro 409; saldo 2 | Aprobado |
| CP-60 | Existencia de -3 | Salida de 1; entrada de 2 | 409; 201 con saldo -1 | Aprobado |
| CP-61 | Sesión de ALMACENISTA | Cantidades inválidas; tipos AJUSTE y CONSUMO_VENTA; existencia inexistente; saldo fuera de rango; sin clave | 400, 400, 404, 400 y 400 | Aprobado |
| CP-62 | Sesión de ALMACENISTA | Repetir con la misma clave; con otros datos; cuatro simultáneas con la misma clave | Misma respuesta sin aplicar de nuevo; 409 `IDEMPOTENCIA_CONFLICTO`; se aplica una vez | Aprobado |
| CP-63 | Existencia en 0 | Salida rechazada; entrada; repetir la salida con la misma clave | 409 y después 201 | Aprobado |
| CP-F27 | Detalle de existencia | Fallo de red, reintento y otra operación igual | El reintento usa la misma clave; la operación siguiente, otra | Aprobado |
| CP-F28 | Detalle de existencia | La API rechaza la salida | Muestra el motivo, conserva la cantidad y actualiza el saldo | Aprobado |
| CP-172 | Existencia con 50 | Salida sin causa; «Otro» sin nota o con espacios; causa desconocida; entrada con causa; nota de 301; después consumo del personal con « Comida del turno » y «Otro» con «Muestra»; por SQL, quitar la causa o la nota de «Otro» | 400 en cada intento y el saldo sigue en 50; 201 con causa y nota recortada; el historial y la auditoría muestran la causa; PostgreSQL rechaza ambos cambios | Aprobado |
| CP-F85 | Detalle de existencia | Elegir salida; registrar sin causa; con «Otro» sin nota; después con « Muestra para cliente » | Aparecen causa, nota y la indicación de ajuste; pide la causa y luego la descripción sin llamar a la API; envía causa y nota recortada | Aprobado |
| CP-F87 | Historial con una salida por merma | Abrir movimientos | Muestra «Merma (caducado o dañado): Caducó» | Aprobado |

## Nivel mínimo · RF16

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-173 | Insumo con 20 y mínimo 10, con una solicitud de ajuste pendiente | Cambiar el mínimo a 25.5; repetirlo; valores -1, «abc», vacío y 7 decimales; existencia inexistente; como administrador; volver a 0 | Nivel bajo con saldo 20 y la misma versión; aparece en «nivel bajo»; auditado una sola vez; la solicitud se autoriza; 400, 404 y 403; con 0 deja de estar bajo | Aprobado |
| CP-F86 | Detalle de existencia | Mínimo -1; después «25,0»; abrir como administrador | Pide una cantidad desde 0 sin llamar a la API; envía «25.0» e informa nivel bajo; el administrador no ve el campo | Aprobado |

## Ajustar inventario · RF18

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-64 | Existencia de 20 | Solicitar 15.5 con justificación | 201 PENDIENTE; la existencia sigue en 20; aparece al filtrar por estado | Aprobado |
| CP-65 | Existencia de 20 | Sin diferencia; sin justificación; negativa; existencia inexistente; sin clave | 400 en todas; no se guarda | Aprobado |
| CP-F29 | Detalle de existencia | Enviar sin justificación; después completa | Muestra el error; luego lleva al detalle de la solicitud | Aprobado |
| CP-F33 | Una solicitud | Filtrar por estado | Lista con ese estado | Aprobado |

## Autorizar ajuste de inventario · RF15

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-66 | Existencia de 20 y solicitud de 15 | Autorizar | Saldo 15; movimiento AJUSTE de -5 con solicitud, motivo, responsable y fecha | Aprobado |
| CP-67 | Igual | Rechazar | Saldo 20; queda en el historial con responsable y sin movimiento | Aprobado |
| CP-68 | Igual, con movimientos posteriores | Autorizar; rechazar; nueva solicitud y autorizar | 409 `EXISTENCIA_CAMBIADA` y sigue pendiente; 200; saldo 15 | Aprobado |
| CP-69 | Igual y dos administradores | Dos autorizaciones y un rechazo simultáneos | Una 200 y dos 409; a lo sumo un movimiento | Aprobado |
| CP-70 | Igual | Decisión inválida; inexistente; resolver dos veces | 400, 404 y 409 `AJUSTE_RESUELTO` | Aprobado |
| CP-71 | Solicitud pendiente y sesión de cada rol | Consultas, movimiento, solicitud y resolución | Consultan administrador y almacenista; operan almacenista; resuelve administrador; los demás 403 | Aprobado (5 roles) |
| CP-72 | Almacenista con sesión, desactivado después | Registrar un movimiento | 401; saldo intacto | Aprobado |
| CP-F30 | Sesión de almacenista | Abrir una solicitud pendiente | Informa que está pendiente, sin botones | Aprobado |
| CP-F31 | Sesión de administrador | Autorizar | Muestra quién y cuándo; retira los botones | Aprobado |
| CP-F32 | Sesión de administrador | Autorizar y recibir 409 | Muestra el motivo; solo permite rechazar | Aprobado |

## Registrar pedido · RF01

Precondición común de la fase 3: una sesión de administrador, dos de mesero (Juan y Ana), una de cajero y una de
cocinero; mesas 1 y 2 libres; productos «Agua» a 20.00 y «Galleta» a 15.50. «Comanda» significa dos aguas y una galleta
sin azúcar enviadas por Juan en la mesa 1.

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-73 | Común | Juan abre la mesa 1; ambos meseros consultan mesas | 201 con Juan como responsable y total 0.00; la mesa 1 queda ocupada; Ana ve quién la atiende, pero no el ID de la cuenta | Aprobado |
| CP-74 | Común | Seis aperturas simultáneas de la mesa 1 por dos meseros; abrir una mesa inexistente | Una 201, las demás 409 `MESA_OCUPADA`; 400 | Aprobado |
| CP-75 | Cuenta abierta | Enviar la comanda; subir el precio del agua; enviar otra | `ENVIADA`, subtotal 55.50 e historial con Juan; la primera conserva su precio; total 269.00 | Aprobado |
| CP-76 | Cuenta abierta | Enviar 1 agua, 2 aguas con observación en blanco y 1 agua «Fría» | Dos líneas: 3 aguas y 1 «Fría» | Aprobado |
| CP-77 | Cuenta abierta | Vacía; cantidades 0, -1 y 1000; suma mayor que 999; producto inexistente o desactivado | 400 o 409 `PRODUCTO_NO_DISPONIBLE`; no se guarda ninguna comanda | Aprobado |
| CP-78 | Común | Repetir apertura y envío con la misma clave; enviar sin clave | Misma respuesta y una sola comanda; 400 | Aprobado |
| CP-79 | Comanda de Juan | Ana consulta, envía, edita y cierra; el cajero consulta | 404 en las de Ana; 200 al cajero | Aprobado |
| CP-F42 | Pantalla de mesas | Abrir una mesa libre | Solo ofrece abrir las libres y ver las propias; lleva a la cuenta | Aprobado |
| CP-F43 | Pantalla de mesas | La API responde 409 | Muestra el motivo y actualiza | Aprobado |
| CP-F44 | Sesión de cajero | Abrir mesas | Ve cuentas sin poder abrir | Aprobado |
| CP-F45 | Cuenta con comanda | Recibir un evento de cambio | Se actualiza sin contar como actividad y muestra la cancelación | Aprobado |
| CP-F47 | Comanda nueva | Agregar dos veces, anotar, fallar por red y reintentar | Subtotal 40.00; conserva lo capturado y reutiliza la clave | Aprobado |
| CP-F51 | Importes | Sumar 0.10 y 0.20, y un importe de 12 enteros | Suma exacta | Aprobado |

## Modificar pedido · RF02

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-80 | Comanda enviada | 5 aguas y sin galleta | Total 100.00, una línea, versión 1 y sin aviso | Aprobado |
| CP-81 | Comanda en preparación | Guardar sin diferencia; después 3 aguas | Sin aviso; después aviso `MODIFICACION_COMANDA` a cocina con mesa y comanda | Aprobado |
| CP-82 | Dos comandas | Quitar todo; detalle ajeno, repetido o en 0; comanda inexistente; borrar por SQL | 409 `ULTIMO_DETALLE`, 400 y 404; total intacto; PostgreSQL rechaza el borrado | Aprobado |
| CP-83 | Comanda | Editar con versión anterior, lista y cancelada | 409 `VERSION_OBSOLETA` y `COMANDA_NO_EDITABLE` | Aprobado |
| CP-84 | Comanda y agua desactivada | Dejar 1 agua | Se acepta con el precio guardado | Aprobado |
| CP-F48 | Pantalla de modificar | Sumar una agua y eliminar la galleta | Recalcula; no permite eliminar la última línea; envía solo lo que queda | Aprobado |
| CP-F49 | Pantalla de modificar | La API responde 409 | Muestra el estado vigente, conserva lo editado y permite guardar con la versión nueva | Aprobado |

## Cancelar pedido · RF03

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-85 | Comanda enviada, en preparación o lista | El cajero cancela con justificación | `CANCELADA` con sus detalles, cajero y motivo; total 0.00; aviso a cocina; sale de la cola | Aprobado (3 estados) |
| CP-86 | Comanda | Sin justificación; versión anterior; inexistente; repetir; servida | 400, 409, 404, 409 y 409 | Aprobado |
| CP-87 | Comanda en preparación | Cancelar y marcar lista a la vez, cinco repeticiones | Una 200 y una 409 | Aprobado |
| CP-F50 | Pantalla de cancelar | Como mesero; como cajero sin y con justificación | Regresa a inicio; exige el motivo; envía | Aprobado |

## Cierre sin cobro y empleados con cuentas

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-88 | Cuenta con comanda | Cerrar; cancelar la comanda; cerrar con versión anterior y con la vigente; operar después | 409 `CUENTA_CON_COMANDAS`, 409, cerrada y auditada; después 409 `CUENTA_CERRADA`; la mesa se puede abrir | Aprobado |
| CP-89 | Juan con cuenta abierta | Desactivarlo, cambiar su rol, cambiar su nombre; cerrar y desactivar | 409 `CUENTAS_ABIERTAS` en las dos primeras; 200 en el nombre; 200 al final | Aprobado |
| CP-90 | Mesero activo | Abrir y desactivar a la vez, cinco repeticiones | Nunca queda un inactivo con cuenta abierta | Aprobado |
| CP-91 | Comanda y sesión de cada rol | Consultas, apertura, envío, edición, cierre y cancelación | Consultan mesero y cajero; opera el mesero; cancela el cajero; los demás 403 | Aprobado (5 roles) |
| CP-F46 | Cuenta sin comandas | Cerrar sin cobro | Exige confirmar y vuelve a mesas | Aprobado |

## Consultar cola de pedidos · RF19

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-92 | Tres comandas en distinto estado | Consultar la cola | Orden de envío; mesa, cuenta, mesero, productos y observaciones; sin precios | Aprobado |
| CP-93 | Una cancelada y una servida | Consultar la cola | Vacía | Aprobado |
| CP-F52 | Cola con dos comandas | Filtrar por estado | Conserva el orden y muestra observaciones sin precios | Aprobado |
| CP-F55 | Pantalla de cocina | Recibir un evento; perder la conexión | Actualiza sin contar como actividad; indica «Sin conexión en vivo» | Aprobado |

## Marcar pedido en preparación · RF20

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-94 | Comanda enviada | Iniciar | `EN_PREPARACION`, versión 1, historial con responsable y fecha; sin aviso al mesero | Aprobado |
| CP-95 | Comanda | De enviada a lista; iniciar dos veces; inexistente; sin versión; versión anterior; cancelada | 409 `TRANSICION_INVALIDA`, 409, 404, 400, 409 y 409 | Aprobado |
| CP-96 | Comanda editada por el mesero | Iniciar con la versión anterior y con la vigente | 409 `VERSION_OBSOLETA` y 200 | Aprobado |
| CP-97 | Dos cocineros | Iniciar a la vez | Una 200 y una 409 | Aprobado |
| CP-F53 | Pantalla de cocina | Iniciar y marcar lista | Usa la versión mostrada y actualiza la tarjeta | Aprobado |
| CP-F54 | Pantalla de cocina | La API responde 409 | Muestra el motivo y actualiza la cola | Aprobado |

## Marcar pedido listo · RF21 y avisos

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-98 | Comanda en preparación | Marcar lista | Aviso `COMANDA_LISTA` solo a Juan, con mesa y cuenta; sigue en la cola | Aprobado |
| CP-99 | Aviso al rol COCINERO | Un cocinero lo marca leído dos veces | 204; leído para esa persona y sin leer para la otra | Aprobado |
| CP-100 | Aviso de Juan | Otras personas lo marcan; inexistente; sin sesión | 404, 404 y 401 | Aprobado |
| CP-101 | Comanda y sesión de cada rol no cocinero | Cola, preparación y lista | 403 sin cambios | Aprobado (4 roles) |
| CP-F56 | Aviso sin leer | «Entendido» en cocina | Lo marca y desaparece | Aprobado |
| CP-F57 | Dos avisos | Abrir la bandeja y recibir un evento | Cuenta los no leídos; destaca los pedidos listos y los faltantes | Aprobado |
| CP-F58 | Pantalla de avisos | Filtrar y marcar | Consulta con el filtro; enlaza la cuenta del mesero; marca lectura | Aprobado |

## Tiempo real · RNF10

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-102 | Cocinero conectado | Enviar, editar, cancelar y una operación rechazada | Un evento por cambio confirmado, con los cinco campos; ninguno por la rechazada | Aprobado |
| CP-103 | Dos meseros conectados | Iniciar y marcar lista | Solo Juan recibe; el de lista llega en menos de 2 s | Aprobado |
| CP-104 | Mesero conectado | Suscribirse a siete destinos que no le corresponden | Se rechazan; cocinero y almacenista entran a los suyos | Aprobado |
| CP-105 | — | Conectar sin sesión y desde otro origen | 401 y 403 | Aprobado |
| CP-106 | Cocinero conectado | Enviar un mensaje por el canal | Se rechaza y no llega a nadie | Aprobado |
| CP-107 | Tres cocineros conectados | Desactivar a uno, cambiar la contraseña de otro y cerrar sesión del tercero | Ninguno recibe más eventos | Aprobado |
| CP-108 | Cocinero conectado a los 40 min | Dejar pasar 6 min más | Deja de recibir y su sesión expira: la conexión no cuenta como actividad | Aprobado |
| CP-F38 | Sin sesión y con cada rol | Iniciar sesión | Se suscribe solo a lo suyo | Aprobado |
| CP-F39 | Conectado | Evento repetido y de otro tipo | Ignora el repetido y filtra por tipo | Aprobado |
| CP-F40 | Conectado | Perder y recuperar la conexión | Avisa para volver a consultar | Aprobado |
| CP-F41 | Conectado | Actualizar el perfil; cerrar sesión | No reconecta; se desconecta | Aprobado |

Se comprobó que CP-107 y CP-108 fallan si se anula la validación de mensajes salientes.

## Marcar pedido servido · RF23

Precondición común de la fase 4: la de la fase 3, más «Café» y «Leche» con 1000 cada uno, «Agua» con 10, y dos
preparados: Capuchino (18.5 de café y 180 de leche) y Latte (250 de leche y 10 de café).

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-109 | Comanda lista | Juan la sirve | `SERVIDA`, versión 3, sin acciones; historial con Juan y fecha; sale de la cola; el total no cambia; la respuesta no incluye saldos | Aprobado |
| CP-110 | Comanda enviada, en preparación y lista | Servir en cada estado; versión anterior; otro mesero; inexistente; sin clave | 409, 409, 409 `VERSION_OBSOLETA`, 404, 404 y 400; sin consumo | Aprobado |
| CP-111 | Comanda servida | Editar, cancelar y cerrar sin cobro | 409 en las tres | Aprobado |
| CP-112 | Comanda lista y sesión de cada otro rol | Servir | 403; sigue lista y sin consumo | Aprobado (4 roles) |
| CP-F59 | Cuenta con comanda lista | Marcar servida y confirmar | Pide confirmación, envía la versión e informa el faltante | Aprobado |
| CP-F60 | Diálogo abierto | Fallo de red, reintento y 409 | Reutiliza la clave; muestra el motivo y actualiza | Aprobado |

## Descontar inventario · RF24

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-113 | Lista: 3 capuchinos y 2 aguas | Servir | Café 944.5, leche 460 y agua 8; tres movimientos con saldos, responsable y comanda; sin alertas; auditado | Aprobado |
| CP-114 | Lista: 3 capuchinos y 2 lattes | Servir | Leche -40 porque la demanda se suma; una sola alerta con faltante 40; saldos encadenados | Aprobado |
| CP-115 | Agua con 10 | Servir 10; después 3 y 3 | 0 sin alerta; -3 y -6 con una alerta cada una, faltante 3; solo almacén las recibe; aparece como nivel bajo | Aprobado |
| CP-116 | Comanda lista | Repetir con la misma clave, con otra y tres a la vez | Un solo descuento; las demás 409 | Aprobado |
| CP-117 | Recetas con insumos en orden inverso | Dos servicios y una salida manual simultáneos, cuatro veces | Todos se completan, sin bloqueos mutuos; café 882 y leche -720 | Aprobado |
| CP-118 | Fallo simulado al guardar avisos o movimientos | Servir y reintentar | 500; comanda lista, saldos, historial y avisos intactos; la misma clave se puede reintentar | Aprobado (2 fallos) |
| CP-119 | Solicitud de ajuste pendiente | Servir y autorizar | 409 `EXISTENCIA_CAMBIADA` | Aprobado |
| CP-120 | Un consumo | Por SQL: repetirlo y registrarlo sin detalle | PostgreSQL rechaza ambos | Aprobado |
| CP-121 | Almacenista y cocinero conectados | Servir sin existencias | Almacén recibe cambios y alertas; cocina, `COMANDA_SERVIDA` | Aprobado |
| CP-F61 | Listado de existencias | Dos cambios seguidos y después filtrar | Una recarga que no cuenta como actividad; filtrar sí cuenta | Aprobado |
| CP-F62 | Detalle de existencia con datos capturados | Cambio de otra existencia, de la propia y reconexión | Solo se actualiza en las dos últimas y conserva lo capturado | Aprobado |
| CP-F63 | Consumo en el historial | Abrir movimientos | Muestra «Consumo», el saldo y la comanda | Aprobado |
| CP-F64 | Alerta de inventario | Abrir avisos como almacenista | Aviso destacado, faltante y enlace a la existencia | Aprobado |
| CP-F65 | Pantalla de avisos | Llega un aviso nuevo | Se recarga sin contar como actividad | Aprobado |

Se comprobó que CP-117 falla con un bloqueo mutuo de PostgreSQL si las existencias no se bloquean por ID.

## Cobrar pedido · RF04

Precondición común de la fase 5: la de la fase 3. «Cuenta servida» es una cuenta de Juan con dos aguas y una galleta
servidas: 55.50, versión 1.

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-122 | Cuenta con una comanda servida y otra cancelada; después sube el precio del agua a 99 | Cobrar 55.50 en efectivo con la versión consultada | 201: venta COMPLETADA y pago por 55.50 sin referencia; cuenta CERRADA con la misma fecha que la venta, sin acciones y con su cobro; pago a nombre de Juan; auditado; otra cuenta puede abrir la mesa | Aprobado |
| CP-123 | Cuenta vacía, después con una comanda enviada, en preparación y lista, y otra con su única comanda cancelada | Cobrar en cada estado | 409 `CUENTA_SIN_COMANDAS` o `COMANDAS_PENDIENTES` y sin acción COBRAR; al cancelar la pendiente, se cobra | Aprobado |
| CP-124 | Cuenta servida de `55.50`; otra con dos unidades de `999999999999.99` | Cobrar la primera con versión `0` o con total `55.49`; cobrar la segunda con total esperado `1999999999999.98` o `999999999999.99` | 409 `VERSION_OBSOLETA` y `TOTAL_CAMBIADO` para la primera; 400 en `totalEsperado` o 409 `TOTAL_INVALIDO` para la segunda, respectivamente; sin venta | Aprobado |
| CP-125 | Cuenta servida de Juan | Cobrar como Ana; cuenta inexistente; sin clave | 404, 404 y 400; sin venta | Aprobado |
| CP-126 | Cuenta servida y sesión de cada otro rol | Cobrar | 403; sin venta | Aprobado (4 roles) |
| CP-127 | Cuenta servida | Cuerpo vacío, método «CHEQUE», totales -1, 55.505, 1e3 y vacío, referencias «A B», «abc$» y de 41 caracteres | 400 con el error de cada campo | Aprobado |
| CP-128 | Cuenta servida | Repetir con la misma clave, con la misma clave y otra referencia, con otra clave, y tres cobros a la vez | Mismo resultado; `IDEMPOTENCIA_CONFLICTO`; `CUENTA_CERRADA`; 201, 409 y 409; una venta y un pago por cuenta | Aprobado |
| CP-129 | Cuenta servida, cuatro veces | Cobrar y enviar otra comanda a la vez; después cancelar una comanda y enviar otra | Gana uno: si gana el envío se cancela y se cobra; después del cobro, cancelar y enviar responden `CUENTA_CERRADA` | Aprobado |
| CP-130 | Fallo simulado al guardar el pago | Cobrar con tarjeta y reintentar dos veces con la misma clave | 500; la cuenta sigue abierta, sin venta ni auditoría; el reintento registra una sola venta | Aprobado |

## Autorizar pago con tarjeta · RF05

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-131 | Cuenta servida | Tarjeta sin referencia, vacía o con espacios; efectivo con referencia; tarjeta « ab-12 »; transferencia sin referencia y con «SPEI9» | 400 en los cuatro primeros; después se guarda «AB-12»; la transferencia se registra con o sin referencia | Aprobado |
| CP-132 | Cuenta servida | Tarjeta y transferencia con 4111-1111-1111-1111; después tarjeta con un folio de 12 dígitos | 400 en ambas, sin el número en la respuesta ni en la auditoría; el folio se acepta | Aprobado |
| CP-133 | Un cobro con tarjeta «778899» | Otra cuenta con la misma referencia; con transferencia; un día después con tarjeta | 409 `REFERENCIA_REPETIDA` en el campo y la cuenta sigue abierta; las otras dos se registran | Aprobado |
| CP-134 | Dos cuentas servidas de meseros distintos | Cobrar ambas a la vez con la referencia «5050» | 201 y 409; un solo pago | Aprobado |
| CP-135 | Cuenta servida a la que se agrega otra comanda servida | Cobrar con tarjeta «616161» y la versión anterior; después con la versión nueva y 75.50 | 409 `VERSION_OBSOLETA` sin registrar nada; después se registra con la misma referencia | Aprobado |
| CP-F66 | Cuenta con COBRAR; con una comanda lista; cerrada con cobro cancelado | Abrir la cuenta | Botón «Cobrar $55.50»; sin botón y con la indicación de servir; método, referencia y «Cancelada» | Aprobado |
| CP-F67 | Cuenta cobrable | Cobrar en efectivo | Envía versión, total y sin referencia; muestra el comprobante y la mesa libre | Aprobado |
| CP-F68 | Cuenta cobrable | Tarjeta sin referencia, con espacios, con el número de una tarjeta, en transferencia y con « 778899 » | Errores sin llamar a la API; envía «778899» | Aprobado |
| CP-F69 | Cuenta cobrable | Cobro con tarjeta sin conexión; salir, volver y reenviar | Conserva método, referencia y clave; avisa del intento sin confirmar; registra una vez | Aprobado |
| CP-F70 | Cuenta cobrable | Cobro con tarjeta y 409 `VERSION_OBSOLETA`; salir y volver; «Ya lo revertí» | Indica revertir la referencia A1 por 55.50 y cobrar 75.50; se conserva al volver; después vuelve al formulario | Aprobado |
| CP-F71 | Cuenta cobrable; cuenta ya cobrada | 409 `REFERENCIA_REPETIDA`; abrir el cobro de la cobrada | Marca el campo y muestra el motivo; «ya se cobró por $55.50» | Aprobado |

## Consultar historial de ventas · RF06

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-136 | Ventas en efectivo, tarjeta (Ana, mesa 2, cancelada) y transferencia | Listar sin filtros y por estado, método, mesero, mesa y combinados; página 2 de tamaño 2; opciones | Orden de la más reciente; cada filtro devuelve lo esperado; total 3 con una fila; meseros y mesas ordenados | Aprobado |
| CP-137 | Venta movida al último segundo y al inicio de un día | Filtrar ese día, el siguiente y hasta el anterior | Se incluye en el día, en la zona del negocio, y no en los otros | Aprobado |
| CP-138 | Ventas justo en el límite de 24 meses y un segundo antes | Listar, con `desde=2000-01-01`, detalle y cancelación | Solo la de dentro; la de fuera responde 404 y sigue en la base | Aprobado |
| CP-139 | Venta con una comanda servida con observación y otra cancelada | Detalle como cajero y administrador; venta inexistente | Pago, mesa, mesero, precios guardados y la cancelación de la comanda; CANCELAR solo para el administrador; 404 | Aprobado |
| CP-140 | Venta y sesión de mesero, cocinero y almacenista; sin sesión | Listado, detalle y opciones | 403; 401 sin sesión | Aprobado (3 roles) |
| CP-141 | Cajero | Filtros inválidos: fecha, estado, método, mesero, mesa, página, tamaño y rango invertido | 400 | Aprobado (8 valores) |
| CP-F72 | Historial con filtros en la URL | Abrir `/ventas?estado=…&metodo=…&meseroId=…&desde=…` | Consulta con esos filtros, los muestra seleccionados y enlaza al detalle | Aprobado |
| CP-F73 | Venta cancelada como cajero; completada como administrador | Abrir el detalle | Pago, comandas y cancelación; «Cancelar venta» solo si la API lo ofrece | Aprobado |

## Cancelar venta · RF22

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-142 | Venta en efectivo | Cancelar con « Cobro equivocado » | Venta CANCELADA sin acciones; devolución por el total, motivo recortado y administrador; pago, cuenta, movimientos y existencias sin cambios; auditado; la cuenta muestra el cobro cancelado | Aprobado |
| CP-143 | Venta con tarjeta y con transferencia | Sin referencia, vacía, con espacios y con «con espacio»; después « rev-1 » | 400; después se guarda «REV-1» | Aprobado (2 métodos) |
| CP-144 | Ventas en efectivo, tarjeta y transferencia | Efectivo con referencia; tarjeta y transferencia con el número de una tarjeta | 400; sin cancelación | Aprobado |
| CP-145 | Venta completada | Sin confirmar o con `false`; motivo vacío, en blanco o de 501; sin clave; inexistente | 400 en cada campo; 404; sin cancelación | Aprobado |
| CP-146 | Venta y sesión de cada otro rol | Cancelar | 403; sin cancelación | Aprobado (4 roles) |
| CP-147 | Venta en efectivo; otra con tarjeta | Repetir con la misma clave y con otra; tres cancelaciones a la vez | Mismo resultado y `VENTA_CANCELADA`; 200, 409 y 409, ambas con `VENTA_CANCELADA` | Aprobado |
| CP-148 | Una devolución con «R1» | Cancelar otra venta con «r1» | 409 `REFERENCIA_REPETIDA`; la venta sigue completada | Aprobado |
| CP-149 | Fallo simulado al guardar la devolución | Cancelar y reintentar con la misma clave | 500 y la venta sigue completada; el reintento la cancela | Aprobado |
| CP-150 | Venta con tarjeta | Por SQL: borrar el pago, cambiar monto, total, estado sin devolución, quitar la referencia, cambiar a efectivo, referencia en blanco, segunda venta, segundo pago y devolución con referencia en efectivo | PostgreSQL rechaza cada una | Aprobado (10 sentencias) |
| CP-151 | Cuenta servida y abierta | Por SQL: venta sin pago; venta y pago sobre la cuenta abierta | PostgreSQL rechaza ambas al confirmar | Aprobado |
| CP-F74 | Venta con tarjeta como administrador | Enviar vacío; después motivo, «R-1» y confirmar | Pide motivo, referencia y confirmación; envía y vuelve al detalle | Aprobado |
| CP-F75 | Venta en efectivo ya cancelada por otro | Motivo y confirmar | No pide referencia e indica entregar desde caja; muestra «ya está cancelada» y actualiza | Aprobado |

Se comprobó que CP-124 falla si no se compara el total y que CP-147 falla si la venta no se bloquea antes de
cancelarla.

## Recibir el efectivo y dar el cambio · RF25 y RF26

Precondición común: la de la fase 3; Juan cobra en efectivo cuentas con dos aguas servidas (40.00 cada pago) y María
es la cajera. Desde la migración V13 caja no presta un fondo antes del cobro: el mesero lleva el efectivo a caja y el
cajero registra lo recibido de cada pago y le da el cambio.

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-152 | Un pago en efectivo de 40 | Recibirlo con 50 | 201: monto 40.00, cambio 10.00, mesero y cajera; el pago muestra 50.00 recibidos y 10.00 de cambio; `fondo_cambio` guarda 50.00/10.00; sale de pendientes; auditoría `EFECTIVO_RECIBIDO` con el cambio | Aprobado |
| CP-153 | Un pago de 40; después dos más de Juan | Recibir el primero con 40.00; los otros dos juntos con 100 y 40 | Cambio 0.00 sin fila de cambio; la entrega conjunta suma 80.00 con 60.00 de cambio, pagos en orden de ID y una sola fila de cambio | Aprobado |
| CP-154 | Pagos de Juan en efectivo, con tarjeta y por transferencia; un pago de Ana | 39.99; con la tarjeta o la transferencia; con el de Ana; inexistente; repetido; vacío; montos 0, -1, «abc» y 1.005; sin clave | 409 `MONTO_INSUFICIENTE` en `pagos` con el importe 40.00; `PAGO_NO_EFECTIVO`; `PAGO_DE_OTRO_MESERO`; 400 en los demás; nada registrado | Aprobado |
| CP-155 | Pagos en efectivo de Juan | Repetir con la misma clave y con otra; tres rondas de recepciones simultáneas con [a, b], [b, a] y [b] | El mismo resultado y después `PAGO_ENTREGADO`; en cada ronda una sola entrega y las demás `PAGO_ENTREGADO`; ningún pago repetido | Aprobado |
| CP-156 | Dos pagos de Juan (uno de una venta cancelada), uno de Ana, uno con tarjeta y uno por transferencia | Consultar todos y los de Juan | Solo los en efectivo, del más reciente al más antiguo; la venta cancelada aparece con su estado y su importe | Aprobado |
| CP-157 | Pago en efectivo y sesión de cada otro rol | Consultar pendientes y recibir | 403; sin entrega | Aprobado (4 roles) |
| CP-158 | Cuenta servida | Como cajera, `POST /cuentas/:id/fondo-cambio` y `GET /caja/fondos`; consultar la cuenta; cobrar con tarjeta | 403 y 404; la cuenta no ofrece acciones de fondo; el cobro con tarjeta se registra | Aprobado |
| CP-159 | Pago en efectivo y fallo simulado al guardar el cambio | Recibir con 100 y reintentar con la misma clave | 500, sin entrega y el pago sigue pendiente; el reintento responde 201 con una fila de cambio | Aprobado |
| CP-160 | Una entrega con cambio | Por SQL: cambio a un pago con tarjeta, lo recibido distinto del pago más el cambio, sin pago, sin entrega, segundo cambio del mismo pago, cambiar el monto o el cambio total de la entrega, quitar o repetir un pago | PostgreSQL rechaza cada una | Aprobado (9 sentencias) |
| CP-F76 | Cuenta cobrable | Abrir como cajero; abrir el cobro como mesero | La cuenta no menciona el fondo; los tres métodos están habilitados y el efectivo indica llevar el dinero a caja | Aprobado |
| CP-F77 | Pagos de Juan (480.00 y 15.50) y de Ana | Abrir **Caja** | Agrupa por mesero; propone el pago más reciente con lo recibido igual a su importe y cambio $0.00 | Aprobado |
| CP-F78 | Pago de 480.00 elegido | Recibido 450; después 500 y registrar | «No cubre el pago de $480.00.» sin llamar a la API; cambio $20.00; envía `montoRecibido` 500 y muestra «Recepción 8 registrada: … Entrega $20.00 de cambio.»; actualiza la lista | Aprobado |
| CP-F79 | El paso anterior sin registrar | Elegir también el de 15.50 con 20; quitar el de 480.00 | Suma $495.50 y $4.50 de cambio; después solo cuenta $15.50 | Aprobado |
| CP-F80 | Pago ya recibido en otra caja | Registrar | Muestra el motivo de `PAGO_ENTREGADO` y actualiza la lista | Aprobado |

## Verificaciones pendientes

Se repitieron con condiciones reales las comprobaciones que antes se simularon o no se habían vuelto a hacer.

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-F83 | Sesión de cada rol | Mostrar el menú principal | Cada rol ve solo sus opciones; **Caja** solo el cajero | Aprobado (5 roles) |

## Aceptación final · Fase 7

| ID | Precondición | Pasos y datos | Resultado esperado | Estado |
|---|---|---|---|---|
| CP-167 | Base nueva con los cinco roles | Insumo y preparado; entrada y ajuste autorizado; cuenta con dos comandas, una editada y otra cancelada; cocina las prepara; servir; cobrar en efectivo; caja recibe 200 del cliente; consultar y cancelar la venta | Saldos 480 y 426 tras el ajuste y el consumo; total 125.00; caja da 75.00 de cambio; la cancelación no repone inventario; cada acción auditada con su responsable | Aprobado |
| CP-168 | 50 000 ventas de dos años y 100 000 movimientos | 20 veces: ventas sin filtros, por mesero y método, del último mes, última página, detalle, opciones y movimientos | p95 menor a 2 s en todas | Aprobado |
| CP-169 | Base de prueba | Mesero, administrador y almacenista a la vez, 15 vueltas: catálogo, enviar y editar comandas, altas y ediciones de productos y empleados, movimientos | p95 menor a 1.5 s, sin errores | Aprobado |
| CP-170 | La historia de CP-168 | Mesero, cocinero, almacenista y cajero a la vez, 15 vueltas | p95 menor a 1.5 s, sin errores; las 15 comandas quedan listas | Aprobado |

Los tiempos están en [aceptacion.md](aceptacion.md#rendimiento). Para que la carga tome segundos, CP-168 y CP-170
insertan la historia con las validaciones por fila suspendidas y después comprueban con una consulta que cuadre.

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
| CP-M10 | Sesión de almacenista | Abrir inventario y el detalle del Brownie | Muestra existencias, unidad y niveles; el detalle permite mover y ajustar | Aprobado |
| CP-M11 | Brownie con 4 piezas | Salida de 9; entrada de 9; solicitar 11 con justificación | Rechaza y conserva el 9; queda en 13; la solicitud queda pendiente | Aprobado |
| CP-M12 | Solicitud anterior y sesión de administrador | Autorizar y abrir movimientos | Existencia 11; «Ajuste -2 pza» con referencia | Aprobado |
| CP-M13 | Sesión de administrador | Alta de «Galleta de prueba» a 18,5 con 12.5 g de azúcar | Aparece a $18.50 con su receta | Aprobado |
| CP-M14 | Producto anterior | Cambiar a 20 y desactivar con confirmación | $20.00 y después Inactivo | Aprobado |
| CP-M15 | Sesión de mesero | Abrir el catálogo y después `/inventario` | Solo activos y sin acciones; regresa a inicio | Aprobado |
| CP-M16 | Sesión cerrada | Enviar vacío; pedir ayuda; entrar con «Recordarme»; salir | Errores por campo y ayuda; al volver propone el correo sin contraseña | Aprobado |
| CP-M17 | Administrador | Productos, inventario, movimientos, ajustes, alta y empleados a 360 px; movimientos a 790, 980 y 1095 px; productos y empleados a 980 px; acceso a 360 y 1400 px | Sin desplazamiento horizontal | Aprobado tras corregir |
| CP-M18 | Sesión de mesero | Abrir la mesa 5 y enviar dos capuchinos «Sin azúcar» y un cheesecake | Comanda enviada, $185.00 | Aprobado |
| CP-M19 | Mesero en Productos | La cocinera inicia y marca lista desde otra sesión | Aparece el aviso con mesa y cuenta, y el contador; leído, persiste al recargar | Aprobado |
| CP-M20 | Sesión de cocinera | El mesero envía dos comandas y modifica una en preparación | Las tarjetas y el aviso llegan sin recargar | Aprobado |
| CP-M21 | Comanda enviada | Sumar un brownie y guardar | Subtotal $210.00 y total actualizado | Aprobado |
| CP-M22 | Sesión de cajero | Cancelar sin y con justificación | Exige el motivo; queda cancelada con responsable y el total baja | Aprobado |
| CP-M23 | Mesero | Abrir la mesa 7 y cerrarla sin cobro | Confirma y queda libre | Aprobado |
| CP-M24 | Mesero, cajero y cocinero | Mesas, cuenta, nueva, modificar, cocina y avisos a 360 px | Sin desplazamiento horizontal | Aprobado |
| CP-M25 | Comanda lista | Marcar servida y confirmar | «La comanda 1 quedó servida», etiqueta Servida y sin botones | Aprobado |
| CP-M26 | Almacenista en Inventario, Brownie con 11 | Se sirven 15 desde otra sesión | Sin recargar pasa a -4 y «Bajo», con aviso destacado y contador | Aprobado |
| CP-M27 | Aviso anterior | Abrir avisos y su enlace, a 360 px, y marcarlo leído | «Faltan 4 pza»; el historial muestra «Consumo -15 pza» con la comanda; sin desplazamiento horizontal; queda leído | Aprobado |
| CP-M28 | Cuenta 1 con una comanda enviada, como mesero | Abrir la cuenta; el cajero cancela la comanda desde otra sesión | Indica que deben estar servidas y no ofrece cobrar; sin recargar aparece «Cobrar $1,235.00» | Aprobado |
| CP-M29 | Cuenta 4 servida por 40.00 | Tarjeta con 4111-1111-1111-1111 | «No captures el número de la tarjeta» y no se envía | Aprobado tras corregir |
| CP-M30 | El paso anterior | Otra comanda desde otra sesión; registrar «778899»; recargar; «Ya lo revertí»; el cajero cancela la comanda; cobrar «778900» | Indica revertir 778899 por $40.00 y cobrar $60.00, también tras recargar; después pide servir; se registra la venta 2 por $40.00 | Aprobado |
| CP-M31 | Administrador con tres ventas | Historial y `?metodo=TRANSFERENCIA` | Muestra las ventas; el filtro deja la venta 1 y las mesas 2 y 5 | Aprobado |
| CP-M32 | Venta 2 con tarjeta | Cancelar con motivo y «rev-778900», sin confirmar y después confirmando | Pide confirmar; el detalle queda Cancelada, conserva el pago 778900 y muestra Rodrigo Herrera, $40.00 y «REV-778900» | Aprobado |
| CP-M33 | Mesero y administrador | Cobro en efectivo de la cuenta 5, historial, detalles y cancelar a 360 px | Sin desplazamiento horizontal; los filtros en dos columnas | Aprobado tras corregir |
| CP-M40 | API de desarrollo con 45 minutos de inactividad; sesiones A y B del almacenista, de 19:27:16 a 20:14:29 | A: una consulta pasiva por minuto (como el sondeo). B: lo mismo y una solicitud activa cada 10 minutos. Al final, una activa en cada una | A responde 200 hasta el minuto 44 y 401 desde el minuto 45: esa primera solicitud cerró la sesión, así que la final responde 401 `SESION_REQUERIDA`. B responde 200 los 47 minutos. No se guardó el cuerpo de la primera respuesta 401; CP-10 y CP-11 comprueban con reloj simulado que su código es `SESION_EXPIRADA` | Aprobado (reloj real) |
| CP-M41 | API en HTTPS (puerto 8443) con un certificado local validado por el cliente | Pedir CSRF, iniciar sesión, cerrar sesión sin token, HTTP al mismo puerto; STOMP por `wss`, suscribirse a `/topic/inventario` y registrar una entrada; conectar con otro origen | Cookies `XSRF-TOKEN` y `SIVANT_SESION` con `Secure` y `SameSite=Lax` (la de sesión también `HttpOnly`); 403 sin token; 400 por HTTP; `CONNECTED` y `EXISTENCIA_ACTUALIZADA` por `wss`; otro origen rechazado | Aprobado |
| CP-M42 | Las cinco cuentas ficticias | Iniciar sesión con cada una, leer el menú y abrir `/caja/entregas` | Administrador: Inicio, Empleados, Productos, Ventas, Inventario y Avisos. Mesero: Inicio, Mesas, Productos y Avisos. Cajero: Inicio, Mesas, Caja, Ventas y Avisos. Cocinero: Inicio, Cocina y Avisos. Almacenista: Inicio, Inventario y Avisos. Solo el cajero abre `/caja`; los demás vuelven a su inicio | Aprobado |
| CP-M44 | Administrador | Alta con «MESERO@sivant.test», ya usado | El error aparece una vez en el campo de correo, el aviso general pide revisar lo marcado y se conservan los datos | Aprobado |
| CP-M45 | Mesero en una nueva comanda a 360 px | Agregar un producto | El selector de cantidad mide 136 × 44 px y no hay desplazamiento horizontal | Aprobado |
| CP-M46 | Los cinco roles; 36 pantallas | Auditoría en la página a 1024, 768 y 360 px: idioma, tipografía base, letra mínima, contraste AA, etiquetas, nombres accesibles, un `h1` y desplazamiento horizontal | Tipografía base de 16 px, ninguna letra menor a 14 px, contraste suficiente, todos los controles con etiqueta y nombre, un `h1` y sin desplazamiento horizontal | Aprobado tras corregir |
| CP-M47 | Inicio de sesión | Recorrer con Tab | El control enfocado muestra un contorno azul de 2.4 px (`:focus-visible` global en todas las pantallas) | Aprobado |
| CP-M48 | Maquetas de `Interfaces` | Comparar empleados, editar producto, movimientos y ajuste con su pantalla | Misma estructura, encabezado, tarjetas, tablas, avisos y botones; las diferencias están en [decisiones.md](decisiones.md) | Aprobado |
| CP-M49 | Clon de la rama en otra carpeta, `.env` nuevo y base vacía (proyecto de Compose aparte) | Pasos del README; `npm ci`; `scripts/recorrido-demostracion.py`; entrar con los cinco roles | 9 migraciones aplicadas; `npm ci` sin errores; 19 de 19 pasos correctos; cada rol ve su menú | Aprobado |
| CP-M51 | API con V13 sobre la base de desarrollo (5 fondos regresados a caja); cajera; cuenta 16 de Juan cobrada en efectivo por 40.00 | Revisar las entregas migradas; en **Caja** recibir 30 y después 100; repetir a 360 px | Los fondos migrados quedan como cambio (por ejemplo, recibido 240 y cambio 200); «No cubre el pago de $40.00»; cambio $60.00 y «Recepción 7 registrada… Entrega $60.00 de cambio.»; la lista queda vacía; sin desplazamiento horizontal, campos etiquetados y **Caja** en el menú | Aprobado |
| CP-M52 | API de la rama con V14 sobre la base de desarrollo; administrador, mesero y cajera | Con un script contra la API: subir un PNG a «Agua de jamaica», consultarla, reemplazarla, subir un SVG con nombre `.png`, consultar como mesero, subir como cajera; después quitarla | 200 con URL versionada y archivo en `backend/almacen/productos`; `image/png` con caché privada; el reemplazo cambia la URL y borra el anterior; el SVG se rechaza; el mesero la ve; la cajera recibe 403; al quitarla no queda archivo | Aprobado (7 de 7) |
| CP-M50 | Almacenista en el Agua embotellada (93 pza, mínimo 12) | Salida de 1 sin causa; con merma y «Se rompió una botella»; mínimo 100; volver a 12; a 360 px | Pide la causa e indica usar un ajuste si no se sabe; queda en 92 y el historial muestra «Merma (caducado o dañado): Se rompió una botella»; el nivel pasa a bajo y vuelve a suficiente; sin desplazamiento horizontal y con todos los campos etiquetados | Aprobado |

### Defectos encontrados

| Caso | Defecto | Corrección |
|---|---|---|
| CP-M09 | A 360 px el encabezado oculto de la tabla ensanchaba la página a 402 px | Se ajustó el estilo y se repitió el caso |
| CP-M04 | El error de correo aparecía dos veces | El mensaje general indica revisar los datos marcados; CP-M44 lo confirma y CP-F15 cubre el error por campo |
| CP-M17 | Las columnas de los listados partían palabras, como «Entrad-a» | Las celdas ya no se estrechan por debajo de una palabra |
| CP-M17 | Movimientos se desbordaba 20 px a 980 px | El menú lateral se pliega antes |
| CP-M17 | A 360 px se cortaba «Ingresa tu contraseña» | Se ajustó el margen interno |
| CP-F10, CP-F18 | Fallaban por tiempo con el equipo cargado | Las pantallas se precargan antes de los casos |
| CP-M18 | «Mesas» no quedaba resaltado dentro de una cuenta | Se separó la clase de la opción incluida |
| CP-M24 | El selector de cantidad se estiraba en móvil | Se ajustó el estilo; CP-M45 lo confirma |
| CP-102, CP-103, CP-107 | El cliente de prueba no leía eventos JSON y otros casos pasaban sin recibir nada | Se corrigió y se exige recibir antes de comprobar el silencio |
| CP-F65 | Los listados que se recargaban solos contaban como actividad y podían alargar la sesión | Las recargas automáticas se envían como pasivas |
| CP-M29 | Una transferencia aceptaba como referencia el número de una tarjeta y lo guardaba | Se rechaza con cualquier método, también en la reversión; lo cubren CP-132, CP-144 y CP-F68 |
| CP-M29 | El nombre del producto aparecía dentro de un recuadro en el cobro y en la cancelación de venta | El estilo usaba el nombre de una clase de la nueva comanda |
| CP-M33 | A 360 px los filtros ocupaban la pantalla y las fechas se salían de su columna | Dos columnas y campos que se ajustan |
| CP-147 | Sin el bloqueo de la venta, las cancelaciones simultáneas respondían `REFERENCIA_REPETIDA` y la prueba no lo detectaba | La prueba exige `VENTA_CANCELADA` |
| CP-M51 | V13 fallaba en la base de desarrollo: «cannot ALTER TABLE fondo_cambio because it has pending trigger events» (la base vacía de Testcontainers no lo mostraba) | La migración empieza con `SET CONSTRAINTS ALL IMMEDIATE` |
| CP-M46 | A 1024 px el cobro tenía 16 px de desplazamiento horizontal: «Transferencia» no cabía en su opción | Las opciones bajan a otra fila; se repitió la auditoría |

En CP-M46 la revisión automática del foco da falsos positivos cuando la página no tiene el foco del sistema; por eso
el foco se comprobó con el teclado en CP-M47.
| CP-172 | La restricción de la causa «Otro» aceptaba una nota nula: `btrim(NULL) <> ''` da nulo y PostgreSQL lo deja pasar | Se compara la nota con `coalesce` |
| CP-01 | Con la migración V11, la prueba seguía esperando solo hasta la V10 | Espera las migraciones 1 a 12 |

## Sin cubrir

- Un cargo real en una terminal: el recorrido usa referencias inventadas.
- Dispositivos físicos: el móvil se revisó con la vista de 360 px del navegador.
- HTTPS con el proxy de producción, que aún no existe: CP-M41 usa TLS directamente en la API, y Angular se probó
  con su servidor de desarrollo en HTTP.
- Disponibilidad (98 % de 08:00 a 17:00): no hay un entorno observado.
- Lectores de pantalla: se revisaron nombres y etiquetas, no se usó un lector.

## Casos de uso del diagrama

Todos los casos de uso del diagrama tienen casos de prueba. La evidencia de la aceptación final está en
[aceptacion.md](aceptacion.md).
