package mx.sivant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Cocina y avisos")
class CocinaTest extends PruebaServicio {

    private JsonNode cola() {
        return new JsonMapper().readTree(cocinero.get("/cocina/cola").texto());
    }

    @Nested
    @DisplayName("RF19 · Consultar cola de pedidos")
    class Cola {

        @Test
        @DisplayName("CP-92 La cola respeta el orden de envío, muestra los datos operativos y no incluye precios")
        void orden() {
            String cuenta = cuentaNueva();
            List<String> ids = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                ids.add(comandaNueva(cuenta).get("id").asString());
            }
            preparar(ids.get(2), 0);
            lista(ids.get(2), 1);
            preparar(ids.get(1), 0);
            JsonNode cola = cola();
            assertThat(cola.size()).isEqualTo(3);
            for (int i = 0; i < 3; i++) {
                assertThat(cola.get(i).get("id").asString()).isEqualTo(ids.get(i));
            }
            JsonNode primera = cola.get(0);
            assertThat(primera.get("mesa").asInt()).isEqualTo(1);
            assertThat(primera.get("cuentaId").asString()).isEqualTo(cuenta);
            assertThat(primera.get("mesero").asString()).isEqualTo("Juan Pérez");
            assertThat(primera.get("estado").asString()).isEqualTo("ENVIADA");
            assertThat(primera.get("detalles").get(1).get("producto").asString()).isEqualTo("Galleta");
            assertThat(primera.get("detalles").get(1).get("observaciones").asString()).isEqualTo("Sin azúcar");
            assertThat(cola.toString().toLowerCase()).doesNotContain("precio", "subtotal");
        }

        @Test
        @DisplayName("CP-93 Servidas y canceladas salen de la cola")
        void salen() {
            String cuenta = cuentaNueva();
            String a = comandaNueva(cuenta).get("id").asString();
            String b = comandaNueva(cuenta).get("id").asString();
            cancelar(cajero, a, 0, "x");
            sql.update("UPDATE sivant.comanda SET estado = 'SERVIDA' WHERE id_comanda = ?", Long.valueOf(b));
            assertThat(cola().size()).isZero();
        }
    }

    @Nested
    @DisplayName("RF20 · Marcar pedido en preparación")
    class Preparacion {

        @Test
        @DisplayName("CP-94 Registra responsable y fecha, y no avisa al mesero")
        void iniciar() {
            String id = comandaNueva(cuentaNueva()).get("id").asString();
            var r = preparar(id, 0);
            assertThat(r.estado()).isEqualTo(200);
            assertThat(r.json().get("estado").asString()).isEqualTo("EN_PREPARACION");
            assertThat(r.json().get("version").asInt()).isEqualTo(1);
            assertThat(sql.queryForObject("""
                    SELECT e.nombre_completo FROM sivant.historial_estado_comanda h JOIN sivant.empleado e USING (id_empleado)
                    WHERE h.estado_anterior = 'ENVIADA' AND h.estado_nuevo = 'EN_PREPARACION' AND h.fecha_hora IS NOT NULL
                    """, String.class)).isEqualTo("Carla Cocina");
            assertThat(avisos(mesero, "")).isZero();
        }

        @Test
        @DisplayName("CP-95 Transiciones inválidas, versión anterior o comanda inexistente se rechazan")
        void invalidas() {
            String id = comandaNueva(cuentaNueva()).get("id").asString();
            assertThat(lista(id, 0).codigo()).as("enviada a lista").isEqualTo("TRANSICION_INVALIDA");
            preparar(id, 0);
            assertThat(preparar(id, 1).codigo()).isEqualTo("TRANSICION_INVALIDA");
            assertThat(preparar("999999", 0).estado()).isEqualTo(404);
            assertThat(cocinero.post("/comandas/" + id + "/lista", Map.of()).campo("version")).isNotBlank();
            assertThat(lista(id, 0).codigo()).isEqualTo("VERSION_OBSOLETA");
            cancelar(cajero, id, 1, "x");
            assertThat(lista(id, 2).codigo()).isEqualTo("TRANSICION_INVALIDA");
        }

        @Test
        @DisplayName("CP-96 Si el mesero edita, cocina no avanza con la versión que ya no está vigente")
        void edicionDelMesero() {
            JsonNode comanda = comandaNueva(cuentaNueva());
            String id = comanda.get("id").asString();
            editar(mesero, id, 0, detalle(comanda.get("detalles").get(0), 9));
            assertThat(preparar(id, 0).codigo()).isEqualTo("VERSION_OBSOLETA");
            assertThat(preparar(id, 1).estado()).isEqualTo(200);
        }

        @Test
        @DisplayName("CP-97 Dos cocineros inician la misma comanda: solo uno lo logra")
        void simultanea() throws Exception {
            String id = comandaNueva(cuentaNueva()).get("id").asString();
            ClienteApi otro = sesionDe(crear("COCINERO"));
            assertThat(GestionEmpleadosTest.simultaneas(List.of(
                    () -> preparar(id, 0).estado(),
                    () -> otro.post("/comandas/" + id + "/preparacion", Map.of("version", 0)).estado())))
                    .containsExactlyInAnyOrder(200, 409);
        }
    }

    @Nested
    @DisplayName("RF21 · Marcar pedido listo")
    class Lista {

        @Test
        @DisplayName("CP-98 Avisa solo al mesero responsable, con mesa y cuenta, y sigue en la cola")
        void avisa() {
            String cuenta = cuentaNueva();
            String id = comandaNueva(cuenta).get("id").asString();
            preparar(id, 0);
            assertThat(lista(id, 1).json().get("estado").asString()).isEqualTo("LISTA");
            var aviso = mesero.get("/notificaciones").json().get("contenido").get(0);
            assertThat(aviso.get("tipo").asString()).isEqualTo("COMANDA_LISTA");
            assertThat(aviso.get("mensaje").asString()).contains("Mesa 1", "cuenta " + cuenta);
            assertThat(aviso.get("contexto").get("cuentaId").asString()).isEqualTo(cuenta);
            assertThat(aviso.get("comandaId").asString()).isEqualTo(id);
            for (ClienteApi c : List.of(otroMesero, cajero, cocinero, admin)) {
                assertThat(avisos(c, "")).isZero();
            }
            assertThat(cola().size()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Notificaciones")
    class Notificaciones {

        @Test
        @DisplayName("CP-99 La lectura es individual, también en avisos dirigidos a un rol, y persiste")
        void lecturaIndividual() {
            String id = comandaNueva(cuentaNueva()).get("id").asString();
            cancelar(cajero, id, 0, "x");
            ClienteApi otro = sesionDe(crear("COCINERO"));
            String aviso = cocinero.get("/notificaciones?leida=false").json().get("contenido").get(0).get("id").asString();
            String ruta = "/notificaciones/" + aviso + "/lectura";
            assertThat(cocinero.put(ruta, null).estado()).isEqualTo(204);
            assertThat(cocinero.put(ruta, null).estado()).as("repetir no falla").isEqualTo(204);
            assertThat(avisos(cocinero, "leida=false")).isZero();
            assertThat(avisos(cocinero, "leida=true")).isEqualTo(1);
            assertThat(avisos(otro, "leida=false")).isEqualTo(1);
        }

        @Test
        @DisplayName("El descarte es individual, idempotente y conserva la notificación")
        void descarteIndividual() {
            String id = comandaNueva(cuentaNueva()).get("id").asString();
            cancelar(cajero, id, 0, "x");
            ClienteApi otro = sesionDe(crear("COCINERO"));
            String aviso = cocinero.get("/notificaciones").json().get("contenido").get(0).get("id").asString();
            String ruta = "/notificaciones/" + aviso + "/descarte";
            assertThat(cocinero.put(ruta, null).estado()).isEqualTo(204);
            assertThat(cocinero.put(ruta, null).estado()).as("repetir no falla").isEqualTo(204);
            assertThat(avisos(cocinero, "")).isZero();
            assertThat(avisos(otro, "leida=false")).isEqualTo(1);
            assertThat(sql.queryForObject("SELECT count(*) FROM sivant.notificacion WHERE id_notificacion = ?",
                    Long.class, Long.valueOf(aviso))).isEqualTo(1L);
        }

        @Test
        @DisplayName("CP-100 Nadie lee un aviso que no le corresponde")
        void ajena() {
            String id = comandaNueva(cuentaNueva()).get("id").asString();
            preparar(id, 0);
            lista(id, 1);
            String aviso = mesero.get("/notificaciones").json().get("contenido").get(0).get("id").asString();
            for (ClienteApi c : List.of(otroMesero, cocinero, admin)) {
                assertThat(c.put("/notificaciones/" + aviso + "/lectura", null).estado()).isEqualTo(404);
                assertThat(c.put("/notificaciones/" + aviso + "/descarte", null).estado()).isEqualTo(404);
            }
            assertThat(mesero.put("/notificaciones/999999/lectura", null).estado()).isEqualTo(404);
            assertThat(anonimo().get("/notificaciones").estado()).isEqualTo(401);
        }
    }

    @ParameterizedTest(name = "CP-101 Permisos de cocina para {0}")
    @ValueSource(strings = {"MESERO", "CAJERO", "ALMACENISTA", "ADMINISTRADOR"})
    void permisos(String rol) {
        String id = comandaNueva(cuentaNueva()).get("id").asString();
        ClienteApi c = sesionDe(crear(rol));
        assertThat(c.get("/cocina/cola").estado()).isEqualTo(403);
        assertThat(c.post("/comandas/" + id + "/preparacion", Map.of("version", 0)).estado()).isEqualTo(403);
        assertThat(c.post("/comandas/" + id + "/lista", Map.of("version", 0)).estado()).isEqualTo(403);
        assertThat(cola().get(0).get("estado").asString()).isEqualTo("ENVIADA");
    }
}
