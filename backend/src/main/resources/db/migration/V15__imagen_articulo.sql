-- La imagen pertenece al artículo para que productos e insumos compartan el mismo mecanismo.
ALTER TABLE sivant.articulo
    ADD COLUMN imagen varchar(120),
    ADD CONSTRAINT ck_articulo_imagen CHECK (
        imagen ~ '^(productos|insumos)/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|webp)$'
    );

UPDATE sivant.articulo a
SET imagen = p.imagen
FROM sivant.producto p
WHERE p.id_producto = a.id_articulo;

ALTER TABLE sivant.producto
    DROP CONSTRAINT ck_producto_imagen,
    DROP COLUMN imagen;

COMMENT ON COLUMN sivant.articulo.imagen IS 'Ruta relativa de la imagen en el almacén de archivos';
