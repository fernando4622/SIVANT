package mx.sivant;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import mx.sivant.empleados.dominio.Empleado;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Soporte · Iniciar y cerrar sesión")
class SesionTest extends PruebaIntegracion {

    @ParameterizedTest(name = "CP-04 {0} inicia sesión y consulta su perfil")
    @ValueSource(strings = {"MESERO", "CAJERO", "COCINERO", "ADMINISTRADOR", "ALMACENISTA"})
    void loginPorRol(String rol) {
        Empleado e = crear(rol);
        ClienteApi cliente = anonimo();

        var login = cliente.login("  " + e.getCorreo().toUpperCase(Locale.ROOT) + " ", CONTRASENA);
        assertThat(login.estado()).isEqualTo(200);

        var perfil = cliente.get("/auth/me");
        assertThat(perfil.estado()).isEqualTo(200);
        assertThat(perfil.json().get("id").isString()).isTrue();
        assertThat(perfil.json().get("id").asString()).isEqualTo(e.getId().toString());
        assertThat(perfil.json().get("rol").asString()).isEqualTo(rol);
        assertThat(perfil.json().propertyNames()).containsExactlyInAnyOrder("id", "nombre", "rol");
    }

    @Test
    @DisplayName("CP-05 Contraseña errónea, correo inexistente y empleado inactivo reciben la misma respuesta")
    void credencialesInvalidas() {
        Empleado inactivo = crear("MESERO");
        inactivo.desactivar();
        empleados.save(inactivo);

        var respuestas = java.util.List.of(
                anonimo().login(administrador.getCorreo(), "otra-contrasena"),
                anonimo().login("nadie@sivant.test", CONTRASENA),
                anonimo().login(inactivo.getCorreo(), CONTRASENA));

        assertThat(respuestas).allSatisfy(r -> {
            assertThat(r.estado()).isEqualTo(401);
            assertThat(r.codigo()).isEqualTo("CREDENCIALES_INVALIDAS");
            assertThat(r.encabezados().get("content-type").getFirst()).startsWith("application/problem+json");
        });
        assertThat(respuestas.stream().map(ClienteApi.Respuesta::texto).distinct()).hasSize(1);
    }

    @Test
    @DisplayName("CP-06 Campos vacíos se informan por campo")
    void camposVacios() {
        var r = anonimo().login("", "");
        assertThat(r.estado()).isEqualTo(400);
        assertThat(r.campo("correo")).isNotBlank();
        assertThat(r.campo("contrasena")).isNotBlank();
    }

    @Test
    @DisplayName("CP-07 Sin token CSRF se rechazan login, logout y operaciones")
    void csrf() {
        ClienteApi cliente = anonimo();
        cliente.get("/auth/csrf");
        var login = cliente.sinCsrf("POST", "/auth/login",
                Map.of("correo", administrador.getCorreo(), "contrasena", CONTRASENA));
        assertThat(login.estado()).isEqualTo(403);
        assertThat(login.codigo()).isEqualTo("CSRF_INVALIDO");

        ClienteApi sesion = sesionDe(administrador);
        assertThat(sesion.sinCsrf("POST", "/auth/logout", null).estado()).isEqualTo(403);
        assertThat(sesion.sinCsrf("POST", "/empleados", Map.of()).codigo()).isEqualTo("CSRF_INVALIDO");
        assertThat(sesion.get("/auth/me").estado()).isEqualTo(200);
    }

    @Test
    @DisplayName("CP-08 El login cambia el ID de sesión, renueva CSRF y protege la cookie")
    void fijacionDeSesion() {
        ClienteApi cliente = sesionDe(administrador);
        String sesionAnterior = cliente.cookie("SIVANT_SESION");
        String csrfAnterior = cliente.cookie("XSRF-TOKEN");

        var login = cliente.login(administrador.getCorreo(), CONTRASENA);

        assertThat(cliente.cookie("SIVANT_SESION")).isNotEqualTo(sesionAnterior);
        assertThat(cliente.cookie("XSRF-TOKEN")).isNotBlank().isNotEqualTo(csrfAnterior);
        assertThat(login.encabezados().get("set-cookie"))
                .anySatisfy(c -> assertThat(c).startsWith("SIVANT_SESION=").contains("HttpOnly", "SameSite=Lax"));
        assertThat(cliente.post("/auth/logout", null).estado()).isEqualTo(204);
    }

    @Test
    @DisplayName("CP-09 El logout invalida la sesión en el servidor, aunque se reutilice la cookie")
    void logout() {
        ClienteApi cliente = sesionDe(administrador);
        String cookie = cliente.cookie("SIVANT_SESION");
        assertThat(cliente.post("/auth/logout", null).estado()).isEqualTo(204);

        cliente.ponerCookieSesion(cookie);
        var r = cliente.get("/auth/me");
        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.codigo()).isEqualTo("SESION_REQUERIDA");
    }

    @Test
    @DisplayName("CP-10 La sesión expira a los 45 minutos sin actividad")
    void inactividad() {
        ClienteApi cliente = sesionDe(administrador);
        reloj.avanzar(Duration.ofMinutes(44));
        assertThat(cliente.get("/auth/me").estado()).isEqualTo(200);
        reloj.avanzar(Duration.ofMinutes(44));
        assertThat(cliente.get("/auth/me").estado()).as("la actividad renueva el plazo").isEqualTo(200);
        reloj.avanzar(Duration.ofMinutes(45));
        assertThat(cliente.get("/auth/me").codigo()).isEqualTo("SESION_EXPIRADA");
        assertThat(cliente.get("/auth/me").estado()).isEqualTo(401);
        assertThat(cliente.login(administrador.getCorreo(), CONTRASENA).estado()).isEqualTo(200);
    }

    @Test
    @DisplayName("CP-11 El sondeo no cuenta como actividad")
    void sondeo() {
        ClienteApi cliente = sesionDe(administrador);
        reloj.avanzar(Duration.ofMinutes(30));
        assertThat(cliente.getPasivo("/auth/me").estado()).isEqualTo(200);
        reloj.avanzar(Duration.ofMinutes(15));
        var r = cliente.getPasivo("/auth/me");
        assertThat(r.estado()).isEqualTo(401);
        assertThat(r.codigo()).isEqualTo("SESION_EXPIRADA");
    }
}
