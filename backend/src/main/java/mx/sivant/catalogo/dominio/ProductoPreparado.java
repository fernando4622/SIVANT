package mx.sivant.catalogo.dominio;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "producto_preparado")
@PrimaryKeyJoinColumn(name = "id_producto")
public class ProductoPreparado extends Producto {

    @OneToMany(mappedBy = "producto", cascade = CascadeType.PERSIST)
    @OrderBy("id")
    private List<RecetaProducto> receta = new ArrayList<>();

    protected ProductoPreparado() {
    }

    public ProductoPreparado(String nombre, String descripcion, BigDecimal precioVenta, UnidadMedida unidadMedida) {
        super(nombre, descripcion, precioVenta, unidadMedida);
    }

    public void agregarInsumo(Insumo insumo, BigDecimal cantidad) {
        receta.add(new RecetaProducto(this, insumo, cantidad));
    }

    public List<RecetaProducto> getReceta() {
        return receta;
    }

    @Override
    public TipoProducto getTipo() {
        return TipoProducto.PREPARADO;
    }
}
