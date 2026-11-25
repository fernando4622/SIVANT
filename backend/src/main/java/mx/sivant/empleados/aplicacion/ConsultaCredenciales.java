package mx.sivant.empleados.aplicacion;

import java.util.Optional;

/** Interfaz que empleados ofrece a identidad. */
public interface ConsultaCredenciales {

    Optional<CredencialEmpleado> porCorreo(String correo);

    Optional<CredencialEmpleado> porId(Long id);
}
