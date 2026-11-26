package mx.sivant.empleados.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.Locale;

@Entity
@Table(name = "empleado")
public class Empleado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_empleado")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol", nullable = false)
    private Rol rol;

    @Column(name = "nombre_completo", nullable = false, length = 150)
    private String nombreCompleto;

    @Column(nullable = false, length = 254)
    private String correo;

    @Column(name = "contrasena_hash", nullable = false, length = 100)
    private String contrasenaHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private EstadoEmpleado estado = EstadoEmpleado.ACTIVO;

    @Column(name = "sesion_version", nullable = false)
    private int sesionVersion;

    protected Empleado() {
    }

    public Empleado(String nombreCompleto, String correo, String contrasenaHash, Rol rol) {
        this.nombreCompleto = nombreCompleto.strip();
        this.correo = normalizarCorreo(correo);
        this.contrasenaHash = contrasenaHash;
        this.rol = rol;
    }

    public static String normalizarCorreo(String correo) {
        return correo.strip().toLowerCase(Locale.ROOT);
    }

    public void cambiarNombre(String nuevo) {
        nombreCompleto = nuevo.strip();
    }

    public void cambiarCorreo(String nuevo) {
        correo = normalizarCorreo(nuevo);
    }

    public void cambiarContrasena(String nuevoHash) {
        contrasenaHash = nuevoHash;
        invalidarSesiones();
    }

    public void cambiarRol(Rol nuevo) {
        rol = nuevo;
        invalidarSesiones();
    }

    public void desactivar() {
        estado = EstadoEmpleado.INACTIVO;
        invalidarSesiones();
    }

    public void reactivar() {
        estado = EstadoEmpleado.ACTIVO;
        invalidarSesiones();
    }

    private void invalidarSesiones() {
        sesionVersion++;
    }

    public boolean estaActivo() {
        return estado == EstadoEmpleado.ACTIVO;
    }

    public boolean esAdministradorActivo() {
        return estaActivo() && Rol.ADMINISTRADOR.equals(rol.getNombre());
    }

    public Long getId() {
        return id;
    }

    public Rol getRol() {
        return rol;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public String getCorreo() {
        return correo;
    }

    public String getContrasenaHash() {
        return contrasenaHash;
    }

    public EstadoEmpleado getEstado() {
        return estado;
    }

    public int getSesionVersion() {
        return sesionVersion;
    }
}
