package mx.sivant.catalogo.aplicacion;

import java.math.BigDecimal;
import java.util.List;
import mx.sivant.catalogo.dominio.TipoProducto;
import mx.sivant.catalogo.dominio.UnidadMedida;

/** `receta` corresponde a PREPARADO y `nivelMinimo` a REVENTA. */
public record AltaProducto(String nombre, String descripcion, BigDecimal precioVenta, TipoProducto tipo,
        UnidadMedida unidadMedida, List<Componente> receta, BigDecimal nivelMinimo) {

    public record Componente(Long insumoId, BigDecimal cantidadRequerida) {
    }
}
