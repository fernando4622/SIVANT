-- Consumo automático al servir (RF24). Cada movimiento se relaciona con el detalle que lo originó.

ALTER TABLE sivant.movimiento_inventario
    ADD COLUMN id_detalle_comanda bigint REFERENCES sivant.detalle_comanda (id_detalle) ON DELETE RESTRICT,
    DROP CONSTRAINT ck_movimiento_tipo,
    DROP CONSTRAINT ck_movimiento_origen,
    DROP CONSTRAINT ck_movimiento_saldo;

ALTER TABLE sivant.movimiento_inventario
    ADD CONSTRAINT ck_movimiento_tipo CHECK (
        tipo IN ('ENTRADA_MANUAL', 'SALIDA_MANUAL', 'CONSUMO_VENTA', 'AJUSTE')),
    ADD CONSTRAINT ck_movimiento_origen CHECK (
        (tipo = 'AJUSTE') = (id_solicitud_ajuste IS NOT NULL) AND
        (tipo = 'CONSUMO_VENTA') = (id_detalle_comanda IS NOT NULL)),
    -- El consumo automático puede dejar la existencia negativa; la salida manual no.
    ADD CONSTRAINT ck_movimiento_saldo CHECK (CASE tipo
        WHEN 'ENTRADA_MANUAL' THEN saldo_posterior - saldo_anterior = cantidad
        WHEN 'SALIDA_MANUAL' THEN saldo_anterior - saldo_posterior = cantidad AND saldo_posterior >= 0
        WHEN 'CONSUMO_VENTA' THEN saldo_anterior - saldo_posterior = cantidad
        ELSE abs(saldo_posterior - saldo_anterior) = cantidad END);

CREATE UNIQUE INDEX uq_consumo_detalle_existencia
    ON sivant.movimiento_inventario (id_detalle_comanda, id_existencia)
    WHERE tipo = 'CONSUMO_VENTA';
