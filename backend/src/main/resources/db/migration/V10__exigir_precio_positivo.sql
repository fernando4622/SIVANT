-- Todo producto que se vende tiene precio mayor que 0, y el precio aplicado en la comanda también.
-- Así una cuenta con comandas vigentes siempre tiene un total que cobrar.

ALTER TABLE sivant.producto
    DROP CONSTRAINT ck_producto_precio,
    ADD CONSTRAINT ck_producto_precio CHECK (precio_venta > 0);

ALTER TABLE sivant.detalle_comanda
    DROP CONSTRAINT ck_detalle_precio,
    ADD CONSTRAINT ck_detalle_precio CHECK (precio_unitario > 0);
