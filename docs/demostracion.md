# Guion de demostración

Muestra el ciclo completo con los cinco roles. Las cuentas ficticias y la contraseña están en el
[README](../README.md#probar-con-cuentas-ficticias).

## Preparación

1. Arrancar PostgreSQL, la API con el perfil `dev` y Angular como indica el [README](../README.md#arranque).
2. Abrir <http://localhost:4200> en una ventana normal y otra privada para tener dos roles a la vez.
3. Para comprobar todo de una vez por la API: `python scripts/recorrido-demostracion.py`. Termina con «19 de 19 pasos
   correctos».

## Recorrido

| # | Rol | Pantalla | Qué mostrar | Resultado esperado |
|---|---|---|---|---|
| 1 | Administrador | Empleados | Filtrar por rol y estado | Solo los que coinciden |
| 2 | Administrador | Nuevo empleado | Registrar con un correo ya usado en mayúsculas; corregir | Error en el campo; se registra al corregir |
| 3 | Empleado nuevo | Inicio | Entrar en la ventana privada | Ve solo las funciones de su rol |
| 4 | Administrador | Empleados | Desactivarlo | Su ventana vuelve al inicio de sesión |
| 5 | Almacenista | Inventario | Salida mayor que la existencia; entrada; solicitar ajuste | La salida se rechaza; la entrada cambia el saldo |
| 6 | Administrador | Ajustes | Autorizar la solicitud | Aparece en Movimientos |
| 7 | Administrador | Productos | Alta de preparado y de reventa, cambio de precio, desactivación | El mesero solo ve los activos |
| 8 | Mesero | Mesas, cuenta | Abrir una mesa y enviar una comanda | La comanda aparece en Cocina al momento |
| 9 | Cocinero | Cocina | Iniciar preparación | Estado En preparación |
| 10 | Mesero | Modificar comanda | Cambiar una cantidad | Cocina recibe el aviso |
| 11 | Cocinero | Cocina | Marcar lista | El mesero recibe el aviso en cualquier pantalla |
| 12 | Cajero | Cuenta | Cancelar otra comanda con justificación | Cocina recibe el aviso; no se cobra |
| 13 | Cajero | Cuenta | Revisar la cuenta antes del cobro | Comandas, estados y total; sin fondo previo |
| 14 | Mesero | Cuenta | Marcar servida | Se descuenta el inventario; el almacenista ve el cambio |
| 15 | Mesero | Cobro | Cobrar en efectivo | La cuenta se cierra y la mesa queda libre |
| 16 | Cajero | Ventas | Filtrar y abrir el detalle | Pago, comandas y cancelaciones |
| 17 | Administrador | Ventas | Cancelar la venta con motivo y confirmación | Pago e inventario se conservan |
| 18 | Cajero | Caja | Recibir el efectivo con más de lo cobrado | Muestra el cambio a entregar y la lista queda vacía |

Para mostrar la tarjeta, cobrar otra cuenta con una referencia inventada (nunca el número de la tarjeta).
