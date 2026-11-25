package mx.sivant.identidad.infraestructura;

import java.util.List;
import mx.sivant.empleados.aplicacion.ConsultaCredenciales;
import mx.sivant.empleados.aplicacion.CredencialEmpleado;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class ProveedorEmpleado implements AuthenticationProvider {

    private final ConsultaCredenciales credenciales;
    private final PasswordEncoder codificador;
    private final String hashSenuelo;

    ProveedorEmpleado(ConsultaCredenciales credenciales, PasswordEncoder codificador) {
        this.credenciales = credenciales;
        this.codificador = codificador;
        this.hashSenuelo = codificador.encode("correo-inexistente");
    }

    @Override
    public Authentication authenticate(Authentication solicitud) {
        String correo = solicitud.getName();
        String contrasena = String.valueOf(solicitud.getCredentials());
        CredencialEmpleado empleado = credenciales.porCorreo(correo).orElse(null);
        // Se compara siempre un hash para que el tiempo no revele si el correo existe.
        boolean coincide = codificador.matches(contrasena,
                empleado == null ? hashSenuelo : empleado.contrasenaHash());
        if (empleado == null || !coincide || !empleado.activo()) {
            throw new BadCredentialsException("Credenciales no válidas");
        }
        return autenticacion(empleado);
    }

    static Authentication autenticacion(CredencialEmpleado empleado) {
        var principal = new EmpleadoPrincipal(empleado.id(), empleado.nombreCompleto(), empleado.rol(),
                empleado.sesionVersion());
        return UsernamePasswordAuthenticationToken.authenticated(principal, null,
                List.of(new SimpleGrantedAuthority(empleado.rol())));
    }

    @Override
    public boolean supports(Class<?> tipo) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(tipo);
    }
}
