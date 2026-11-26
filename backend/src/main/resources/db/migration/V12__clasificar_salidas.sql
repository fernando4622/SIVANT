-- Toda salida manual declara su causa; con «Otro», también una descripción en `motivo`.
-- Así una salida no se confunde con un ajuste, que corrige el saldo tras un conteo y requiere autorización.

ALTER TABLE sivant.movimiento_inventario ADD COLUMN causa varchar(25);

UPDATE sivant.movimiento_inventario
SET causa = 'OTRO', motivo = coalesce(nullif(btrim(motivo), ''), 'Registrada antes de exigir la causa')
WHERE tipo = 'SALIDA_MANUAL';

ALTER TABLE sivant.movimiento_inventario
    ADD CONSTRAINT ck_movimiento_causa CHECK (
        causa IN ('MERMA', 'CONSUMO_PERSONAL', 'DEVOLUCION_PROVEEDOR', 'OTRO')),
    ADD CONSTRAINT ck_movimiento_causa_salida CHECK ((tipo = 'SALIDA_MANUAL') = (causa IS NOT NULL)),
    ADD CONSTRAINT ck_movimiento_causa_otro CHECK (causa IS DISTINCT FROM 'OTRO' OR coalesce(btrim(motivo), '') <> '');
