package mx.sivant.catalogo.dominio;

import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "producto_reventa")
@PrimaryKeyJoinColumn(name = "id_producto")
public class ProductoReventa extends Producto {

    protected ProductoReventa() {
    }

    public ProductoReventa(String nombre, String descripcion, BigDecimal precioVenta, UnidadMedida unidadMedida) {
        super(nombre, descripcion, precioVenta, unidadMedida);
    }

    @Override
    public TipoProducto getTipo() {
        return TipoProducto.REVENTA;
    }
}
