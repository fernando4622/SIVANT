# SIVANT

Sistema de ventas e inventario para Jiva House. Angular, Spring Boot (Java 21) y PostgreSQL 18.

**Estado:** fases 0 a 7 del [ROADMAP](docs/ROADMAP.md). Incluye inicio y cierre de sesión, permisos para los cinco
roles, empleados (RF11 a RF14), productos y recetas (RF07 a RF10), inventario (RF15 a RF18),
mesas, cuentas y comandas (RF01 a RF03), cocina con avisos en tiempo real (RF19 a RF21),
entrega con descuento de inventario (RF23 y RF24), cobro, historial y cancelación de ventas (RF04 a RF06 y RF22), y caja que recibe el efectivo y da el cambio
(RF25 y RF26). La [aceptación final](docs/aceptacion.md) reúne la evidencia de cada criterio.

## Estructura

| Carpeta | Contenido |
|---|---|
| `backend/` | API Spring Boot. Migraciones en `src/main/resources/db/migration/` |
| `frontend/` | Aplicación Angular |
| `database/` | Configuración local de PostgreSQL |
| `docs/` | Roadmap, arquitectura, modelo lógico y [físico](docs/modelo-fisico.md), [contrato OpenAPI](docs/openapi.yaml), [casos de prueba](docs/casos-de-prueba.md), [decisiones](docs/decisiones.md), [aceptación](docs/aceptacion.md) y [guion de demostración](docs/demostracion.md) |
| `scripts/` | Recorrido de demostración por la API |
| `compose.yaml` | PostgreSQL 18 para desarrollo |

## Requisitos

- JDK 21. Maven no hace falta: se usa el Wrapper incluido.
- Node 20.19, 22.12 o posterior de esas líneas, o 24 en adelante.
- Docker con Compose. Docker también es necesario para las pruebas del backend. Si prefieres tu PostgreSQL 18,
  consulta [database/README.md](database/README.md).

`java -version` debe indicar 21. Si tienes varios JDK, define `JAVA_HOME` en la terminal antes de usar Maven:

```powershell
$env:JAVA_HOME = "C:\ruta\a\jdk-21"
```

## Arranque

Los comandos se ejecutan en PowerShell desde la raíz del repositorio.

1. Crear la configuración local. Cambia los valores de `.env`; ese archivo no se versiona.

```powershell
Copy-Item .env.example .env
```

2. Iniciar PostgreSQL:

```powershell
docker compose up -d --wait
```

3. Iniciar la API con el perfil de desarrollo, en una terminal. Flyway crea el esquema en el primer arranque:

```powershell
cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
```

4. Iniciar Angular, en otra terminal:

```powershell
cd frontend; npm ci; npm start
```

Abrir <http://localhost:4200>. El proxy de Angular envía `/api` y `/ws` a `http://localhost:8080`.

## Variables de entorno

Se leen de `.env`. Una variable definida en el sistema tiene prioridad.

| Variable | Uso |
|---|---|
| `POSTGRES_SUPERUSER`, `POSTGRES_SUPERUSER_PASSWORD` | Superusuario del contenedor. La aplicación no lo usa |
| `SIVANT_DB_HOST`, `SIVANT_DB_PORT` | Servidor y puerto. Por omisión `localhost` y 5433 |
| `SIVANT_DB_NAME`, `SIVANT_DB_USER`, `SIVANT_DB_PASSWORD` | Base y usuario de la aplicación |
| `SIVANT_HTTP_PORT` | Puerto de la API. Si cambia, ajustar `frontend/proxy.conf.json` |
| `SIVANT_DEV_CONTRASENA` | Contraseña de las cuentas ficticias. Solo perfil `dev` |
| `SIVANT_ADMIN_NOMBRE`, `SIVANT_ADMIN_CORREO`, `SIVANT_ADMIN_CONTRASENA` | Primer administrador fuera de `dev` |

Los scripts de `database/init` solo se ejecutan con un volumen nuevo: cambiar después las credenciales de la base en
`.env` no modifica las ya creadas.

### Perfiles

| Perfil | Comportamiento |
|---|---|
| `dev` | Cookie sin HTTPS y cuentas ficticias |
| Sin perfil | Cookie `Secure` (requiere HTTPS) y sin cuentas ficticias |

**Primer administrador.** Fuera de `dev`, si no existe ningún administrador, la aplicación lo crea al arrancar con las
variables `SIVANT_ADMIN_*` (contraseña de 12 caracteres o más). Retira esas variables después del primer arranque.

## Probar con cuentas ficticias

Todas usan la contraseña de `SIVANT_DEV_CONTRASENA` de tu `.env`.

| Correo | Rol |
|---|---|
| `admin@sivant.test` | Administrador |
| `mesero@sivant.test` | Mesero |
| `cajero@sivant.test` | Cajero |
| `cocinero@sivant.test` | Cocinero |
| `almacenista@sivant.test` | Almacenista |
| `inactivo@sivant.test` | Mesero inactivo: el acceso se rechaza |

Recorrido sugerido:

1. Entra como administrador y abre **Empleados**. Busca y filtra por rol y estado.
2. Registra un empleado con un correo ya usado, en mayúsculas: se muestra el error y se conservan los datos. Corrige
   el correo y guarda.
3. En una ventana privada, entra con el empleado nuevo. Cada rol ve sus funciones y solo el administrador ve
   **Empleados**.
4. Como administrador, desactiva a ese empleado. Su ventana vuelve al inicio de sesión en un minuto o en su siguiente
   operación, y ya no puede entrar.
5. Entra como mesero y abre `http://localhost:4200/empleados`: regresa a su inicio. La API también lo rechaza con 403.
6. Como almacenista, abre **Inventario**, entra a un artículo, registra una salida mayor que la existencia (se rechaza),
   una entrada y solicita un ajuste.
7. Como administrador, abre **Inventario > Ajustes** y autoriza o rechaza la solicitud. El resultado aparece en
   **Movimientos**.
8. Como administrador, registra un producto preparado con receta y otro de reventa, cambia un precio y desactiva uno.
   Como mesero, comprueba que el catálogo solo muestra los activos.
9. Como mesero, abre una mesa, envía una comanda y deja esa ventana en otra pantalla.
10. En una ventana privada, entra como cocinero: inicia la preparación y marca lista. El mesero recibe el aviso al
    momento.
11. Como mesero, modifica una comanda en preparación: cocina recibe el aviso. Como cajero, entra a la cuenta desde
    **Mesas** y cancela una comanda con justificación.
12. Como mesero, marca servida una comanda lista. Si pide más de lo registrado en inventario, se sirve igual y el
    almacenista recibe la alerta.
13. Como mesero, con todas las comandas servidas, pulsa **Cobrar**. Con tarjeta, escribe una referencia inventada
    (nunca el número de la tarjeta). La cuenta se cierra y la mesa queda libre.
14. Como cajero o administrador, abre **Ventas**, filtra y entra al detalle. Como administrador, cancela la venta con
    el motivo, la referencia de la reversión si no fue en efectivo y la confirmación de la devolución.
15. Como mesero, cobra otra cuenta en efectivo y lleva el dinero a caja.
16. Como cajero, abre **Caja**, elige al mesero y su pago, escribe lo que entregó el cliente y registra la recepción.
    La pantalla indica el cambio que se le da al mesero.

El perfil `dev` también carga mesas, insumos, productos y existencias ficticias.

## Cargar insumos y mesas

No se registran desde la aplicación. Edita `database/cargas/insumos.sql` y `database/cargas/mesas.sql`, y ejecútalos
después del primer arranque de la API. Con Docker:

```powershell
docker compose cp database/cargas db:/tmp/
```

```powershell
docker compose exec db sh -c 'PGPASSWORD=$SIVANT_DB_PASSWORD psql -h 127.0.0.1 -U $SIVANT_DB_USER -d $SIVANT_DB_NAME -f /tmp/cargas/insumos.sql -f /tmp/cargas/mesas.sql'
```

Los insumos inician con existencia 0.

## Pruebas

```powershell
cd backend; .\mvnw.cmd test
```

```powershell
cd frontend; npm test -- --watch=false
```

Las pruebas del backend crean su propia base PostgreSQL 18 con Testcontainers y no tocan la de desarrollo.

Las de rendimiento se ejecutan aparte y tardan unos minutos:

```powershell
cd backend; .\mvnw.cmd test "-Dexcluir=ninguna" "-Dgroups=rendimiento"
```

Con la API y Angular en marcha, el recorrido de demostración se comprueba por la API:

```powershell
python scripts/recorrido-demostracion.py
```

## Repetir desde una base vacía

Borra todos los datos del contenedor local:

```powershell
docker compose down -v
```

Después, repite los pasos 2 y 3 del arranque.

## Problemas conocidos

- npm 10.9.2 puede fallar con `Cannot read properties of null (reading 'edgesOut')` al **agregar** dependencias. Usa
  `npx npm@11 install`. `npm ci` con el archivo de bloqueo funciona con npm 10.
- En una carpeta sincronizada con OneDrive, `node_modules` puede retrasar la instalación. Conviene excluirla o clonar
  el repositorio fuera de OneDrive.
