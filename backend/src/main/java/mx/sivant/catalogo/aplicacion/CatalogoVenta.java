package mx.sivant.catalogo.aplicacion;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;

/** Interfaz que catálogo ofrece a servicio. */
public interface CatalogoVenta {

    /**
     * Precio vigente de los productos activos. Los mantiene sin cambios hasta terminar la transacción. Un producto
     * inactivo o inexistente no aparece en el resultado.
     */
    Map<Long, BigDecimal> preciosVigentes(Collection<Long> productos);
}
