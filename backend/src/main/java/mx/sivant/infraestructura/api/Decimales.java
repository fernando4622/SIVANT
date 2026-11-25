package mx.sivant.infraestructura.api;

import java.math.BigDecimal;

/** Dinero e inventario viajan como cadenas decimales sin signo ni exponente. */
public final class Decimales {

    /** Hasta 12 enteros y 2 decimales: numeric(14,2). */
    public static final String DINERO = "^\\d{1,12}(\\.\\d{1,2})?$";
    /** Hasta 12 enteros y 6 decimales: numeric(18,6). */
    public static final String CANTIDAD = "^\\d{1,12}(\\.\\d{1,6})?$";

    private Decimales() {
    }

    public static String dinero(BigDecimal valor) {
        return valor.setScale(2).toPlainString();
    }

    public static String cantidad(BigDecimal valor) {
        return valor.signum() == 0 ? "0" : valor.stripTrailingZeros().toPlainString();
    }
}
