-- Carga autorizada de insumos. La aplicación no ofrece su alta en esta etapa.
-- Editar la lista y ejecutar con el usuario de la aplicación:
--   psql -h localhost -p 5433 -U sivant_app -d sivant -f database/cargas/insumos.sql
-- Cada insumo inicia con existencia 0; su cantidad se registra después como entrada de inventario.
-- Es repetible: omite los insumos cuyo nombre ya existe.
\set ON_ERROR_STOP on
BEGIN;

DO
$$
DECLARE
    fila      record;
    articulo  bigint;
    existencia bigint;
BEGIN
    FOR fila IN
        SELECT * FROM (VALUES
            -- nombre, unidad (PIEZA, GRAMO, KILOGRAMO, MILILITRO, LITRO), nivel mínimo
            ('Harina de trigo', 'GRAMO', 2000::numeric),
            ('Chocolate',       'GRAMO', 500::numeric)
        ) AS v(nombre, unidad, minimo)
    LOOP
        CONTINUE WHEN EXISTS (
            SELECT 1 FROM sivant.insumo i JOIN sivant.articulo a ON a.id_articulo = i.id_insumo
            WHERE lower(a.nombre) = lower(fila.nombre));
        INSERT INTO sivant.articulo (nombre, unidad_medida) VALUES (fila.nombre, fila.unidad)
            RETURNING id_articulo INTO articulo;
        INSERT INTO sivant.insumo (id_insumo) VALUES (articulo);
        INSERT INTO sivant.existencia_inventario (nivel_minimo, fecha_actualizacion) VALUES (fila.minimo, now())
            RETURNING id_existencia INTO existencia;
        INSERT INTO sivant.existencia_insumo (id_existencia, id_insumo) VALUES (existencia, articulo);
    END LOOP;
END;
$$;

COMMIT;
