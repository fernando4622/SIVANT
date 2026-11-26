package mx.sivant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import mx.sivant.empleados.dominio.Empleado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Actores · Autorización en el servidor")
class PermisosTest extends PruebaIntegracion {

    @ParameterizedTest(name = "CP-12 {0} no puede gestionar empleados y nada cambia")
    @ValueSource(strings = {"MESERO", "CAJERO", "COCINERO", "ALMACENISTA"})
    void rolSinPermiso(String rol) {
        Empleado objetivo = crear("MESERO");
        ClienteApi cliente = sesionDe(crear(rol));
        long total = empleados.count();

        assertThat(operaciones(cliente, objetivo)).hasSize(5).allSatisfy(r -> {
            assertThat(r.estado()).isEqualTo(403);
            assertThat(r.codigo()).isEqualTo("ACCESO_DENEGADO");
        });

        assertThat(empleados.count()).isEqualTo(total);
        assertThat(recargar(objetivo).estaActivo()).isTrue();
        assertThat(recargar(objetivo).getNombreCompleto()).isEqualTo(objetivo.getNombreCompleto());
    }

    @Test
    @DisplayName("CP-13 El administrador puede ejecutar las cinco operaciones")
    void administrador() {
        assertThat(operaciones(sesionDe(administrador), crear("MESERO")))
                .extracting(ClienteApi.Respuesta::estado).containsExactly(201, 200, 200, 200, 200);
    }

    @Test
    @DisplayName("CP-14 Sin sesión toda operación responde 401")
    void sinSesion() {
        ClienteApi cliente = anonimo();
        cliente.get("/auth/csrf");
        assertThat(operaciones(cliente, crear("MESERO"))).allSatisfy(r -> {
            assertThat(r.estado()).isEqualTo(401);
            assertThat(r.codigo()).isEqualTo("SESION_REQUERIDA");
        });
        assertThat(cliente.get("/auth/me").estado()).isEqualTo(401);
    }

    @Test
    @DisplayName("CP-15 Una ruta no declarada se rechaza incluso para el administrador")
    void rutaNoDeclarada() {
        assertThat(sesionDe(administrador).get("/reportes").estado()).isEqualTo(403);
    }

    private static List<ClienteApi.Respuesta> operaciones(ClienteApi cliente, Empleado objetivo) {
        String ruta = "/empleados/" + objetivo.getId();
        return List.of(
                cliente.post("/empleados", Map.of("nombreCompleto", "Alta no permitida", "correo", "alta@sivant.test",
                        "contrasena", CONTRASENA, "rol", "MESERO")),
                cliente.get("/empleados"),
                cliente.get(ruta),
                cliente.patch(ruta, Map.of("nombreCompleto", "Nombre cambiado")),
                cliente.post(ruta + "/desactivacion", Map.of("confirmado", true)));
    }
}
