-- Alternativa a Docker: crea el rol y la base en un PostgreSQL 18 ya instalado.
-- Ejecutar con un superusuario y pasar la contraseña como variable (ver database/README.md):
--   psql -U postgres -p 5432 -v app_password="..." -f database/local/crear-base.sql
\set ON_ERROR_STOP on
\if :{?app_user}
\else
  \set app_user sivant_app
\endif
\if :{?app_db}
\else
  \set app_db sivant
\endif
\if :{?app_password}
\else
  \echo 'Falta -v app_password=...'
  \quit
\endif

CREATE ROLE :"app_user" LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE PASSWORD :'app_password';
CREATE DATABASE :"app_db" OWNER :"app_user" ENCODING 'UTF8' TEMPLATE template0;
REVOKE ALL ON DATABASE :"app_db" FROM PUBLIC;
