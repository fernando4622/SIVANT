package mx.sivant.identidad.infraestructura;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import mx.sivant.empleados.aplicacion.ConsultaCredenciales;
import mx.sivant.empleados.aplicacion.CredencialEmpleado;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Comprueba en cada solicitud que el empleado siga activo, con el mismo rol y credenciales, y que la sesión
 * no supere el tiempo de inactividad. Si algo cambió, la solicitud continúa como anónima y la ruta protegida
 * responde 401.
 */
public class ValidacionSesionFilter extends OncePerRequestFilter {

    /** Solicitudes automáticas (sondeo): no cuentan como actividad. */
    public static final String ENCABEZADO_PASIVO = "X-Solicitud-Pasiva";
    public static final String ULTIMA_ACTIVIDAD = "sivant.ultimaActividad";
    static final String MOTIVO_CIERRE = "sivant.motivoCierre";

    private final ConsultaCredenciales credenciales;
    private final Clock reloj;
    private final Duration inactividad;

    ValidacionSesionFilter(ConsultaCredenciales credenciales, Clock reloj, Duration inactividad) {
        this.credenciales = credenciales;
        this.reloj = reloj;
        this.inactividad = inactividad;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest solicitud, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        Authentication actual = SecurityContextHolder.getContext().getAuthentication();
        if (actual != null && actual.getPrincipal() instanceof EmpleadoPrincipal principal) {
            validar(solicitud, principal);
        }
        cadena.doFilter(solicitud, respuesta);
    }

    private void validar(HttpServletRequest solicitud, EmpleadoPrincipal principal) {
        HttpSession sesion = solicitud.getSession(false);
        Long ultima = sesion == null ? null : (Long) sesion.getAttribute(ULTIMA_ACTIVIDAD);
        long ahora = reloj.millis();
        if (ultima == null || ahora - ultima >= inactividad.toMillis()) {
            cerrar(solicitud, sesion, "SESION_EXPIRADA");
            return;
        }
        CredencialEmpleado empleado = credenciales.porId(principal.id()).orElse(null);
        if (empleado == null || !empleado.activo() || empleado.sesionVersion() != principal.sesionVersion()) {
            cerrar(solicitud, sesion, "SESION_INVALIDADA");
            return;
        }
        if (!Boolean.parseBoolean(solicitud.getHeader(ENCABEZADO_PASIVO))) {
            sesion.setAttribute(ULTIMA_ACTIVIDAD, ahora);
        }
        // Los permisos de la solicitud salen del rol vigente en base de datos, no de la sesión.
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(ProveedorEmpleado.autenticacion(empleado));
        SecurityContextHolder.setContext(contexto);
    }

    private static void cerrar(HttpServletRequest solicitud, HttpSession sesion, String motivo) {
        if (sesion != null) {
            sesion.invalidate();
        }
        SecurityContextHolder.clearContext();
        solicitud.setAttribute(MOTIVO_CIERRE, motivo);
    }
}
