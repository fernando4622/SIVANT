package mx.sivant.empleados.aplicacion;

/** Vista interna para el módulo de identidad. No se serializa ni se guarda en sesión. */
public record CredencialEmpleado(Long id, String nombreCompleto, String rol, String contrasenaHash, boolean activo,
        int sesionVersion) {

    @Override
    public String toString() {
        return "CredencialEmpleado[id=" + id + "]";
    }
}
