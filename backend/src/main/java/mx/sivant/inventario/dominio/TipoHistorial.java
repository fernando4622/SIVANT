package mx.sivant.inventario.dominio;

/** Movimientos del modelo más las solicitudes rechazadas, que forman parte del historial sin mover existencias. */
public enum TipoHistorial {
    ENTRADA_MANUAL, SALIDA_MANUAL, CONSUMO_VENTA, AJUSTE, AJUSTE_RECHAZADO
}
