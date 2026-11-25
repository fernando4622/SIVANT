package mx.sivant.identidad.api;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.identidad.infraestructura.ValidacionSesionFilter;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** El cierre de sesión (POST /auth/logout) lo atiende Spring Security. */
@RestController
@RequestMapping("/api/v1/auth")
class AuthController {

    private static final Logger AUDITORIA = LoggerFactory.getLogger("sivant.auditoria");

    private final AuthenticationManager autenticador;
    private final SessionAuthenticationStrategy estrategia;
    private final SecurityContextRepository contextos;
    private final Clock reloj;

    AuthController(AuthenticationManager autenticador, SessionAuthenticationStrategy estrategia,
            SecurityContextRepository contextos, Clock reloj) {
        this.autenticador = autenticador;
        this.estrategia = estrategia;
        this.contextos = contextos;
        this.reloj = reloj;
    }

    record Credenciales(
            @NotBlank(message = "Escribe tu correo.")
            @Size(max = 254, message = "El correo es demasiado largo.") String correo,
            @NotBlank(message = "Escribe tu contraseña.")
            @Size(max = 200, message = "La contraseña es demasiado larga.") String contrasena) {

        @Override
        public String toString() {
            return "Credenciales[...]";
        }
    }

    record Perfil(String id, String nombre, String rol) {

        static Perfil de(EmpleadoPrincipal p) {
            return new Perfil(p.id().toString(), p.nombre(), p.rol());
        }
    }

    record TokenCsrf(String token, String encabezado) {
    }

    @GetMapping("/csrf")
    TokenCsrf csrf(CsrfToken token) {
        return new TokenCsrf(token.getToken(), token.getHeaderName());
    }

    @PostMapping("/login")
    Perfil login(@Valid @RequestBody Credenciales credenciales, HttpServletRequest solicitud,
            HttpServletResponse respuesta) {
        Authentication autenticacion;
        try {
            autenticacion = autenticador.authenticate(UsernamePasswordAuthenticationToken
                    .unauthenticated(credenciales.correo(), credenciales.contrasena()));
        } catch (AuthenticationException e) {
            AUDITORIA.info("accion=LOGIN_RECHAZADO origen={}", solicitud.getRemoteAddr());
            throw ReglaNegocioException.noAutenticado("CREDENCIALES_INVALIDAS",
                    "El correo o la contraseña no son correctos.");
        }
        estrategia.onAuthentication(autenticacion, solicitud, respuesta);
        solicitud.getSession().setAttribute(ValidacionSesionFilter.ULTIMA_ACTIVIDAD, reloj.millis());
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(autenticacion);
        SecurityContextHolder.setContext(contexto);
        contextos.saveContext(contexto, solicitud, respuesta);
        EmpleadoPrincipal principal = (EmpleadoPrincipal) autenticacion.getPrincipal();
        AUDITORIA.info("accion=LOGIN actor={}", principal.id());
        return Perfil.de(principal);
    }

    @GetMapping("/me")
    Perfil perfil(@AuthenticationPrincipal EmpleadoPrincipal principal) {
        return Perfil.de(principal);
    }
}
