package mx.sivant.catalogo.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;

/** Un artículo es producto o insumo, nunca ambos. La base lo comprueba al confirmar. */
@Entity
@Table(name = "articulo")
@Inheritance(strategy = InheritanceType.JOINED)
public abstract class Articulo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_articulo")
    private Long id;

    @Column(nullable = false, length = 120)
    private String nombre;

    @Enumerated(EnumType.STRING)
    @Column(name = "unidad_medida", nullable = false, length = 10)
    private UnidadMedida unidadMedida;

    @Column(nullable = false)
    private boolean activo = true;

    /** Ruta relativa en el almacén de imágenes; nula si el artículo no tiene imagen. */
    @Column(length = 120)
    private String imagen;

    protected Articulo() {
    }

    protected Articulo(String nombre, UnidadMedida unidadMedida) {
        this.nombre = nombre.strip();
        this.unidadMedida = unidadMedida;
    }

    public void cambiarNombre(String nuevo) {
        nombre = nuevo.strip();
    }

    public void desactivar() {
        activo = false;
    }

    public void reactivar() {
        activo = true;
    }

    public void cambiarImagen(String ruta) {
        imagen = ruta;
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public UnidadMedida getUnidadMedida() {
        return unidadMedida;
    }

    public boolean isActivo() {
        return activo;
    }

    public String getImagen() {
        return imagen;
    }
}
