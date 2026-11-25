package mx.sivant.identidad.aplicacion;

import java.io.Serializable;
import java.security.Principal;

/** Actor autenticado. Excluye correo, contraseña y hash. */
public record EmpleadoPrincipal(Long id, String nombre, String rol, int sesionVersion)
        implements Principal, Serializable {

    @Override
    public String getName() {
        return id.toString();
    }
}
