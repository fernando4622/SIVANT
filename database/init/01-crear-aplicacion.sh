#!/bin/sh
# Se ejecuta una sola vez, al inicializar un volumen vacío.
# Crea el rol de aplicación (sin privilegios de superusuario) y su base.
# El esquema `sivant` y sus tablas los crea Flyway desde el backend.
set -e

psql -v ON_ERROR_STOP=1 \
     -v app_user="$SIVANT_DB_USER" \
     -v app_password="$SIVANT_DB_PASSWORD" \
     -v app_db="$SIVANT_DB_NAME" \
     --username "$POSTGRES_USER" --dbname postgres <<'SQL'
CREATE ROLE :"app_user" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'app_password';
CREATE DATABASE :"app_db" OWNER :"app_user" ENCODING 'UTF8' TEMPLATE template0;
REVOKE ALL ON DATABASE :"app_db" FROM PUBLIC;
SQL
