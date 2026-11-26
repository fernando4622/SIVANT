-- Imagen opcional del producto. El archivo se guarda fuera de la base (por ahora en una carpeta local del
-- servidor); la base guarda solo su ruta relativa, con un nombre que genera el sistema.
ALTER TABLE sivant.producto
    ADD COLUMN imagen varchar(120),
    ADD CONSTRAINT ck_producto_imagen
        CHECK (imagen ~ '^productos/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(jpg|png|webp)$');

COMMENT ON COLUMN sivant.producto.imagen IS 'Ruta relativa de la imagen en el almacén de archivos';
