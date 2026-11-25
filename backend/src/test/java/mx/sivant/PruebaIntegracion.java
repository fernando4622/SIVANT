package mx.sivant;

import java.util.Locale;
import java.util.UUID;
import mx.sivant.empleados.dominio.Empleado;
import mx.sivant.empleados.infraestructura.EmpleadoRepository;
import mx.sivant.empleados.infraestructura.RolRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;

/** Servidor real en un puerto libre, HTTP con cookies y PostgreSQL 18. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, PruebaIntegracion.Reloj.class})
abstract class PruebaIntegracion {

    static final String CONTRASENA = "Prueba.2026";

    @TestConfiguration(proxyBeanMethods = false)
    static class Reloj {

        @Bean
        @Primary
        RelojAjustable relojAjustable() {
            return new RelojAjustable();
        }
    }

    @LocalServerPort
    int puerto;
    @Autowired
    EmpleadoRepository empleados;
    @Autowired
    RolRepository roles;
    @Autowired
    PasswordEncoder codificador;
    @Autowired
    JdbcTemplate sql;
    @Autowired
    RelojAjustable reloj;

    Empleado administrador;

    @BeforeEach
    void limpiar() {
        sql.update("DELETE FROM sivant.empleado");
        administrador = crear("ADMINISTRADOR");
    }

    Empleado crear(String rol) {
        return crear("Persona " + rol, rol.toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID() + "@sivant.test", rol);
    }

    Empleado crear(String nombre, String correo, String rol) {
        return empleados.save(new Empleado(nombre, correo, codificador.encode(CONTRASENA),
                roles.findByNombre(rol).orElseThrow()));
    }

    Empleado recargar(Empleado e) {
        return empleados.findConRolById(e.getId()).orElseThrow();
    }

    ClienteApi anonimo() {
        return new ClienteApi(puerto);
    }

    ClienteApi sesionDe(Empleado e) {
        ClienteApi cliente = anonimo();
        var respuesta = cliente.login(e.getCorreo(), CONTRASENA);
        if (respuesta.estado() != 200) {
            throw new IllegalStateException("No se pudo iniciar sesión: " + respuesta.texto());
        }
        return cliente;
    }
}
