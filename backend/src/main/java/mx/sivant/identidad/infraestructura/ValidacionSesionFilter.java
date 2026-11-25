package mx.sivant.identidad.infraestructura;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.io.IOException;
import java.time.Clock;
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

    private final ValidadorSesion validador;
    private final Clock reloj;

    ValidacionSesionFilter(ValidadorSesion validador, Clock reloj) {
        this.validador = validador;
        this.reloj = reloj;
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
        ValidadorSesion.Resultado resultado = validador.validar(sesion, principal);
        if (!resultado.vigente()) {
            if (sesion != null) {
                sesion.invalidate();
            }
            SecurityContextHolder.clearContext();
            solicitud.setAttribute(MOTIVO_CIERRE, resultado.motivo());
            return;
        }
        if (!esPasiva(solicitud)) {
            sesion.setAttribute(ULTIMA_ACTIVIDAD, reloj.millis());
        }
        // Los permisos de la solicitud salen del rol vigente en base de datos, no de la sesión.
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(ProveedorEmpleado.autenticacion(resultado.empleado()));
        SecurityContextHolder.setContext(contexto);
    }

    /** La conexión WebSocket se abre y reintenta sola, y el navegador no permite agregarle encabezados. */
    private static boolean esPasiva(HttpServletRequest solicitud) {
        return Boolean.parseBoolean(solicitud.getHeader(ENCABEZADO_PASIVO)) || "/ws".equals(solicitud.getServletPath());
    }
}
