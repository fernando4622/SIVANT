# Decisiones y diferencias · Fases 0 a 2

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
| `CONSUMO_VENTA` y `id_detalle_comanda` | Se agregan en la fase 4 | Dependen de `DETALLE_COMANDA` |
| Insumos | Se cargan con `database/cargas/insumos.sql` e inician en 0 | Su pantalla se definirá después |
| Receta y nivel mínimo | No se editan desde la aplicación | El contrato de RF09 solo cambia nombre, descripción y precio |
| WebSocket de inventario | Se agrega con el primer aviso (fases 3 y 4) | En esta fase no hay alertas |
| Nombre de producto | Puede repetirse | Ningún RF ni el modelo exigen unicidad |

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
| «Registrar entrega de efectivo» asociado a mesero y cajero | La registra el cajero (arquitectura, sección 3) |
| «Entregar fondo de cambio» extiende «Cobrar pedido» | Lo registra el cajero antes del cobro |
| Iniciar y cerrar sesión no aparecen | Se implementan como soporte de todos los actores |

## Pendiente de las fases 0 a 2

- `AGENTS.md` no está en el repositorio ni en los documentos recibidos, así que no se pudieron revisar sus reglas.
- La regla sobre cuentas a cargo al desactivar o cambiar de rol depende de las cuentas (fase 3).
