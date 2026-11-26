package mx.sivant.catalogo.aplicacion;

import java.math.BigDecimal;
import java.util.List;
import mx.sivant.catalogo.dominio.Producto;
import mx.sivant.catalogo.dominio.TipoProducto;
import mx.sivant.catalogo.dominio.UnidadMedida;
import mx.sivant.inventario.aplicacion.Existencias.Saldo;

/** `receta` y `existencia` son nulas en listados y en la consulta del mesero. `imagen` es la ruta en el almacén. */
public record DatosProducto(Long id, String nombre, String descripcion, BigDecimal precioVenta, TipoProducto tipo,
        UnidadMedida unidadMedida, boolean activo, String imagen, List<Componente> receta, Saldo existencia) {

    public record Componente(Long insumoId, String nombre, UnidadMedida unidadMedida, BigDecimal cantidadRequerida) {
    }

    static DatosProducto resumen(Producto p) {
        return detalle(p, null, null);
    }

    static DatosProducto detalle(Producto p, List<Componente> receta, Saldo existencia) {
        return new DatosProducto(p.getId(), p.getNombre(), p.getDescripcion(), p.getPrecioVenta(), p.getTipo(),
                p.getUnidadMedida(), p.isActivo(), p.getImagen(), receta, existencia);
    }
}
