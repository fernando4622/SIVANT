package mx.sivant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import mx.sivant.empleados.dominio.Empleado;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.junit.jupiter.api.extension.ExtendWith;

@DisplayName("Gestionar empleados")
@ExtendWith(OutputCaptureExtension.class)
class GestionEmpleadosTest extends PruebaIntegracion {

    ClienteApi admin;

    @BeforeEach
    void iniciarSesion() {
        admin = sesionDe(administrador);
    }

    static Map<String, Object> alta(String correo) {
        return new HashMap<>(Map.of("nombreCompleto", "Laura Gómez", "correo", correo,
                "contrasena", "Inicial.2026", "rol", "MESERO"));
    }

    static List<Integer> simultaneas(List<Callable<Integer>> tareas) throws Exception {
        CyclicBarrier salida = new CyclicBarrier(tareas.size());
        try (var hilos = Executors.newFixedThreadPool(tareas.size())) {
            List<Future<Integer>> resultados = new ArrayList<>();
            for (Callable<Integer> tarea : tareas) {
                resultados.add(hilos.submit(() -> {
                    salida.await();
                    return tarea.call();
                }));
            }
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> r : resultados) {
                estados.add(r.get());
            }
            return estados;
        }
    }

    @Nested
    @DisplayName("RF11 · Registrar empleado")
    class Registrar {

        @Test
        @DisplayName("CP-16 Alta válida: 201, correo normalizado, hash BCrypt y acceso inmediato")
        void valida(CapturedOutput salida) {
            var r = admin.post("/empleados", alta(" Laura.Gomez@Sivant.TEST "));

            assertThat(r.estado()).isEqualTo(201);
            assertThat(r.encabezados().get("location").getFirst())
                    .isEqualTo("/api/v1/empleados/" + r.json().get("id").asString());
            assertThat(r.json().get("correo").asString()).isEqualTo("laura.gomez@sivant.test");
            assertThat(r.json().get("estado").asString()).isEqualTo("ACTIVO");
            assertThat(r.json().propertyNames())
                    .containsExactlyInAnyOrder("id", "nombreCompleto", "correo", "rol", "estado");

            String hash = empleados.findByCorreo("laura.gomez@sivant.test").orElseThrow().getContrasenaHash();
            assertThat(hash).startsWith("$2").isNotEqualTo("Inicial.2026");
            assertThat(salida.getAll()).doesNotContain("Inicial.2026", hash);
            assertThat(anonimo().login("laura.gomez@sivant.test", "Inicial.2026").estado()).isEqualTo(200);
        }

        @Test
        @DisplayName("CP-17 Correo repetido con otras mayúsculas: 409")
        void duplicado() {
            admin.post("/empleados", alta("repetido@sivant.test"));
            var r = admin.post("/empleados", alta("REPETIDO@Sivant.test"));
            assertThat(r.estado()).isEqualTo(409);
            assertThat(r.codigo()).isEqualTo("CORREO_DUPLICADO");
            assertThat(r.campo("correo")).isNotBlank();
        }

        @Test
        @DisplayName("CP-18 Altas simultáneas con el mismo correo: solo una se guarda")
        void concurrentes() throws Exception {
            List<Callable<Integer>> tareas = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                ClienteApi sesion = sesionDe(administrador);
                tareas.add(() -> sesion.post("/empleados", alta("carrera@sivant.test")).estado());
            }
            assertThat(simultaneas(tareas)).containsExactlyInAnyOrder(201, 409, 409, 409);
        }

        @Test
        @DisplayName("CP-19 Datos inválidos: 400 con el error de cada campo")
        void invalidos() {
            var vacio = admin.post("/empleados", Map.of());
            assertThat(vacio.estado()).isEqualTo(400);
            for (String campo : List.of("nombreCompleto", "correo", "contrasena", "rol")) {
                assertThat(vacio.campo(campo)).as(campo).isNotBlank();
            }
            Map<String, Object> casos = Map.of("nombreCompleto", "   ", "correo", "sin-arroba", "contrasena", "corta",
                    "rol", "GERENTE");
            casos.forEach((campo, valor) -> {
                Map<String, Object> datos = alta("invalido@sivant.test");
                datos.put(campo, valor);
                var r = admin.post("/empleados", datos);
                assertThat(r.estado()).as(campo).isEqualTo(400);
                assertThat(r.campo(campo)).as(campo).isNotBlank();
            });
            Map<String, Object> larga = alta("larga@sivant.test");
            larga.put("contrasena", "ñ".repeat(40));
            assertThat(admin.post("/empleados", larga).campo("contrasena")).isNotBlank();
            Map<String, Object> extra = alta("extra@sivant.test");
            extra.put("estado", "INACTIVO");
            assertThat(admin.post("/empleados", extra).estado()).isEqualTo(400);
            assertThat(admin.post("/empleados", "{mal").estado()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("RF12 · Consultar empleado")
    class Consultar {

        @BeforeEach
        void datos() {
            crear("Ana Prueba", "ana_p@sivant.test", "CAJERO");
            crear("Beto Prueba", "beto@sivant.test", "MESERO");
            Empleado inactivo = crear("Ciro Prueba", "ciro@sivant.test", "MESERO");
            inactivo.desactivar();
            empleados.save(inactivo);
        }

        private List<String> nombres(String filtro) {
            List<String> nombres = new ArrayList<>();
            admin.get("/empleados?" + filtro).json().get("contenido")
                    .forEach(e -> nombres.add(e.get("nombreCompleto").asString()));
            return nombres;
        }

        @Test
        @DisplayName("CP-20 El listado incluye activos e inactivos, sin secretos y en orden estable")
        void listado() {
            var r = admin.get("/empleados");
            assertThat(r.json().get("totalElementos").asInt()).isEqualTo(4);
            assertThat(nombres("")).containsExactly("Ana Prueba", "Beto Prueba", "Ciro Prueba",
                    "Persona ADMINISTRADOR");
            assertThat(r.texto().toLowerCase()).doesNotContain("contrasena", "hash", "$2a$", "sesionversion");
        }

        @Test
        @DisplayName("CP-21 Filtra por nombre, correo, rol, estado y búsqueda combinada")
        void filtros() {
            assertThat(nombres("nombre=bETO")).containsExactly("Beto Prueba");
            assertThat(nombres("correo=ciro@")).containsExactly("Ciro Prueba");
            assertThat(nombres("rol=MESERO")).containsExactly("Beto Prueba", "Ciro Prueba");
            assertThat(nombres("estado=INACTIVO")).containsExactly("Ciro Prueba");
            assertThat(nombres("rol=MESERO&estado=ACTIVO")).containsExactly("Beto Prueba");
            assertThat(nombres("q=prueba&rol=CAJERO")).containsExactly("Ana Prueba");
            assertThat(nombres("q=ana_p")).containsExactly("Ana Prueba");
            assertThat(nombres("nombre=%25")).as("el comodín se busca literal").isEmpty();
            assertThat(admin.get("/empleados?rol=GERENTE").estado()).isEqualTo(400);
            assertThat(admin.get("/empleados?estado=BAJA").campo("estado")).isNotBlank();
        }

        @Test
        @DisplayName("CP-22 Pagina desde cero y limita el tamaño a 100")
        void paginacion() {
            var r = admin.get("/empleados?page=1&size=3");
            assertThat(r.json().get("contenido").size()).isEqualTo(1);
            assertThat(r.json().get("totalPaginas").asInt()).isEqualTo(2);
            assertThat(admin.get("/empleados?size=101").campo("size")).isNotBlank();
            assertThat(admin.get("/empleados?size=0").estado()).isEqualTo(400);
            assertThat(admin.get("/empleados?page=-1").campo("page")).isNotBlank();
        }

        @Test
        @DisplayName("CP-23 Detalle existente, inexistente e ID inválido")
        void detalle() {
            var r = admin.get("/empleados/" + administrador.getId());
            assertThat(r.json().get("correo").asString()).isEqualTo(administrador.getCorreo());
            assertThat(admin.get("/empleados/999999999").estado()).isEqualTo(404);
            assertThat(admin.get("/empleados/abc").estado()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("RF13 · Editar empleado")
    class Editar {

        private String ruta(Empleado e) {
            return "/empleados/" + e.getId();
        }

        @Test
        @DisplayName("CP-24 Edición parcial: conserva lo no enviado y la sesión del empleado")
        void parcial() {
            Empleado e = crear("CAJERO");
            ClienteApi sesion = sesionDe(e);
            var r = admin.patch(ruta(e), Map.of("nombreCompleto", " Nombre Nuevo ", "correo", "NUEVO@sivant.test"));
            assertThat(r.estado()).isEqualTo(200);
            assertThat(r.json().get("nombreCompleto").asString()).isEqualTo("Nombre Nuevo");
            assertThat(r.json().get("correo").asString()).isEqualTo("nuevo@sivant.test");
            assertThat(r.json().get("rol").asString()).isEqualTo("CAJERO");
            assertThat(sesion.get("/auth/me").json().get("nombre").asString()).isEqualTo("Nombre Nuevo");
        }

        @Test
        @DisplayName("CP-25 Correo de otro empleado: 409 sin cambios parciales; el propio se acepta")
        void correoDuplicado() {
            Empleado e = crear("CAJERO");
            var r = admin.patch(ruta(e), Map.of("nombreCompleto", "Otro Nombre",
                    "correo", administrador.getCorreo().toUpperCase()));
            assertThat(r.estado()).isEqualTo(409);
            assertThat(r.codigo()).isEqualTo("CORREO_DUPLICADO");
            assertThat(recargar(e).getNombreCompleto()).isEqualTo(e.getNombreCompleto());
            assertThat(admin.patch(ruta(e), Map.of("correo", e.getCorreo().toUpperCase())).estado()).isEqualTo(200);
        }

        @Test
        @DisplayName("CP-26 Cambiar la contraseña cierra la sesión abierta y anula la contraseña anterior")
        void contrasena() {
            Empleado e = crear("CAJERO");
            ClienteApi sesion = sesionDe(e);
            assertThat(admin.patch(ruta(e), Map.of("contrasena", "Nueva.Clave.2026")).estado()).isEqualTo(200);
            var r = sesion.get("/auth/me");
            assertThat(r.estado()).isEqualTo(401);
            assertThat(r.codigo()).isEqualTo("SESION_INVALIDADA");
            assertThat(anonimo().login(e.getCorreo(), CONTRASENA).estado()).isEqualTo(401);
            assertThat(anonimo().login(e.getCorreo(), "Nueva.Clave.2026").estado()).isEqualTo(200);
        }

        @Test
        @DisplayName("CP-27 Cambiar el rol retira los permisos de la sesión abierta")
        void rol() {
            Empleado otroAdmin = crear("ADMINISTRADOR");
            ClienteApi sesion = sesionDe(otroAdmin);
            assertThat(sesion.get("/empleados").estado()).isEqualTo(200);
            assertThat(admin.patch(ruta(otroAdmin), Map.of("rol", "MESERO")).json().get("rol").asString())
                    .isEqualTo("MESERO");
            assertThat(sesion.get("/empleados").estado()).isEqualTo(401);
            assertThat(sesionDe(otroAdmin).get("/empleados").estado()).isEqualTo(403);
        }

        @Test
        @DisplayName("CP-28 Edición vacía, valores inválidos o empleado inexistente")
        void invalida() {
            Empleado e = crear("CAJERO");
            assertThat(admin.patch(ruta(e), Map.of()).estado()).isEqualTo(400);
            assertThat(admin.patch(ruta(e), Map.of("nombreCompleto", " ")).campo("nombreCompleto")).isNotBlank();
            assertThat(admin.patch(ruta(e), Map.of("correo", "")).campo("correo")).isNotBlank();
            assertThat(admin.patch(ruta(e), Map.of("contrasena", "corta")).campo("contrasena")).isNotBlank();
            assertThat(admin.patch(ruta(e), Map.of("rol", "GERENTE")).campo("rol")).isNotBlank();
            assertThat(admin.patch(ruta(e), Map.of("estado", "INACTIVO")).estado()).isEqualTo(400);
            assertThat(admin.patch("/empleados/999999999", Map.of("rol", "MESERO")).estado()).isEqualTo(404);
        }

        @Test
        @DisplayName("CP-29 El último administrador activo conserva su rol")
        void ultimoAdministrador() {
            var r = admin.patch(ruta(administrador), Map.of("rol", "MESERO"));
            assertThat(r.estado()).isEqualTo(409);
            assertThat(r.codigo()).isEqualTo("ULTIMO_ADMINISTRADOR");
            assertThat(admin.get("/empleados").estado()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("RF14 · Desactivar empleado")
    class Desactivar {

        private ClienteApi.Respuesta desactivar(Empleado e, Object cuerpo) {
            return admin.post("/empleados/" + e.getId() + "/desactivacion", cuerpo);
        }

        @Test
        @DisplayName("CP-30 Sin confirmación no hay cambios")
        void sinConfirmar() {
            Empleado e = crear("MESERO");
            assertThat(desactivar(e, Map.of("confirmado", false)).estado()).isEqualTo(400);
            assertThat(desactivar(e, Map.of()).campo("confirmado")).isNotBlank();
            assertThat(recargar(e).estaActivo()).isTrue();
        }

        @Test
        @DisplayName("CP-31 El desactivado pierde acceso con la sesión abierta y no puede volver a entrar")
        void pierdeAcceso() {
            Empleado e = crear("ADMINISTRADOR");
            ClienteApi sesion = sesionDe(e);
            assertThat(sesion.get("/empleados").estado()).isEqualTo(200);

            var r = desactivar(e, Map.of("confirmado", true));
            assertThat(r.estado()).isEqualTo(200);
            assertThat(r.json().get("estado").asString()).isEqualTo("INACTIVO");

            var rechazada = sesion.post("/empleados", alta("no.debe.existir@sivant.test"));
            assertThat(rechazada.estado()).isEqualTo(401);
            assertThat(rechazada.codigo()).isEqualTo("SESION_INVALIDADA");
            assertThat(sesion.get("/auth/me").estado()).isEqualTo(401);
            assertThat(empleados.existsByCorreo("no.debe.existir@sivant.test")).isFalse();
            assertThat(anonimo().login(e.getCorreo(), CONTRASENA).estado()).isEqualTo(401);
        }

        @Test
        @DisplayName("CP-32 Conserva el registro y rechaza repetir la operación")
        void conservaRegistro() {
            Empleado e = crear("MESERO");
            desactivar(e, Map.of("confirmado", true));
            var detalle = admin.get("/empleados/" + e.getId());
            assertThat(detalle.json().get("correo").asString()).isEqualTo(e.getCorreo());
            assertThat(desactivar(e, Map.of("confirmado", true)).codigo()).isEqualTo("EMPLEADO_INACTIVO");
            assertThat(admin.post("/empleados", alta(e.getCorreo())).estado()).isEqualTo(409);
        }

        @Test
        void reactivarRestableceElAccesoSinRecuperarSesionesAnteriores() {
            Empleado e = crear("ADMINISTRADOR");
            ClienteApi sesionAnterior = sesionDe(e);
            String reactivacion = "/empleados/" + e.getId() + "/reactivacion";

            assertThat(desactivar(e, Map.of("confirmado", true)).estado()).isEqualTo(200);
            assertThat(admin.post(reactivacion, Map.of("confirmado", false)).estado()).isEqualTo(400);
            assertThat(recargar(e).estaActivo()).isFalse();
            assertThat(sesionDe(crear("MESERO")).post(reactivacion, Map.of("confirmado", true)).estado())
                    .isEqualTo(403);

            var respuesta = admin.post(reactivacion, Map.of("confirmado", true));
            assertThat(respuesta.estado()).isEqualTo(200);
            assertThat(respuesta.json().get("estado").asString()).isEqualTo("ACTIVO");
            assertThat(respuesta.json().get("rol").asString()).isEqualTo("ADMINISTRADOR");
            assertThat(sesionAnterior.get("/auth/me").estado()).isEqualTo(401);
            assertThat(anonimo().login(e.getCorreo(), CONTRASENA).estado()).isEqualTo(200);
            assertThat(admin.post(reactivacion, Map.of("confirmado", true)).codigo()).isEqualTo("EMPLEADO_ACTIVO");
        }

        @Test
        @DisplayName("CP-33 El último administrador activo no puede desactivarse")
        void ultimoAdministrador() {
            assertThat(desactivar(administrador, Map.of("confirmado", true)).codigo())
                    .isEqualTo("ULTIMO_ADMINISTRADOR");
            assertThat(recargar(administrador).estaActivo()).isTrue();
        }

        @Test
        @DisplayName("CP-34 Dos administradores que se desactivan a la vez: siempre queda uno")
        void cruzada() throws Exception {
            for (int i = 0; i < 5; i++) {
                limpiar();
                Empleado otro = crear("ADMINISTRADOR");
                ClienteApi sesionA = sesionDe(administrador);
                ClienteApi sesionB = sesionDe(otro);
                String rutaA = "/empleados/" + administrador.getId() + "/desactivacion";
                String rutaB = "/empleados/" + otro.getId() + "/desactivacion";
                List<Integer> estados = simultaneas(List.of(
                        () -> sesionA.post(rutaB, Map.of("confirmado", true)).estado(),
                        () -> sesionB.post(rutaA, Map.of("confirmado", true)).estado()));
                assertThat(estados).containsOnlyOnce(200).doesNotContain(500);
                assertThat(sql.queryForObject("SELECT count(*) FROM sivant.empleado WHERE estado = 'ACTIVO'",
                        Integer.class)).isEqualTo(1);
            }
        }
    }
}
