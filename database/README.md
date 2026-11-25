# Configuración local de PostgreSQL 18

Esta carpeta contiene solo recursos para levantar PostgreSQL en desarrollo. Las tablas las crea
Flyway desde `backend/src/main/resources/db/migration/`; aquí no se guarda ningún esquema.

| Recurso | Uso |
|---|---|
| `postgresql.conf` | Parámetros del contenedor (`compose.yaml`) |
| `init/01-crear-aplicacion.sh` | Crea el rol de aplicación y la base en un volumen nuevo |
| `local/crear-base.sql` | Lo mismo para un PostgreSQL 18 instalado, sin Docker |

## Con Docker

```powershell
Copy-Item .env.example .env
docker compose up -d --wait
```

Para repetir el arranque desde una base vacía. **Borra todos los datos locales del contenedor**:

```powershell
docker compose down -v
```

## Sin Docker

1. Crear rol y base con un superusuario. Cambia el puerto si tu servicio usa otro:

```powershell
psql -U postgres -p 5432 -v app_password="cambiar-app-local" -f database/local/crear-base.sql
```

2. En `.env`, poner en `SIVANT_DB_PORT` el puerto de tu servicio y en `SIVANT_DB_PASSWORD` la contraseña usada.

La aplicación se conecta con `SIVANT_DB_USER`, dueño de la base y sin privilegios de superusuario.
