package mx.sivant;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;
import mx.sivant.catalogo.dominio.Insumo;
import mx.sivant.catalogo.dominio.UnidadMedida;
import mx.sivant.catalogo.infraestructura.InsumoRepository;
import mx.sivant.empleados.dominio.Empleado;
import mx.sivant.empleados.infraestructura.EmpleadoRepository;
import mx.sivant.empleados.infraestructura.RolRepository;
import mx.sivant.inventario.aplicacion.Existencias;
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
import org.springframework.transaction.support.TransactionTemplate;

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

    @Autowired
    TransactionTemplate transaccion;
    @Autowired
    InsumoRepository insumos;
    @Autowired
    Existencias existencias;

    Empleado administrador;

    @BeforeEach
    void limpiar() {
        // TRUNCATE no dispara la protección por fila de la auditoría.
        sql.execute("TRUNCATE sivant.auditoria, sivant.idempotencia_operacion, sivant.empleado, sivant.articulo, "
                + "sivant.existencia_inventario, sivant.mesa CASCADE");
        administrador = crear("ADMINISTRADOR");
    }

    Empleado crear(String rol) {
        return crear("Persona " + rol, rol.toLowerCase(Locale.ROOT) + "-" + UUID.randomUUID() + "@sivant.test", rol);
    }

    Empleado crear(String nombre, String correo, String rol) {
        return empleados.save(new Empleado(nombre, correo, codificador.encode(CONTRASENA),
                roles.findByNombre(rol).orElseThrow()));
    }

    /** Los insumos se preparan por carga autorizada, no por la API. */
    Long crearInsumo(String nombre, String unidad) {
        return transaccion.execute(t -> {
            Insumo insumo = insumos.saveAndFlush(new Insumo(nombre, UnidadMedida.valueOf(unidad)));
            existencias.crearParaInsumo(insumo.getId(), BigDecimal.TEN);
            return insumo.getId();
        });
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
