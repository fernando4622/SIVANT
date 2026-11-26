package mx.sivant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
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

/** RF25 y RF26: el mesero lleva el efectivo a caja al cobrar; el cajero lo recibe y entrega el cambio. */
@DisplayName("Recepción de efectivo y cambio")
class CajaTest extends PruebaServicio {

    // --- Apoyos

    private String abrirEn(ClienteApi cliente, String mesa) {
        var r = abrir(cliente, mesa);
        assertThat(r.estado()).as(r.texto()).isEqualTo(201);
        return r.json().get("id").asString();
    }

    /** Dos aguas servidas: 40.00. */
    private String cuentaServida(ClienteApi cliente, String mesa) {
        String cuenta = abrirEn(cliente, mesa);
        String id = enviar(cliente, cuenta, linea(agua, 2, null)).json().get("id").asString();
        preparar(id, 0);
        lista(id, 1);
        assertThat(cliente.postConClave("/comandas/" + id + "/servicio", clave(), Map.of("version", 2)).estado())
                .isEqualTo(200);
        return cuenta;
    }

    private ClienteApi.Respuesta cobrar(ClienteApi cliente, String cuenta, String metodo, String referencia) {
        JsonNode c = cliente.get("/cuentas/" + cuenta).json();
        Map<String, Object> cuerpo = new HashMap<>(Map.of("versionCuenta", c.get("version").asInt(), "metodo", metodo,
                "totalEsperado", c.get("total").asString()));
        if (referencia != null) {
            cuerpo.put("referenciaExterna", referencia);
        }
        return cliente.postConClave("/cuentas/" + cuenta + "/cobro", clave(), cuerpo);
    }

    /** Pago en efectivo de 40.00 de una cuenta nueva. */
    private String pagoEnEfectivo(ClienteApi cliente, String mesa) {
        var r = cobrar(cliente, cuentaServida(cliente, mesa), "EFECTIVO", null);
        assertThat(r.estado()).as(r.texto()).isEqualTo(201);
        return r.json().get("pago").get("id").asString();
    }

    private static Map<String, Object> recibido(String pago, String monto) {
        return Map.of("pagoId", pago, "montoRecibido", monto);
    }

    private static Map<String, Object> recepcion(Long mesero, Object... pagos) {
        return Map.of("meseroId", mesero.toString(), "pagos", List.of(pagos));
    }

    private ClienteApi.Respuesta recibir(ClienteApi cliente, Map<String, Object> cuerpo) {
        return cliente.postConClave("/caja/entregas-efectivo", clave(), cuerpo);
    }

    private int contar(String tabla) {
        return sql.queryForObject("SELECT count(*) FROM sivant." + tabla, Integer.class);
    }

    /** El cliente de prueba solo interpreta objetos: las listas se leen del texto. */
    private static JsonNode arreglo(ClienteApi.Respuesta r) {
        assertThat(r.estado()).as(r.texto()).isEqualTo(200);
        return new JsonMapper().readTree(r.texto());
    }

    private static List<String> ids(JsonNode arreglo) {
        List<String> ids = new ArrayList<>();
        arreglo.forEach(n -> ids.add(n.get("id").asString()));
        return ids;
    }

    @Nested
    @DisplayName("RF25 y RF26 · Recibir el efectivo y dar el cambio")
    class Recibir {

        @Test
        @DisplayName("CP-152 Recibe el pago con lo que entregó el cliente, registra el cambio y lo saca de pendientes")
        void conCambio() {
            String pago = pagoEnEfectivo(mesero, mesa1);

            var r = recibir(cajero, recepcion(juan.getId(), recibido(pago, "50")));

            assertThat(r.estado()).as(r.texto()).isEqualTo(201);
            assertThat(r.json().get("monto").asString()).isEqualTo("40.00");
            assertThat(r.json().get("cambio").asString()).isEqualTo("10.00");
            assertThat(r.json().get("mesero").asString()).isEqualTo("Juan Pérez");
            assertThat(r.json().get("cajero").asString()).isEqualTo("María Cajera");
            JsonNode recibidoPago = r.json().get("pagos").get(0);
            assertThat(recibidoPago.get("montoRecibido").asString()).isEqualTo("50.00");
            assertThat(recibidoPago.get("cambio").asString()).isEqualTo("10.00");
            assertThat(sql.queryForObject("SELECT monto_recibido || '/' || monto FROM sivant.fondo_cambio", String.class))
                    .isEqualTo("50.00/10.00");
            assertThat(arreglo(cajero.get("/caja/pagos-efectivo-pendientes"))).isEmpty();
            assertThat(sql.queryForObject("SELECT detalle ->> 'cambio' FROM sivant.auditoria WHERE accion = "
                    + "'EFECTIVO_RECIBIDO'", String.class)).isEqualTo("10.00");
        }

        @Test
        @DisplayName("CP-153 Un pago exacto no lleva cambio; varios pagos del mismo mesero se reciben juntos")
        void exactoYVarios() {
            String exacto = pagoEnEfectivo(mesero, mesa1);
            var r = recibir(cajero, recepcion(juan.getId(), recibido(exacto, "40.00")));
            assertThat(r.json().get("cambio").asString()).isEqualTo("0.00");
            assertThat(contar("fondo_cambio")).isZero();

            String uno = pagoEnEfectivo(mesero, mesa1);
            String dos = pagoEnEfectivo(mesero, mesa1);
            var juntos = recibir(cajero, recepcion(juan.getId(), recibido(dos, "100"), recibido(uno, "40")));
            assertThat(juntos.estado()).as(juntos.texto()).isEqualTo(201);
            assertThat(juntos.json().get("monto").asString()).isEqualTo("80.00");
            assertThat(juntos.json().get("cambio").asString()).isEqualTo("60.00");
            assertThat(ids(juntos.json().get("pagos"))).containsExactly(uno, dos);
            assertThat(contar("fondo_cambio")).isEqualTo(1);
        }

        @Test
        @DisplayName("CP-154 Rechaza lo recibido menor al pago, tarjeta, transferencia, otro mesero y datos inválidos")
        void rechazos() {
            String pago = pagoEnEfectivo(mesero, mesa1);
            String tarjeta = cobrar(mesero, cuentaServida(mesero, mesa1), "TARJETA", "T1").json().get("pago").get("id")
                    .asString();
            String transferencia = cobrar(mesero, cuentaServida(mesero, mesa1), "TRANSFERENCIA", null).json().get("pago")
                    .get("id").asString();
            String deAna = pagoEnEfectivo(otroMesero, mesa2);
            Long juanId = juan.getId();

            var corto = recibir(cajero, recepcion(juanId, recibido(pago, "39.99")));
            assertThat(corto.codigo()).isEqualTo("MONTO_INSUFICIENTE");
            assertThat(corto.campo("pagos")).contains("40.00");
            for (String otro : List.of(tarjeta, transferencia)) {
                assertThat(recibir(cajero, recepcion(juanId, recibido(pago, "40"), recibido(otro, "40"))).codigo())
                        .isEqualTo("PAGO_NO_EFECTIVO");
            }
            assertThat(recibir(cajero, recepcion(juanId, recibido(pago, "40"), recibido(deAna, "40"))).codigo())
                    .isEqualTo("PAGO_DE_OTRO_MESERO");
            assertThat(recibir(cajero, recepcion(juanId, recibido("999999", "40"))).campo("pagos")).isNotBlank();
            assertThat(recibir(cajero, recepcion(juanId, recibido(pago, "40"), recibido(pago, "50"))).campo("pagos"))
                    .isNotBlank();
            assertThat(recibir(cajero, recepcion(juanId)).campo("pagos")).isNotBlank();
            for (String monto : List.of("0", "-40", "abc", "40.001")) {
                assertThat(recibir(cajero, recepcion(juanId, recibido(pago, monto))).estado()).as(monto).isEqualTo(400);
            }
            assertThat(cajero.post("/caja/entregas-efectivo", recepcion(juanId, recibido(pago, "40")))
                    .campo("Idempotency-Key")).isNotBlank();
            assertThat(contar("entrega_efectivo")).isZero();
            assertThat(contar("fondo_cambio")).isZero();
        }

        @Test
        @DisplayName("CP-155 Un pago se recibe una sola vez, también con recepciones simultáneas y cruzadas")
        void doble() throws Exception {
            String uno = pagoEnEfectivo(mesero, mesa1);
            String clave = clave();
            var cuerpo = recepcion(juan.getId(), recibido(uno, "50"));
            var primera = cajero.postConClave("/caja/entregas-efectivo", clave, cuerpo);
            assertThat(primera.estado()).isEqualTo(201);
            assertThat(cajero.postConClave("/caja/entregas-efectivo", clave, cuerpo).texto()).isEqualTo(primera.texto());
            assertThat(recibir(cajero, cuerpo).codigo()).isEqualTo("PAGO_ENTREGADO");

            for (int i = 0; i < 3; i++) {
                String a = pagoEnEfectivo(mesero, mesa1);
                String b = pagoEnEfectivo(mesero, mesa1);
                List<String> codigos = Collections.synchronizedList(new ArrayList<>());
                List<Callable<Integer>> tareas = new ArrayList<>();
                for (List<String> pagos : List.of(List.of(a, b), List.of(b, a), List.of(b))) {
                    tareas.add(() -> {
                        var r = recibir(cajero, recepcion(juan.getId(),
                                pagos.stream().map(p -> recibido(p, "40")).toArray()));
                        codigos.add(r.codigo());
                        return r.estado();
                    });
                }
                assertThat(GestionEmpleadosTest.simultaneas(tareas)).containsOnlyOnce(201).containsOnly(201, 409);
                assertThat(codigos).filteredOn(c -> !c.isEmpty()).containsOnly("PAGO_ENTREGADO");
            }
            assertThat(sql.queryForObject("SELECT count(*) - count(DISTINCT id_pago) FROM sivant.entrega_efectivo_pago",
                    Integer.class)).isZero();
        }

        @Test
        @DisplayName("CP-156 Lista los pagos en efectivo por recibir, del más reciente, con las ventas canceladas")
        void pendientes() {
            String uno = pagoEnEfectivo(mesero, mesa1);
            String dos = pagoEnEfectivo(mesero, mesa1);
            cobrar(mesero, cuentaServida(mesero, mesa1), "TARJETA", "T1");
            cobrar(mesero, cuentaServida(mesero, mesa1), "TRANSFERENCIA", null);
            String deAna = pagoEnEfectivo(otroMesero, mesa2);
            String venta = sql.queryForObject("SELECT id_venta FROM sivant.pago WHERE id_pago = ?", Long.class,
                    Long.valueOf(uno)).toString();
            admin.postConClave("/ventas/" + venta + "/cancelacion", clave(), Map.of("confirmado", true,
                    "justificacion", "x"));

            assertThat(ids(arreglo(cajero.get("/caja/pagos-efectivo-pendientes")))).containsExactly(deAna, dos, uno);
            JsonNode deJuan = arreglo(cajero.get("/caja/pagos-efectivo-pendientes?meseroId=" + juan.getId()));
            assertThat(ids(deJuan)).containsExactly(dos, uno);
            assertThat(deJuan.get(1).get("estadoVenta").asString()).isEqualTo("CANCELADA");
            assertThat(deJuan.get(1).get("monto").asString()).isEqualTo("40.00");
        }

        @ParameterizedTest(name = "CP-157 {0} no puede consultar ni recibir efectivo")
        @ValueSource(strings = {"MESERO", "COCINERO", "ALMACENISTA", "ADMINISTRADOR"})
        void permisos(String rol) {
            String pago = pagoEnEfectivo(mesero, mesa1);
            ClienteApi cliente = rol.equals("MESERO") ? mesero : sesionDe(crear(rol));
            assertThat(cliente.get("/caja/pagos-efectivo-pendientes").estado()).isEqualTo(403);
            assertThat(recibir(cliente, recepcion(juan.getId(), recibido(pago, "40"))).estado()).isEqualTo(403);
            assertThat(contar("entrega_efectivo")).isZero();
        }

        @Test
        @DisplayName("CP-158 El fondo previo al cobro ya no existe: sus rutas se rechazan y el cobro no se condiciona")
        void sinFondoPrevio() {
            String cuenta = cuentaServida(mesero, mesa1);
            assertThat(cajero.postConClave("/cuentas/" + cuenta + "/fondo-cambio", clave(), Map.of("monto", "100"))
                    .estado()).isEqualTo(403);
            assertThat(cajero.get("/caja/fondos").estado()).isEqualTo(404);
            assertThat(cajero.get("/cuentas/" + cuenta).json().get("acciones").toString()).doesNotContain("FONDO");
            assertThat(cobrar(mesero, cuenta, "TARJETA", "T9").estado()).isEqualTo(201);
        }

        @Test
        @DisplayName("CP-159 Un fallo al guardar el cambio revierte la recepción; la misma clave puede reintentarse")
        void reversion() {
            String pago = pagoEnEfectivo(mesero, mesa1);
            sql.execute("ALTER TABLE sivant.fondo_cambio ADD CONSTRAINT ck_fallo_simulado CHECK (false) NOT VALID");
            String clave = clave();
            var cuerpo = recepcion(juan.getId(), recibido(pago, "100"));
            try {
                assertThat(cajero.postConClave("/caja/entregas-efectivo", clave, cuerpo).estado()).isEqualTo(500);
                assertThat(contar("entrega_efectivo")).isZero();
                assertThat(arreglo(cajero.get("/caja/pagos-efectivo-pendientes"))).hasSize(1);
            } finally {
                sql.execute("ALTER TABLE sivant.fondo_cambio DROP CONSTRAINT ck_fallo_simulado");
            }
            assertThat(cajero.postConClave("/caja/entregas-efectivo", clave, cuerpo).estado()).isEqualTo(201);
            assertThat(contar("fondo_cambio")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("Integridad en PostgreSQL")
    class Integridad {

        @ParameterizedTest(name = "CP-160 PostgreSQL rechaza: {0}")
        @ValueSource(strings = {
                "UPDATE sivant.entrega_efectivo SET monto = monto + 1",
                "UPDATE sivant.entrega_efectivo SET monto_fondo = 0",
                "UPDATE sivant.fondo_cambio SET monto = monto + 1",
                "UPDATE sivant.fondo_cambio SET monto_recibido = 1",
                "UPDATE sivant.fondo_cambio SET id_entrega = NULL",
                "DELETE FROM sivant.entrega_efectivo_pago",
                "INSERT INTO sivant.entrega_efectivo_pago (id_entrega, id_pago) SELECT id_entrega, id_pago FROM "
                        + "sivant.entrega_efectivo_pago",
                "UPDATE sivant.fondo_cambio SET id_pago = (SELECT id_pago FROM sivant.pago WHERE metodo = 'TARJETA')",
                "INSERT INTO sivant.entrega_efectivo_pago (id_entrega, id_pago) SELECT id_entrega, "
                        + "(SELECT id_pago FROM sivant.pago WHERE metodo = 'TARJETA') FROM sivant.entrega_efectivo"})
        void integridad(String sentencia) {
            String pago = pagoEnEfectivo(mesero, mesa1);
            cobrar(mesero, cuentaServida(mesero, mesa1), "TARJETA", "T1");
            assertThat(recibir(cajero, recepcion(juan.getId(), recibido(pago, "100"))).estado()).isEqualTo(201);
            assertThatThrownBy(() -> sql.update(sentencia)).isInstanceOf(DataAccessException.class);
            assertThat(sql.queryForObject("SELECT monto FROM sivant.fondo_cambio", String.class)).isEqualTo("60.00");
        }
    }
}
