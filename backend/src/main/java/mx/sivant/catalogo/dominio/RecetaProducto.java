package mx.sivant.catalogo.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "receta_producto")
public class RecetaProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_receta_detalle")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_producto_preparado", nullable = false)
    private ProductoPreparado producto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_insumo", nullable = false)
    private Insumo insumo;

    @Column(name = "cantidad_requerida", nullable = false, precision = 18, scale = 6)
    private BigDecimal cantidadRequerida;

    protected RecetaProducto() {
    }

    RecetaProducto(ProductoPreparado producto, Insumo insumo, BigDecimal cantidadRequerida) {
        this.producto = producto;
        this.insumo = insumo;
        this.cantidadRequerida = cantidadRequerida;
    }

    public Insumo getInsumo() {
        return insumo;
    }

    public BigDecimal getCantidadRequerida() {
        return cantidadRequerida;
    }
}
