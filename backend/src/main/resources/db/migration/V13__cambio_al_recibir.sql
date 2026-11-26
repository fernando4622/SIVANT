-- El fondo de cambio deja de prestarse antes del cobro. El mesero lleva el efectivo a caja al cobrar; el cajero recibe
-- el pago y, si el cliente pagó con más, le da el cambio. FONDO_CAMBIO guarda lo recibido y el cambio de ese pago, en la
-- entrega en la que caja lo recibió. ENTREGA_EFECTIVO.monto_fondo es el total de cambio entregado.

-- Solo se conservan los fondos que ya regresaron a caja con su pago; los pendientes, aplicados sin entregar y anulados
-- eran del flujo anterior.
-- Las validaciones diferidas se comprueban al momento: con eventos pendientes no se puede alterar la tabla.
SET CONSTRAINTS ALL IMMEDIATE;

DELETE FROM sivant.fondo_cambio WHERE estado <> 'LIQUIDADO';

DROP TRIGGER tg_fondo_pago ON sivant.fondo_cambio;
DROP FUNCTION sivant.tg_validar_fondo();
DROP INDEX sivant.uq_fondo_vigente;
DROP INDEX sivant.ix_fondo_estado;

ALTER TABLE sivant.fondo_cambio
    ADD COLUMN monto_recibido numeric(14, 2);

UPDATE sivant.fondo_cambio f SET monto_recibido = p.monto + f.monto FROM sivant.pago p WHERE p.id_pago = f.id_pago;

ALTER TABLE sivant.fondo_cambio
    DROP CONSTRAINT ck_fondo_estado,
    DROP CONSTRAINT ck_fondo_pago,
    DROP CONSTRAINT ck_fondo_entrega,
    DROP CONSTRAINT ck_fondo_anulacion,
    DROP CONSTRAINT ck_fondo_justificacion,
    DROP COLUMN id_cuenta,
    DROP COLUMN id_mesero,
    DROP COLUMN estado,
    DROP COLUMN id_cajero_anulacion,
    DROP COLUMN fecha_hora_anulacion,
    DROP COLUMN justificacion_anulacion,
    ALTER COLUMN id_pago SET NOT NULL,
    ALTER COLUMN id_entrega SET NOT NULL,
    ALTER COLUMN monto_recibido SET NOT NULL;

COMMENT ON COLUMN sivant.fondo_cambio.monto IS 'Cambio que caja entregó por el pago';
COMMENT ON COLUMN sivant.fondo_cambio.monto_recibido IS 'Efectivo que entregó el cliente';

-- El cambio corresponde a un pago en efectivo: lo recibido es el pago más el cambio.
CREATE FUNCTION sivant.tg_validar_cambio() RETURNS trigger
    LANGUAGE plpgsql AS
$$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM sivant.pago p
        WHERE p.id_pago = NEW.id_pago AND p.metodo = 'EFECTIVO' AND NEW.monto_recibido = p.monto + NEW.monto) THEN
        RAISE EXCEPTION 'El cambio % no corresponde a lo recibido por un pago en efectivo', NEW.id_fondo_cambio
            USING ERRCODE = 'integrity_constraint_violation';
    END IF;
    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER tg_cambio_pago AFTER INSERT OR UPDATE ON sivant.fondo_cambio
    DEFERRABLE INITIALLY DEFERRED FOR EACH ROW EXECUTE FUNCTION sivant.tg_validar_cambio();
