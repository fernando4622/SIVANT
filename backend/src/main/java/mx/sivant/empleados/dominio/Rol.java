package mx.sivant.empleados.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Catálogo fijo cargado por Flyway. Su nombre es la autoridad de Spring Security. */
@Entity
@Table(name = "rol")
public class Rol {

    public static final String ADMINISTRADOR = "ADMINISTRADOR";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_rol")
    private Long id;

    @Column(nullable = false, updatable = false, length = 30)
    private String nombre;

    @Column(nullable = false, updatable = false, length = 200)
    private String descripcion;

    protected Rol() {
    }

    public Long getId() {
        return id;
    }

    public String getNombre() {
        return nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }
}
