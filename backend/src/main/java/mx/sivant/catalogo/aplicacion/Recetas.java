package mx.sivant.catalogo.aplicacion;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Interfaz que catálogo ofrece a inventario. */
public interface Recetas {

    record Componente(Long insumoId, BigDecimal cantidad) {
    }

    /** Insumos por unidad de cada preparado. Los productos de reventa no aparecen. */
    Map<Long, List<Componente>> porProducto(Collection<Long> productos);
}
