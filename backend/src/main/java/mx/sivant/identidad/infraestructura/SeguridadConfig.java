package mx.sivant.identidad.infraestructura;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.List;
import mx.sivant.empleados.aplicacion.ConsultaCredenciales;
import mx.sivant.infraestructura.errores.Problemas;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import tools.jackson.databind.json.JsonMapper;

@Configuration
@EnableMethodSecurity
class SeguridadConfig {

    private static final String API = "/api/v1";

    @Bean
    SecurityFilterChain cadena(HttpSecurity http, ConsultaCredenciales credenciales, Clock reloj,
            PropiedadesSesion sesion, SecurityContextRepository contextos, CsrfTokenRepository csrf,
            JsonMapper json) throws Exception {
        http
                .authorizeHttpRequests(rutas -> rutas
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(HttpMethod.GET, API + "/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, API + "/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, API + "/auth/me").authenticated()
                        .requestMatchers(API + "/empleados/**").hasAuthority("ADMINISTRADOR")
                        .requestMatchers(HttpMethod.GET, API + "/productos/**").hasAnyAuthority("ADMINISTRADOR", "MESERO")
                        .requestMatchers(API + "/productos/**", API + "/insumos").hasAuthority("ADMINISTRADOR")
                        // Cada módulo nuevo declara aquí sus roles. Lo no declarado se rechaza.
                        .anyRequest().denyAll())
                .securityContext(contexto -> contexto.securityContextRepository(contextos))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
                .csrf(c -> c.csrfTokenRepository(csrf).csrfTokenRequestHandler(new CsrfSpaHandler()))
                .logout(salida -> salida
                        .logoutUrl(API + "/auth/logout")
                        .deleteCookies("SIVANT_SESION")
                        .logoutSuccessHandler((solicitud, respuesta, autenticacion) ->
                                respuesta.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(sinSesion(json))
                        .accessDeniedHandler(sinPermiso(json)))
                .requestCache(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .addFilterBefore(new ValidacionSesionFilter(credenciales, reloj, sesion.inactividad()),
                        AnonymousAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    PasswordEncoder codificador() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    AuthenticationManager autenticador(ProveedorEmpleado proveedor) {
        return new ProviderManager(proveedor);
    }

    @Bean
    SecurityContextRepository contextos() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    CsrfTokenRepository repositorioCsrf(@Value("${server.servlet.session.cookie.secure}") boolean segura) {
        CookieCsrfTokenRepository repositorio = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repositorio.setCookieCustomizer(cookie -> cookie.secure(segura).sameSite("Lax").path("/"));
        return repositorio;
    }

    /** Al iniciar sesión cambia el ID de sesión y renueva el token CSRF. */
    @Bean
    SessionAuthenticationStrategy estrategiaSesion(CsrfTokenRepository csrf) {
        CsrfAuthenticationStrategy renovarCsrf = new CsrfAuthenticationStrategy(csrf);
        renovarCsrf.setRequestHandler(new CsrfSpaHandler());
        return new CompositeSessionAuthenticationStrategy(
                List.of(new ChangeSessionIdAuthenticationStrategy(), renovarCsrf));
    }

    private static AuthenticationEntryPoint sinSesion(JsonMapper json) {
        return (solicitud, respuesta, error) -> {
            Object motivo = solicitud.getAttribute(ValidacionSesionFilter.MOTIVO_CIERRE);
            String codigo = motivo == null ? "SESION_REQUERIDA" : motivo.toString();
            String detalle = switch (codigo) {
                case "SESION_EXPIRADA" -> "La sesión se cerró por inactividad. Inicia sesión de nuevo.";
                case "SESION_INVALIDADA" -> "Tu acceso cambió. Inicia sesión de nuevo.";
                default -> "Inicia sesión para continuar.";
            };
            escribir(json, respuesta,
                    Problemas.crear(HttpStatus.UNAUTHORIZED, codigo, detalle, solicitud.getRequestURI()));
        };
    }

    private static AccessDeniedHandler sinPermiso(JsonMapper json) {
        return (solicitud, respuesta, error) -> {
            boolean csrf = error instanceof CsrfException;
            escribir(json, respuesta, Problemas.crear(HttpStatus.FORBIDDEN, csrf ? "CSRF_INVALIDO" : "ACCESO_DENEGADO",
                    csrf ? "La solicitud no pudo validarse. Recarga la página." : "Tu rol no permite esta operación.",
                    solicitud.getRequestURI()));
        };
    }

    private static void escribir(JsonMapper json, HttpServletResponse respuesta, ProblemDetail problema)
            throws IOException {
        respuesta.setStatus(problema.getStatus());
        respuesta.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        respuesta.setCharacterEncoding("UTF-8");
        json.writeValue(respuesta.getOutputStream(), problema);
    }
}
