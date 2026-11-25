package mx.sivant.infraestructura.arranque;

import mx.sivant.empleados.dominio.Empleado;
import mx.sivant.empleados.infraestructura.EmpleadoRepository;
import mx.sivant.empleados.infraestructura.RolRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Inserta empleados al arrancar, cuando todavía no hay un administrador autenticado. */
@Component
class AltaInicial {

    private final EmpleadoRepository empleados;
    private final RolRepository roles;
    private final PasswordEncoder codificador;

    AltaInicial(EmpleadoRepository empleados, RolRepository roles, PasswordEncoder codificador) {
        this.empleados = empleados;
        this.roles = roles;
        this.codificador = codificador;
    }

    boolean existeRol(String rol) {
        return empleados.existsByRolNombre(rol);
    }

    /** Conserva sin cambios al empleado que ya tenga ese correo. */
    void crearSiFalta(String nombre, String correo, String contrasena, String rol, boolean activo) {
        if (empleados.existsByCorreo(Empleado.normalizarCorreo(correo))) {
            return;
        }
        Empleado nuevo = new Empleado(nombre, correo, codificador.encode(contrasena),
                roles.findByNombre(rol).orElseThrow());
        if (!activo) {
            nuevo.desactivar();
        }
        empleados.save(nuevo);
    }
}
