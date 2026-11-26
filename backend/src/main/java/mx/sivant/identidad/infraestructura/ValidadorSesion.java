package mx.sivant.identidad.infraestructura;

import jakarta.servlet.http.HttpSession;
import java.time.Clock;
import mx.sivant.empleados.aplicacion.ConsultaCredenciales;
import mx.sivant.empleados.aplicacion.CredencialEmpleado;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import org.springframework.stereotype.Component;

/** Regla única de vigencia, para HTTP y WebSocket. */
@Component
public class ValidadorSesion {

    private final ConsultaCredenciales credenciales;
    private final Clock reloj;
    private final PropiedadesSesion propiedades;

    ValidadorSesion(ConsultaCredenciales credenciales, Clock reloj, PropiedadesSesion propiedades) {
        this.credenciales = credenciales;
        this.reloj = reloj;
        this.propiedades = propiedades;
    }

    /** `motivo` es nulo si la sesión sigue vigente. */
    record Resultado(CredencialEmpleado empleado, String motivo) {

        boolean vigente() {
            return motivo == null;
        }
    }

    Resultado validar(HttpSession sesion, EmpleadoPrincipal principal) {
        Long ultima;
        try {
            ultima = sesion == null ? null : (Long) sesion.getAttribute(ValidacionSesionFilter.ULTIMA_ACTIVIDAD);
        } catch (IllegalStateException e) {
            // La sesión HTTP ya se invalidó, por ejemplo al cerrar sesión.
            ultima = null;
        }
        if (ultima == null || reloj.millis() - ultima >= propiedades.inactividad().toMillis()) {
            return new Resultado(null, "SESION_EXPIRADA");
        }
        CredencialEmpleado empleado = credenciales.porId(principal.id()).orElse(null);
        if (empleado == null || !empleado.activo() || empleado.sesionVersion() != principal.sesionVersion()) {
            return new Resultado(null, "SESION_INVALIDADA");
        }
        return new Resultado(empleado, null);
    }
}
