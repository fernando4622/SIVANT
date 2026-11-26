package mx.sivant.catalogo.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.PrimaryKeyJoinColumn;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "producto")
@PrimaryKeyJoinColumn(name = "id_producto")
public abstract class Producto extends Articulo {

    @Column(nullable = false, length = 500)
    private String descripcion;

    @Column(name = "precio_venta", nullable = false, precision = 14, scale = 2)
    private BigDecimal precioVenta;

    /** Ruta relativa en el almacén de imágenes; nula si el producto no tiene imagen. */
    @Column(length = 120)
    private String imagen;

    protected Producto() {
    }

    protected Producto(String nombre, String descripcion, BigDecimal precioVenta, UnidadMedida unidadMedida) {
        super(nombre, unidadMedida);
        this.descripcion = descripcion.strip();
        this.precioVenta = precioVenta;
    }

    public abstract TipoProducto getTipo();

    public void cambiarDescripcion(String nueva) {
        descripcion = nueva.strip();
    }

    public void cambiarPrecio(BigDecimal nuevo) {
        precioVenta = nuevo;
    }

    public void cambiarImagen(String ruta) {
        imagen = ruta;
    }

    public String getImagen() {
        return imagen;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public BigDecimal getPrecioVenta() {
        return precioVenta;
    }
}
