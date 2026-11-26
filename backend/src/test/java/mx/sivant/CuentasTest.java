package mx.sivant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Mesas y cuentas")
class CuentasTest extends PruebaServicio {

    @Nested
    @DisplayName("RF01 · Registrar pedido")
    class Registrar {

        @Test
        @DisplayName("CP-73 Abrir una cuenta ocupa la mesa y conserva al mesero responsable")
        void apertura() {
            var r = abrir(mesero, mesa1);
            assertThat(r.estado()).isEqualTo(201);
            assertThat(r.json().get("mesero").asString()).isEqualTo("Juan Pérez");
            assertThat(r.json().get("total").asString()).isEqualTo("0.00");
            assertThat(r.json().get("acciones").toString()).contains("ENVIAR_COMANDA", "CERRAR_SIN_COBRO");
            var mesas = new JsonMapper().readTree(mesero.get("/mesas").texto());
            assertThat(mesas.get(0).get("disponible").asBoolean()).isFalse();
            assertThat(mesas.get(0).get("cuentaId").asString()).isEqualTo(r.json().get("id").asString());
            assertThat(mesas.get(1).get("disponible").asBoolean()).isTrue();
            var ajenas = new JsonMapper().readTree(otroMesero.get("/mesas").texto());
            assertThat(ajenas.get(0).has("cuentaId")).as("la cuenta ajena no se expone").isFalse();
            assertThat(ajenas.get(0).get("mesero").asString()).isEqualTo("Juan Pérez");
        }

        @Test
        @DisplayName("CP-74 Dos aperturas simultáneas de la misma mesa: solo una se logra")
        void aperturaSimultanea() throws Exception {
            List<Callable<Integer>> tareas = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                tareas.add(() -> abrir(mesero, mesa1).estado());
                tareas.add(() -> abrir(otroMesero, mesa1).estado());
            }
            assertThat(GestionEmpleadosTest.simultaneas(tareas)).containsOnlyOnce(201).doesNotContain(500);
            assertThat(abrir(otroMesero, mesa1).codigo()).isEqualTo("MESA_OCUPADA");
            assertThat(abrir(mesero, "999999").campo("mesaId")).isNotBlank();
        }

        @Test
        @DisplayName("CP-75 La comanda se envía a cocina con precio guardado, subtotal e historial")
        void envio() {
            String cuenta = cuentaNueva();
            JsonNode comanda = comandaNueva(cuenta);
            assertThat(comanda.get("estado").asString()).isEqualTo("ENVIADA");
            assertThat(comanda.get("subtotal").asString()).isEqualTo("55.50");
            assertThat(comanda.get("detalles").get(0).get("precioUnitario").asString()).isEqualTo("20.00");
            assertThat(comanda.get("detalles").get(1).get("observaciones").asString()).isEqualTo("Sin azúcar");
            assertThat(sql.queryForList("SELECT estado_anterior, estado_nuevo, id_empleado FROM "
                    + "sivant.historial_estado_comanda")).singleElement().satisfies(h -> {
                assertThat(h.get("estado_anterior")).isNull();
                assertThat(h.get("estado_nuevo")).isEqualTo("ENVIADA");
                assertThat(h.get("id_empleado")).isEqualTo(juan.getId());
            });
            admin.patch("/productos/" + agua, Map.of("precioVenta", "99.00"));
            assertThat(mesero.get("/cuentas/" + cuenta).json().get("total").asString()).isEqualTo("55.50");
            comandaNueva(cuenta);
            assertThat(mesero.get("/cuentas/" + cuenta).json().get("total").asString()).isEqualTo("269.00");
        }

        @Test
        @DisplayName("CP-76 Agrupa el mismo producto y observación; distingue observaciones")
        void agrupa() {
            var r = enviar(mesero, cuentaNueva(), linea(agua, 1, null), linea(agua, 2, "  "), linea(agua, 1, " Fría "));
            assertThat(r.json().get("detalles").size()).isEqualTo(2);
            assertThat(r.json().get("detalles").get(0).get("cantidad").asInt()).isEqualTo(3);
            assertThat(r.json().get("detalles").get(1).get("observaciones").asString()).isEqualTo("Fría");
        }

        @Test
        @DisplayName("CP-77 Rechaza comandas vacías, cantidades inválidas y productos no disponibles")
        void invalidas() {
            String cuenta = cuentaNueva();
            assertThat(enviar(mesero, cuenta).campo("detalles")).isNotBlank();
            for (int cantidad : new int[] {0, -1, 1000}) {
                assertThat(enviar(mesero, cuenta, linea(agua, cantidad, null)).estado()).isEqualTo(400);
            }
            assertThat(enviar(mesero, cuenta, linea(agua, 600, null), linea(agua, 600, null)).campo("detalles")).isNotBlank();
            assertThat(enviar(mesero, cuenta, linea("999999", 1, null)).codigo()).isEqualTo("PRODUCTO_NO_DISPONIBLE");
            admin.post("/productos/" + agua + "/desactivacion", Map.of("confirmado", true));
            assertThat(enviar(mesero, cuenta, linea(agua, 1, null), linea(galleta, 1, null)).codigo())
                    .isEqualTo("PRODUCTO_NO_DISPONIBLE");
            assertThat(sql.queryForObject("SELECT count(*) FROM sivant.comanda", Integer.class)).isZero();
        }

        @Test
        @DisplayName("CP-78 Repetir el envío o la apertura con la misma clave no los duplica")
        void idempotencia() {
            String clave = clave();
            var una = mesero.postConClave("/cuentas", clave, Map.of("mesaId", mesa1));
            var dos = mesero.postConClave("/cuentas", clave, Map.of("mesaId", mesa1));
            assertThat(dos.estado()).isEqualTo(201);
            assertThat(dos.texto()).isEqualTo(una.texto());
            String ruta = "/cuentas/" + una.json().get("id").asString() + "/comandas";
            var cuerpo = Map.of("detalles", List.of(linea(agua, 1, null)));
            var envio = mesero.postConClave(ruta, clave, cuerpo);
            assertThat(mesero.postConClave(ruta, clave, cuerpo).texto()).isEqualTo(envio.texto());
            assertThat(sql.queryForObject("SELECT count(*) FROM sivant.comanda", Integer.class)).isEqualTo(1);
            assertThat(mesero.post(ruta, cuerpo).campo("Idempotency-Key")).isNotBlank();
        }

        @Test
        @DisplayName("CP-79 Otro mesero no consulta ni opera la cuenta")
        void ajena() {
            String cuenta = cuentaNueva();
            JsonNode comanda = comandaNueva(cuenta);
            assertThat(otroMesero.get("/cuentas/" + cuenta).estado()).isEqualTo(404);
            assertThat(enviar(otroMesero, cuenta, linea(agua, 1, null)).estado()).isEqualTo(404);
            assertThat(editar(otroMesero, comanda.get("id").asString(), 0, detalle(comanda.get("detalles").get(0), 5))
                    .estado()).isEqualTo(404);
            assertThat(otroMesero.postConClave("/cuentas/" + cuenta + "/cierre", clave(), Map.of("version", 1)).estado())
                    .isEqualTo(404);
            assertThat(otroMesero.get("/cuentas").json().get("totalElementos").asInt()).isZero();
            assertThat(mesero.get("/cuentas?estado=ABIERTA").json().get("totalElementos").asInt()).isEqualTo(1);
            assertThat(cajero.get("/cuentas/" + cuenta).estado()).isEqualTo(200);
            assertThat(cajero.get("/cuentas?mesaId=" + mesa2).json().get("totalElementos").asInt()).isZero();
        }
    }

    @Nested
    @DisplayName("RF02 · Modificar pedido")
    class Modificar {

        @Test
        @DisplayName("CP-80 Antes de preparar, cambia cantidades o elimina productos y recalcula, sin avisar")
        void directo() {
            String cuenta = cuentaNueva();
            JsonNode comanda = comandaNueva(cuenta);
            String id = comanda.get("id").asString();
            var r = editar(mesero, id, 0, detalle(comanda.get("detalles").get(0), 5));
            assertThat(r.estado()).isEqualTo(200);
            assertThat(r.json().get("total").asString()).isEqualTo("100.00");
            assertThat(r.json().get("comandas").get(0).get("detalles").size()).isEqualTo(1);
            assertThat(r.json().get("comandas").get(0).get("version").asInt()).isEqualTo(1);
            assertThat(avisos(cocinero, "")).isZero();
        }

        @Test
        @DisplayName("CP-81 En preparación, el cambio genera aviso a cocina; sin cambio real, no")
        void enPreparacion() {
            JsonNode comanda = comandaNueva(cuentaNueva());
            String id = comanda.get("id").asString();
            preparar(id, 0);
            JsonNode uno = comanda.get("detalles").get(0);
            JsonNode dos = comanda.get("detalles").get(1);
            editar(mesero, id, 1, detalle(uno, 2), detalle(dos, 1));
            assertThat(avisos(cocinero, "")).isZero();
            assertThat(editar(mesero, id, 1, detalle(uno, 3), detalle(dos, 1)).estado()).isEqualTo(200);
            var aviso = cocinero.get("/notificaciones").json().get("contenido").get(0);
            assertThat(aviso.get("tipo").asString()).isEqualTo("MODIFICACION_COMANDA");
            assertThat(aviso.get("contexto").get("mesa").asInt()).isEqualTo(1);
            assertThat(aviso.get("contexto").get("comandaId").asString()).isEqualTo(id);
        }

        @Test
        @DisplayName("CP-82 No se puede quitar el último producto ni usar detalles ajenos o repetidos")
        void invalida() {
            String cuenta = cuentaNueva();
            JsonNode a = comandaNueva(cuenta);
            JsonNode b = comandaNueva(cuenta);
            String id = a.get("id").asString();
            JsonNode uno = a.get("detalles").get(0);
            assertThat(editar(mesero, id, 0).codigo()).isEqualTo("ULTIMO_DETALLE");
            assertThat(editar(mesero, id, 0, detalle(b.get("detalles").get(0), 1)).campo("detalles")).isNotBlank();
            assertThat(editar(mesero, id, 0, detalle(uno, 1), detalle(uno, 2)).campo("detalles")).isNotBlank();
            assertThat(editar(mesero, id, 0, detalle(uno, 0)).estado()).isEqualTo(400);
            assertThat(editar(mesero, "999999", 0, detalle(uno, 1)).estado()).isEqualTo(404);
            assertThat(mesero.get("/cuentas/" + cuenta).json().get("total").asString()).isEqualTo("111.00");
            assertThatThrownBy(() -> sql.update("DELETE FROM sivant.detalle_comanda WHERE id_comanda = ?",
                    Long.valueOf(id))).isInstanceOf(DataAccessException.class);
        }

        @Test
        @DisplayName("CP-83 Lista o cancelada no se modifica; una versión anterior se rechaza")
        void estados() {
            String cuenta = cuentaNueva();
            JsonNode comanda = comandaNueva(cuenta);
            String id = comanda.get("id").asString();
            JsonNode uno = comanda.get("detalles").get(0);
            preparar(id, 0);
            assertThat(editar(mesero, id, 0, detalle(uno, 9)).codigo()).isEqualTo("VERSION_OBSOLETA");
            lista(id, 1);
            assertThat(editar(mesero, id, 2, detalle(uno, 9)).codigo()).isEqualTo("COMANDA_NO_EDITABLE");
            cancelar(cajero, id, 2, "Error");
            assertThat(editar(mesero, id, 3, detalle(uno, 9)).codigo()).isEqualTo("COMANDA_NO_EDITABLE");
        }

        @Test
        @DisplayName("CP-84 Un producto desactivado se puede ajustar en una comanda existente, con su precio")
        void productoDesactivado() {
            JsonNode comanda = comandaNueva(cuentaNueva());
            admin.post("/productos/" + agua + "/desactivacion", Map.of("confirmado", true));
            var r = editar(mesero, comanda.get("id").asString(), 0, detalle(comanda.get("detalles").get(0), 1));
            assertThat(r.json().get("total").asString()).isEqualTo("20.00");
        }
    }

    @Nested
    @DisplayName("RF03 · Cancelar pedido")
    class Cancelar {

        @ParameterizedTest(name = "CP-85 El cajero cancela una comanda {0}")
        @ValueSource(strings = {"ENVIADA", "EN_PREPARACION", "LISTA"})
        void elegible(String estado) {
            String cuenta = cuentaNueva();
            String id = comandaNueva(cuenta).get("id").asString();
            int version = 0;
            if (!estado.equals("ENVIADA")) {
                preparar(id, version++);
            }
            if (estado.equals("LISTA")) {
                lista(id, version++);
            }
            var r = cancelar(cajero, id, version, " Cliente se retiró ");
            assertThat(r.estado()).as(r.texto()).isEqualTo(200);
            assertThat(r.json().get("estado").asString()).isEqualTo("CANCELADA");
            assertThat(r.json().get("detalles").size()).isEqualTo(2);
            assertThat(r.json().get("cancelacion").get("justificacion").asString()).isEqualTo("Cliente se retiró");
            assertThat(r.json().get("cancelacion").get("cajero").asString()).isEqualTo("María Cajera");
            assertThat(mesero.get("/cuentas/" + cuenta).json().get("total").asString()).isEqualTo("0.00");
            assertThat(cocinero.get("/notificaciones").json().get("contenido").get(0).get("tipo").asString())
                    .isEqualTo("CANCELACION_COMANDA");
            assertThat(cocinero.get("/cocina/cola").texto()).isEqualTo("[]");
        }

        @Test
        @DisplayName("CP-86 Exige justificación, no se repite y solo aplica a comandas sin servir")
        void invalida() {
            String id = comandaNueva(cuentaNueva()).get("id").asString();
            assertThat(cancelar(cajero, id, 0, "  ").campo("justificacion")).isNotBlank();
            assertThat(cancelar(cajero, id, 7, "x").codigo()).isEqualTo("VERSION_OBSOLETA");
            assertThat(cancelar(cajero, "999999", 0, "x").estado()).isEqualTo(404);
            assertThat(cancelar(cajero, id, 0, "x").estado()).isEqualTo(200);
            assertThat(cancelar(cajero, id, 1, "x").codigo()).isEqualTo("COMANDA_NO_CANCELABLE");
            sql.update("UPDATE sivant.comanda SET estado = 'SERVIDA'");
            assertThat(cancelar(cajero, id, 1, "x").codigo()).isEqualTo("COMANDA_NO_CANCELABLE");
        }

        @Test
        @DisplayName("CP-87 Cancelar y marcar lista a la vez: nunca se aplican las dos con la misma versión")
        void simultanea() throws Exception {
            String cuenta = cuentaNueva();
            for (int i = 0; i < 5; i++) {
                String id = comandaNueva(cuenta).get("id").asString();
                preparar(id, 0);
                assertThat(GestionEmpleadosTest.simultaneas(List.of(
                        () -> cancelar(cajero, id, 1, "x").estado(),
                        () -> lista(id, 1).estado()))).containsExactlyInAnyOrder(200, 409);
            }
        }
    }

    @Nested
    @DisplayName("Cierre sin cobro")
    class Cierre {

        private ClienteApi.Respuesta cerrar(String cuenta, int version) {
            return mesero.postConClave("/cuentas/" + cuenta + "/cierre", clave(), Map.of("version", version));
        }

        @Test
        @DisplayName("CP-88 El mesero cierra una cuenta sin comandas vigentes y libera la mesa")
        void sinVigentes() {
            String cuenta = cuentaNueva();
            String id = comandaNueva(cuenta).get("id").asString();
            assertThat(cerrar(cuenta, 1).codigo()).isEqualTo("CUENTA_CON_COMANDAS");
            cancelar(cajero, id, 0, "x");
            assertThat(cerrar(cuenta, 1).codigo()).isEqualTo("VERSION_OBSOLETA");
            var r = cerrar(cuenta, 2);
            assertThat(r.json().get("estado").asString()).isEqualTo("CERRADA");
            assertThat(r.json().get("acciones").size()).isZero();
            assertThat(sql.queryForObject("SELECT count(*) FROM sivant.auditoria WHERE accion = "
                    + "'CUENTA_CERRADA_SIN_COBRO'", Integer.class)).isEqualTo(1);
            assertThat(cerrar(cuenta, 3).codigo()).isEqualTo("CUENTA_CERRADA");
            assertThat(enviar(mesero, cuenta, linea(agua, 1, null)).codigo()).isEqualTo("CUENTA_CERRADA");
            assertThat(abrir(otroMesero, mesa1).estado()).isEqualTo(201);
        }
    }

    @Nested
    @DisplayName("Empleados con cuentas abiertas")
    class ConCuentas {

        @Test
        @DisplayName("CP-89 No se desactiva ni cambia de rol a quien tiene cuentas abiertas; sí después de cerrarlas")
        void responsable() {
            String cuenta = cuentaNueva();
            String ruta = "/empleados/" + juan.getId();
            assertThat(admin.post(ruta + "/desactivacion", Map.of("confirmado", true)).codigo())
                    .isEqualTo("CUENTAS_ABIERTAS");
            assertThat(admin.patch(ruta, Map.of("rol", "CAJERO")).codigo()).isEqualTo("CUENTAS_ABIERTAS");
            assertThat(admin.patch(ruta, Map.of("nombreCompleto", "Juan P.")).estado()).isEqualTo(200);
            assertThat(mesero.get("/cuentas/" + cuenta).estado()).isEqualTo(200);
            mesero.postConClave("/cuentas/" + cuenta + "/cierre", clave(), Map.of("version", 0));
            assertThat(admin.post(ruta + "/desactivacion", Map.of("confirmado", true)).estado()).isEqualTo(200);
        }

        @Test
        @DisplayName("CP-90 Abrir una cuenta y desactivar a la vez: nunca queda un inactivo con cuenta abierta")
        void simultanea() throws Exception {
            for (int i = 0; i < 5; i++) {
                var e = crear("MESERO");
                ClienteApi sesion = sesionDe(e);
                var estados = GestionEmpleadosTest.simultaneas(List.of(
                        () -> abrir(sesion, mesa2).estado(),
                        () -> admin.post("/empleados/" + e.getId() + "/desactivacion", Map.of("confirmado", true))
                                .estado()));
                assertThat(estados).doesNotContain(500);
                assertThat(sql.queryForObject("SELECT count(*) FROM sivant.cuenta c JOIN sivant.empleado e "
                        + "ON e.id_empleado = c.id_mesero WHERE c.estado = 'ABIERTA' AND e.estado = 'INACTIVO'",
                        Integer.class)).isZero();
                sql.update("UPDATE sivant.cuenta SET estado = 'CERRADA', fecha_hora_cierre = now()");
            }
        }
    }

    @ParameterizedTest(name = "CP-91 Permisos de mesas y cuentas para {0}")
    @ValueSource(strings = {"MESERO", "CAJERO", "COCINERO", "ALMACENISTA", "ADMINISTRADOR"})
    void permisos(String rol) {
        String cuenta = cuentaNueva();
        JsonNode comanda = comandaNueva(cuenta);
        String id = comanda.get("id").asString();
        ClienteApi c = rol.equals("MESERO") ? mesero : sesionDe(crear(rol));
        boolean consulta = rol.equals("MESERO") || rol.equals("CAJERO");
        for (String ruta : List.of("/mesas", "/cuentas", "/cuentas/" + cuenta)) {
            assertThat(c.get(ruta).estado()).as(ruta).isEqualTo(consulta ? 200 : 403);
        }
        if (!rol.equals("MESERO")) {
            assertThat(abrir(c, mesa2).estado()).isEqualTo(403);
            assertThat(enviar(c, cuenta, linea(agua, 1, null)).estado()).isEqualTo(403);
            assertThat(editar(c, id, 0, detalle(comanda.get("detalles").get(0), 1)).estado()).isEqualTo(403);
            assertThat(c.postConClave("/cuentas/" + cuenta + "/cierre", clave(), Map.of("version", 1)).estado())
                    .isEqualTo(403);
        }
        assertThat(cancelar(c, id, 0, "x").estado()).isEqualTo(rol.equals("CAJERO") ? 200 : 403);
    }
}
