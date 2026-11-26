package mx.sivant.inventario.dominio;

/** Por qué sale inventario fuera de una venta (RF17). Un faltante sin causa conocida se corrige con un ajuste. */
public enum CausaSalida {
    MERMA, CONSUMO_PERSONAL, DEVOLUCION_PROVEEDOR, OTRO
}
