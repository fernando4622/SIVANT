# Decisiones y diferencias · Fases 0 a 7

Los documentos `ROADMAP.md`, `arquitectura-sivant.md` y `modelo-datos-logico-relacional.md` se copiaron sin cambios.
Aquí se registra lo que la implementación precisó o resolvió de otra forma.

## Decisiones de implementación

| Tema | Decisión | Motivo |
|---|---|---|
| Ubicación de Compose | `compose.yaml` en la raíz y recursos en `database/`. Arquitectura proponía `infra/` | Indicación de la entrega |
| `EMPLEADO.sesion_version` | Columna adicional. Aumenta al desactivar, cambiar contraseña o cambiar rol. Cada solicitud la compara con la de la sesión | Invalida sesiones aunque haya varias o se reinicie el servidor, sin depender de un registro en memoria |
| Último administrador | Se rechaza desactivarlo o cambiarle el rol (409 `ULTIMO_ADMINISTRADOR`) | Los RF no lo contemplan. Evita que nadie pueda gestionar empleados |
| Longitud de contraseña | De 8 a 72 caracteres y hasta 72 bytes. Administrador inicial: 12 como mínimo | Los RF no fijan política. BCrypt admite 72 bytes |
| Búsqueda `q` en empleados | Se agrega a los filtros `nombre,correo,rol,estado` | La interfaz de referencia tiene un solo cuadro de búsqueda |
| Reactivar empleado | No se implementa | RF14 indica que el desactivado no vuelve a acceder y ningún RF define la reactivación |
| Inactividad | Encabezado `X-Solicitud-Pasiva` para consultas automáticas. Angular consulta la sesión cada minuto con él | El sondeo no debe contar como actividad |
| Rutas no declaradas | Se rechazan (401 o 403) | Un módulo nuevo debe declarar sus roles para quedar accesible |
| Migración de mesas | Se deja para la fase 3 | El ROADMAP ubica ahí las mesas; arquitectura las menciona junto a identidad |
| Auditoría de cambios (RNF09) | Tabla `AUDITORIA`, adicional al modelo: responsable, fecha y hora, acción, entidad, registro y detalle JSON. Se guarda en la misma transacción que el cambio y un trigger impide modificarla o borrarla. De la contraseña solo consta que cambió. Los inicios de sesión quedan en el registro de la aplicación | RNF09 pide auditar todos los cambios y el modelo no tenía dónde guardarlos |
| «Recordarme» en el acceso | Guarda solo el correo en el navegador. No alarga la sesión | RNF08 limita la sesión a 45 minutos y los dispositivos son compartidos |
| «¿Olvidaste tu contraseña?» | Indica pedir una nueva al administrador | Ningún RF define recuperación; RF13 permite al administrador cambiarla |
| `GET /insumos`, `GET /inventario/movimientos`, `GET /inventario/existencias/:id`, `GET /inventario/ajustes/:id` | Se agregan al contrato | Los piden la selección de insumos y las interfaces de referencia |
| Historial de inventario | Incluye los ajustes rechazados como `AJUSTE_RECHAZADO`, sin saldo posterior | RF15 pide conservar las decisiones en el historial |
| Filtro por días | Incluye completo el día `hasta` | Es lo que espera quien elige una fecha final |
| Solicitudes simultáneas | Puede haber varias pendientes de la misma existencia. Autorizar una impide autorizar las demás | Arquitectura valida la versión de la existencia |
| Insumos | Se cargan con `database/cargas/insumos.sql` e inician en 0 | Su pantalla se definirá después |
| Receta | No se edita desde la aplicación | El contrato de RF09 solo cambia nombre, descripción y precio |
| **Nivel mínimo** | Lo cambia el almacenista desde el detalle de la existencia. No cambia el saldo ni la versión, así que no invalida ajustes pendientes | Decisión del responsable del proyecto, 26 de noviembre de 2026. Sin poder ajustarlo, la alerta de nivel bajo pierde utilidad |
| **Causa de la salida** | Obligatoria en toda salida manual: merma, consumo del personal, devolución a proveedor u otra, esta con descripción. Las salidas anteriores quedaron como «Otro» | Decisión del responsable del proyecto, 26 de noviembre de 2026. Distingue una salida conocida de un ajuste, que corrige un faltante sin causa y requiere autorización |
| Nombre de producto | Puede repetirse | Ningún RF ni el modelo exigen unicidad |
| **Precio de productos y comandas** | El precio de venta del producto y el precio unitario aplicado al detalle de comanda deben ser mayores que cero. El alta y la edición admiten hasta dos decimales. V10 sustituye la regla de precios no negativos de los documentos de referencia | Decisión del responsable del proyecto, 25 de noviembre de 2026. Una cuenta con comandas vigentes tiene un importe positivo para cobrar |
| **Imagen de artículo** | Cada producto o insumo puede tener una imagen opcional que sube el administrador (JPG, PNG o WebP, hasta 2 MB). El archivo se guarda en una carpeta local del servidor y la base guarda solo su ruta en `ARTICULO.imagen`; V15 migra las rutas creadas por V14. Para el despliegue se cambia la carpeta por un volumen o almacén externo | Decisión del responsable del proyecto, 26 de noviembre de 2026. Ayuda a reconocer productos e insumos en catálogo e inventario |
| **Cierre sin cobro** | Lo hace el mesero responsable si no hay comandas vigentes. Libera la mesa y queda en auditoría | Decisión del responsable del proyecto, 25 de noviembre de 2026 |
| **Mesero con cuentas abiertas** | Desactivarlo o cambiar su rol responde 409 `CUENTAS_ABIERTAS` | Decisión del responsable del proyecto, 25 de noviembre de 2026 |
| `POST /cuentas/:id/cierre` | Se agrega al contrato | Lo requiere el cierre sin cobro |
| Edición de comanda | Devuelve la cuenta completa | Arquitectura pide la comanda y el total actualizado |
| Cantidad máxima | 999 por producto en una comanda | Evita errores de captura; los RF no fijan límite |
| `COMANDA_ACTUALIZADA` | Evento al mesero cuando cocina inicia o el cajero cancela. No crea aviso | Mantiene su pantalla al día sin llenar la bandeja |
| Conexión WebSocket | No cuenta como actividad. Se revalida en cada evento: deja de recibir al expirar, desactivar o cambiar credenciales | RNF08 y arquitectura |
| Aviso emergente | `COMANDA_LISTA` al mesero y `ALERTA_INVENTARIO` al almacenista; los demás quedan en la bandeja y en cocina | RF21 y RF24 piden avisar a quien debe actuar |
| Mesas | Se cargan con `database/cargas/mesas.sql` | Su pantalla se definirá después |
| Faltante de la alerta | Demanda del servicio menos la existencia previa, sin contar una previa negativa | Arquitectura pide guardar el faltante sin definir ese caso |
| Servir con la cuenta cerrada | No se exige cuenta abierta | Arquitectura, sección 5. Con las reglas actuales no ocurre: la cuenta no se cierra con comandas por servir |
| Respuesta al servir | Indica qué se descontó y si faltó, sin saldos | El mesero no consulta inventario |
| Confirmar al servir | La pantalla pide confirmación | Descuenta inventario y no puede deshacerse |
| `EXISTENCIA_ACTUALIZADA` | Evento al almacenista en cada cambio de saldo, también manual o por ajuste | Arquitectura prevé «cambios que requieren actualizar la consulta» |
| Saldo fuera de rango | 409 `EXISTENCIA_FUERA_DE_RANGO` si superaría 12 enteros | Protege el límite de la columna |
| **Cargo aprobado y la cuenta cambió** | Se rechaza (409 `VERSION_OBSOLETA` o `TOTAL_CAMBIADO`) y no se registra nada. La pantalla indica revertir el cargo con su referencia e importe hasta que el mesero lo confirma | Decisión del responsable del proyecto, 25 de noviembre de 2026 |
| **Referencia repetida** | No se admite la misma referencia con el mismo método el mismo día (zona del negocio) | Decisión del responsable del proyecto, 25 de noviembre de 2026. Los códigos de autorización se repiten con el tiempo |
| Referencia de la reversión | La misma regla por día | El ROADMAP pide un procedimiento verificable para las devoluciones |
| Fallo de red al cobrar | La pantalla conserva clave, método y referencia en la pestaña: al volver, reenvía sin registrar dos veces | Arquitectura pide reintentar con la misma referencia y clave |
| Número de tarjeta | Se rechaza una referencia de 13 a 19 dígitos que cumpla Luhn, con cualquier método | Arquitectura excluye el número de tarjeta de formularios y registros. Puede rechazar una referencia válida con esa forma |
| Formato de referencia | Hasta 40 letras, números o guiones; se guarda en mayúsculas | Evita duplicados que solo difieren en mayúsculas |
| Momento del cobro | Solo con todas las comandas no canceladas servidas; cierra la cuenta y libera la mesa | Arquitectura, sección 9. Por eso al cancelar una venta no quedan comandas sin servir |
| Fechas del cobro | Venta, pago y cierre de cuenta llevan la misma fecha | Una sola operación |
| Importe devuelto | Siempre el total: lo fija el servidor | Arquitectura pide devolución completa |
| Ventana de 24 meses | Desde el inicio de ese día en la zona del negocio. Aplica también al detalle y a la cancelación (404 fuera) | RF06. Las ventas anteriores se conservan |
| `GET /ventas/opciones` | Se agrega: meseros y mesas con ventas en la ventana | Los filtros piden mesero y mesa, y el cajero no consulta empleados |
| `cobro` y `COBRAR` en la cuenta | La cuenta indica si puede cobrarse y, cerrada, con qué método | El mesero ve el resultado aunque se pierda la respuesta |
| Rango del total de venta | 409 `TOTAL_INVALIDO` si el total calculado queda fuera del rango admitido. El límite superior es `999999999999.99` | La venta exige un total positivo que quepa en `numeric(14,2)` |
| **Cambio al recibir el efectivo** | Caja ya no presta un fondo antes del cobro. Al cobrar en efectivo, el mesero lleva el dinero a caja; el cajero registra lo recibido de cada pago (al menos su importe) y le da el cambio. `FONDO_CAMBIO` guarda lo recibido y el cambio, ligado al pago y a la entrega; `ENTREGA_EFECTIVO.monto_fondo` es el cambio total. V13 conserva los fondos ya regresados a caja como cambio y borra los pendientes, aplicados sin entregar y anulados. Se actualizaron el modelo lógico y la arquitectura; los RF no cambian | Decisión del responsable del proyecto, 26 de noviembre de 2026. Sustituye la entrega previa del fondo, su anulación y su liquidación |
| Pagos de ventas canceladas | Siguen pendientes de entregar por su importe original | Arquitectura: la devolución sale de caja |
| Recepción en la pantalla | Propone el cobro más reciente del mesero con lo recibido igual al importe y calcula el cambio; no envía si lo recibido no cubre el pago. La API lo vuelve a validar (`MONTO_INSUFICIENTE`) | Evita registrar un cambio por error de captura |
| Pruebas de rendimiento | Se ejecutan aparte (etiqueta `rendimiento`), con 50 000 ventas y 100 000 movimientos | Tardan minutos y miden el equipo, no la lógica |
| Modelo físico | Se documenta aparte en `modelo-fisico.md`, generado desde la base | El modelo lógico recibido se conserva sin cambios |

## Diferencias con las interfaces de referencia

Las imágenes definen el aspecto y los documentos definen las reglas.

| Referencia | Implementación o regla vigente |
|---|---|
| Menú «Punto de venta, Pedidos, Cobros, Inventario» igual para todos | El menú depende del rol y muestra lo disponible |
| Bote de basura en empleados | Desactiva con confirmación; no elimina |
| Justificación opcional al cancelar pedido | Obligatoria (fase 3) |
| Agregar productos al modificar un pedido | Solo cantidades y eliminación; lo nuevo va en otra comanda (fase 3) |
| Subtotal e IVA 16 % | El total es la suma de cantidad por precio (fases 3 y 5) |
| Categorías e imágenes de producto | No existen en el modelo (fase 2) |
| Interruptor de estado del producto | Solo desactivar (fase 2) |
| Producto con existencia e insumos a la vez | Preparado tiene receta; reventa tiene existencia (fase 2) |
| Estado «Completado» en movimientos | El movimiento no tiene estado (fase 2) |
| Selector de mesa en inventario | No aplica (fase 2) |

## Diferencias con el diagrama de casos de uso

| Diagrama | Regla vigente |
|---|---|
| «Pedido» | Comanda dentro de una cuenta |
| «Autorizar pago con tarjeta» por el cajero (CU-05) | Lo registra el mesero responsable (RF05 vigente) |
| «Registrar devolución» parcial con reposición de inventario (CU-15) | Cancelar venta con devolución completa y sin reponer inventario (RF22) |
| «Registrar entrega de efectivo» asociado a mesero y cajero | La registra el cajero (arquitectura, sección 3) |
| «Entregar fondo de cambio» extiende «Cobrar pedido» | El cajero da el cambio al recibir el efectivo del cobro, en la misma recepción |
| Iniciar y cerrar sesión no aparecen | Se implementan como soporte de todos los actores |

## Pendiente

- `AGENTS.md` no está en el repositorio ni en los documentos recibidos, así que no se pudieron revisar sus reglas.
- «Efectivo por entregar» no está paginado; con miles de pagos pendientes de un mesero tarda más de medio segundo.
- Proxy de producción con HTTPS y medición de disponibilidad en un entorno observado.
