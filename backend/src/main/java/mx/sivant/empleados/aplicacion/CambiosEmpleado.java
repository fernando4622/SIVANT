package mx.sivant.empleados.aplicacion;

/** Un valor nulo conserva el dato actual. */
public record CambiosEmpleado(String nombreCompleto, String correo, String contrasena, String rol) {

    @Override
    public String toString() {
        return "CambiosEmpleado[...]";
    }
}
