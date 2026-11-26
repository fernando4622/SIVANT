package mx.sivant.inventario.aplicacion;

import java.math.BigDecimal;
import java.util.Optional;

/** Interfaz que inventario ofrece a catálogo. */
public interface Existencias {

    record Saldo(Long id, BigDecimal cantidadActual, BigDecimal nivelMinimo) {
    }

    /** Crea la existencia en cero dentro de la transacción del alta. */
    Long crearParaReventa(Long idProducto, BigDecimal nivelMinimo);

    Long crearParaInsumo(Long idInsumo, BigDecimal nivelMinimo);

    Optional<Saldo> deProducto(Long idProducto);
}
