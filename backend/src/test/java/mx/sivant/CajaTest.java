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

@DisplayName("Entrega de efectivo y fondo de cambio")
class CajaTest extends PruebaServicio {

    // --- Apoyos

    private String abrirEn(ClienteApi cliente, String mesa) {
        var r = abrir(cliente, mesa);
        assertThat(r.estado()).as(r.texto()).isEqualTo(201);
        return r.json().get("id").asString();
    }

    private String servida(ClienteApi cliente, String cuenta, Object... lineas) {
        String id = enviar(cliente, cuenta, lineas).json().get("id").asString();
        preparar(id, 0);
        lista(id, 1);
        assertThat(cliente.postConClave("/comandas/" + id + "/servicio", clave(), Map.of("version", 2)).estado())
                .isEqualTo(200);
        return id;
    }

    /** Dos aguas servidas: 40.00. */
    private String cuentaServida(ClienteApi cliente, String mesa) {
        String cuenta = abrirEn(cliente, mesa);
        servida(cliente, cuenta, linea(agua, 2, null));
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

    private ClienteApi.Respuesta entregarFondo(ClienteApi cliente, String cuenta, String monto) {
        return cliente.postConClave("/cuentas/" + cuenta + "/fondo-cambio", clave(), Map.of("monto", monto));
    }

    private ClienteApi.Respuesta anularFondo(ClienteApi cliente, String cuenta, String justificacion) {
        return cliente.postConClave("/cuentas/" + cuenta + "/fondo-cambio/anulacion", clave(),
                Map.of("justificacion", justificacion));
    }

    private Map<String, Object> recepcion(Long mesero, List<String> pagos, String monto, String fondo) {
        Map<String, Object> cuerpo = new HashMap<>(Map.of("meseroId", mesero.toString(), "pagoIds", pagos,
                "montoRecibido", monto));
        if (fondo != null) {
            cuerpo.put("fondoRecibido", fondo);
        }
        return cuerpo;
    }

    private ClienteApi.Respuesta recibir(ClienteApi cliente, Map<String, Object> cuerpo) {
        return cliente.postConClave("/caja/entregas-efectivo", clave(), cuerpo);
    }

    private Long ana() {
        return sql.queryForObject("SELECT id_empleado FROM sivant.empleado WHERE correo = 'ana@sivant.test'", Long.class);
    }

    private int contar(String tabla) {
        return sql.queryForObject("SELECT count(*) FROM sivant." + tabla, Integer.class);
    }

    private String estadoFondo() {
        return sql.queryForObject("SELECT string_agg(estado, ',' ORDER BY id_fondo_cambio) FROM sivant.fondo_cambio",
                String.class);
    }

    /** El cliente de prueba solo interpreta objetos: las listas se leen del texto. */
    private static JsonNode arreglo(ClienteApi.Respuesta r) {
        assertThat(r.estado()).as(r.texto()).isEqualTo(200);
        return new tools.jackson.databind.json.JsonMapper().readTree(r.texto());
    }

    private static List<String> ids(JsonNode arreglo) {
        List<String> ids = new ArrayList<>();
        arreglo.forEach(n -> ids.add(n.get("id").asString()));
        return ids;
    }

    @Nested
    @DisplayName("RF26 · Entregar fondo de cambio")
    class FondoDeCambio {

        @Test
        @DisplayName("CP-152 El cajero entrega un fondo pendiente al mesero de la cuenta; el mesero lo consulta")
        void entregar() {
            String cuenta = cuentaServida(mesero, mesa1);
            assertThat(cajero.get("/cuentas/" + cuenta).json().get("acciones").toString()).contains("ENTREGAR_FONDO");

            var r = entregarFondo(cajero, cuenta, "200");

            assertThat(r.estado()).as(r.texto()).isEqualTo(201);
            assertThat(r.json().get("estado").asString()).isEqualTo("PENDIENTE");
            assertThat(r.json().get("monto").asString()).isEqualTo("200.00");
            assertThat(r.json().get("mesero").asString()).isEqualTo("Juan Pérez");
            assertThat(r.json().get("cajero").asString()).isEqualTo("María Cajera");
            JsonNode vista = cajero.get("/cuentas/" + cuenta).json();
            assertThat(vista.get("fondo").get("estado").asString()).isEqualTo("PENDIENTE");
            assertThat(vista.get("acciones").toString()).contains("ANULAR_FONDO").doesNotContain("ENTREGAR_FONDO");
            var propios = mesero.get("/cuentas/" + cuenta + "/fondo-cambio");
            assertThat(propios.estado()).isEqualTo(200);
            assertThat(propios.texto()).contains("\"monto\":\"200.00\"");
            assertThat(mesero.get("/cuentas/" + cuenta).json().get("fondo").get("monto").asString()).isEqualTo("200.00");
            assertThat(otroMesero.get("/cuentas/" + cuenta + "/fondo-cambio").estado()).isEqualTo(404);
            assertThat(sql.queryForObject("SELECT detalle ->> 'monto' FROM sivant.auditoria WHERE accion = "
                    + "'FONDO_ENTREGADO'", String.class)).isEqualTo("200");
        }

        @Test
        @DisplayName("CP-153 Solo en cuentas abiertas con consumo, uno vigente a la vez, con importe válido")
        void rechazos() throws Exception {
            String vacia = abrirEn(mesero, mesa1);
            assertThat(entregarFondo(cajero, vacia, "100").codigo()).isEqualTo("CUENTA_SIN_COMANDAS");
            String unica = enviar(mesero, vacia, linea(agua, 1, null)).json().get("id").asString();
            cancelar(cajero, unica, 0, "x");
            assertThat(entregarFondo(cajero, vacia, "100").codigo()).isEqualTo("CUENTA_SIN_COMANDAS");
            mesero.postConClave("/cuentas/" + vacia + "/cierre", clave(), Map.of("version", 2));
            assertThat(entregarFondo(cajero, vacia, "100").codigo()).isEqualTo("CUENTA_CERRADA");
            assertThat(entregarFondo(cajero, "999999", "100").estado()).isEqualTo(404);

            String cuenta = cuentaServida(mesero, mesa1);
            for (String monto : List.of("0", "-1", "abc", "1.005")) {
                assertThat(entregarFondo(cajero, cuenta, monto).campo("monto")).as(monto).isNotBlank();
            }
            assertThat(cajero.post("/cuentas/" + cuenta + "/fondo-cambio", Map.of("monto", "100"))
                    .campo("Idempotency-Key")).isNotBlank();
            assertThat(GestionEmpleadosTest.simultaneas(List.of(
                    () -> entregarFondo(cajero, cuenta, "100").estado(),
                    () -> entregarFondo(cajero, cuenta, "150").estado()))).containsExactlyInAnyOrder(201, 409);
            assertThat(entregarFondo(cajero, cuenta, "100").codigo()).isEqualTo("FONDO_VIGENTE");
            assertThat(contar("fondo_cambio")).isEqualTo(1);
        }

        @ParameterizedTest(name = "CP-154 {0} no puede entregar ni anular un fondo")
        @ValueSource(strings = {"MESERO", "COCINERO", "ALMACENISTA", "ADMINISTRADOR"})
        void permisos(String rol) {
            String cuenta = cuentaServida(mesero, mesa1);
            ClienteApi cliente = rol.equals("MESERO") ? mesero : sesionDe(crear(rol));
            assertThat(entregarFondo(cliente, cuenta, "100").estado()).isEqualTo(403);
            assertThat(anularFondo(cliente, cuenta, "x").estado()).isEqualTo(403);
            if (!rol.equals("MESERO")) {
                assertThat(cliente.get("/cuentas/" + cuenta + "/fondo-cambio").estado()).isEqualTo(403);
            }
            assertThat(contar("fondo_cambio")).isZero();
        }

        @Test
        @DisplayName("CP-155 Con un fondo pendiente solo se cobra en efectivo, y el fondo queda aplicado a ese pago")
        void cobro() {
            String cuenta = cuentaServida(mesero, mesa1);
            entregarFondo(cajero, cuenta, "200");
            assertThat(cobrar(mesero, cuenta, "TARJETA", "A1").codigo()).isEqualTo("FONDO_PENDIENTE");
            assertThat(cobrar(mesero, cuenta, "TRANSFERENCIA", null).campo("metodo")).isNotBlank();
            var r = cobrar(mesero, cuenta, "EFECTIVO", null);
            assertThat(r.estado()).as(r.texto()).isEqualTo(201);
            assertThat(r.json().get("cuenta").get("fondo").get("estado").asString()).isEqualTo("APLICADO");
            JsonNode fondo = arreglo(cajero.get("/cuentas/" + cuenta + "/fondo-cambio")).get(0);
            assertThat(fondo.get("estado").asString()).isEqualTo("APLICADO");
            assertThat(fondo.get("ventaId").asString()).isEqualTo(r.json().get("venta").get("id").asString());
            assertThat(sql.queryForObject("SELECT id_pago FROM sivant.fondo_cambio", Long.class).toString())
                    .isEqualTo(r.json().get("pago").get("id").asString());
            assertThat(sql.queryForObject("SELECT detalle ->> 'fondo' FROM sivant.auditoria WHERE accion = "
                    + "'VENTA_COBRADA'", String.class)).isEqualTo(fondo.get("id").asString());
            assertThat(anularFondo(cajero, cuenta, "x").codigo()).as("aplicado no se anula")
                    .isEqualTo("SIN_FONDO_PENDIENTE");
        }

        @Test
        @DisplayName("CP-156 Anular un fondo devuelto antes del cobro lo conserva y permite entregar otro")
        void anular() {
            String cuenta = cuentaServida(mesero, mesa1);
            assertThat(anularFondo(cajero, cuenta, "x").codigo()).isEqualTo("SIN_FONDO_PENDIENTE");
            entregarFondo(cajero, cuenta, "200");
            assertThat(anularFondo(cajero, cuenta, "   ").campo("justificacion")).isNotBlank();
            var r = anularFondo(cajero, cuenta, " Lo regresó completo ");
            assertThat(r.estado()).as(r.texto()).isEqualTo(200);
            assertThat(r.json().get("estado").asString()).isEqualTo("ANULADO");
            assertThat(r.json().get("anulacion").get("cajero").asString()).isEqualTo("María Cajera");
            assertThat(r.json().get("anulacion").get("justificacion").asString()).isEqualTo("Lo regresó completo");
            assertThat(cajero.get("/cuentas/" + cuenta).json().has("fondo")).isFalse();
            assertThat(entregarFondo(cajero, cuenta, "100").estado()).isEqualTo(201);
            assertThat(estadoFondo()).isEqualTo("ANULADO,PENDIENTE");
            assertThat(arreglo(cajero.get("/cuentas/" + cuenta + "/fondo-cambio")).size()).isEqualTo(2);
            assertThat(cobrar(mesero, cuenta, "EFECTIVO", null).estado()).isEqualTo(201);
            assertThat(estadoFondo()).isEqualTo("ANULADO,APLICADO");
        }

        @Test
        @DisplayName("CP-157 Una cuenta con fondo pendiente no se cierra sin cobro hasta anularlo")
        void cierreSinCobro() {
            String cuenta = abrirEn(mesero, mesa1);
            String comanda = enviar(mesero, cuenta, linea(agua, 1, null)).json().get("id").asString();
            entregarFondo(cajero, cuenta, "50");
            cancelar(cajero, comanda, 0, "Ya no la quiere");
            JsonNode c = mesero.get("/cuentas/" + cuenta).json();
            assertThat(c.get("acciones").toString()).doesNotContain("CERRAR_SIN_COBRO");
            var r = mesero.postConClave("/cuentas/" + cuenta + "/cierre", clave(), Map.of("version", c.get("version")
                    .asInt()));
            assertThat(r.codigo()).isEqualTo("FONDO_PENDIENTE");
            assertThat(mesero.get("/cuentas/" + cuenta).json().get("estado").asString()).isEqualTo("ABIERTA");
            assertThat(cajero.get("/cuentas/" + cuenta).json().get("acciones").toString()).contains("ANULAR_FONDO");
            anularFondo(cajero, cuenta, "Lo regresó");
            assertThat(mesero.postConClave("/cuentas/" + cuenta + "/cierre", clave(), Map.of("version", c.get("version")
                    .asInt())).estado()).isEqualTo(200);
        }

        @Test
        @DisplayName("CP-158 Cobrar y anular el fondo a la vez deja un estado coherente")
        void cobroYAnulacion() throws Exception {
            for (int i = 0; i < 4; i++) {
                String cuenta = cuentaServida(mesero, mesa1);
                entregarFondo(cajero, cuenta, "100");
                List<Integer> estados = GestionEmpleadosTest.simultaneas(List.of(
                        () -> cobrar(mesero, cuenta, "EFECTIVO", null).estado(),
                        () -> anularFondo(cajero, cuenta, "Lo regresó").estado()));
                assertThat(estados.getFirst()).isEqualTo(201);
                String estado = sql.queryForObject("SELECT estado FROM sivant.fondo_cambio WHERE id_cuenta = ?",
                        String.class, Long.valueOf(cuenta));
                assertThat(estado).isEqualTo(estados.get(1) == 200 ? "ANULADO" : "APLICADO");
                assertThat(sql.queryForObject("SELECT count(*) FROM sivant.fondo_cambio WHERE id_cuenta = ? AND "
                        + "(estado = 'APLICADO') = (id_pago IS NOT NULL)", Integer.class, Long.valueOf(cuenta)))
                        .isEqualTo(1);
            }
        }

        @Test
        @DisplayName("CP-159 El cajero consulta los fondos por estado, con su venta, entrega y anulación")
        void listado() {
            String anulada = cuentaServida(mesero, mesa1);
            entregarFondo(cajero, anulada, "10");
            anularFondo(cajero, anulada, "Lo regresó");
            String pendiente = cuentaServida(otroMesero, mesa2);
            entregarFondo(cajero, pendiente, "20");
            entregarFondo(cajero, anulada, "30");
            cobrar(mesero, anulada, "EFECTIVO", null);
            assertThat(cajero.get("/caja/fondos").json().get("totalElementos").asInt()).isEqualTo(3);
            JsonNode anulados = cajero.get("/caja/fondos?estado=ANULADO").json().get("contenido");
            assertThat(anulados.size()).isEqualTo(1);
            assertThat(anulados.get(0).get("anulacion").get("justificacion").asString()).isEqualTo("Lo regresó");
            assertThat(cajero.get("/caja/fondos?estado=PENDIENTE").json().get("contenido").get(0).get("mesero")
                    .asString()).isEqualTo("Ana Mesera");
            JsonNode aplicado = cajero.get("/caja/fondos?estado=APLICADO").json().get("contenido").get(0);
            assertThat(aplicado.has("ventaId")).isTrue();
            assertThat(aplicado.has("entregaId")).isFalse();
            assertThat(cajero.get("/caja/fondos?estado=OTRO").estado()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("RF25 · Registrar entrega de efectivo")
    class Entrega {

        @Test
        @DisplayName("CP-160 Lista los pagos en efectivo pendientes por mesero, con su fondo y ventas canceladas")
        void pendientes() {
            String uno = pagoEnEfectivo(mesero, mesa1);
            String cuenta = cuentaServida(mesero, mesa1);
            entregarFondo(cajero, cuenta, "100");
            String dos = cobrar(mesero, cuenta, "EFECTIVO", null).json().get("pago").get("id").asString();
            cobrar(mesero, cuentaServida(mesero, mesa1), "TARJETA", "T1");
            cobrar(mesero, cuentaServida(mesero, mesa1), "TRANSFERENCIA", null);
            String deAna = pagoEnEfectivo(otroMesero, mesa2);
            String venta = sql.queryForObject("SELECT id_venta FROM sivant.pago WHERE id_pago = ?", Long.class,
                    Long.valueOf(uno)).toString();
            admin.postConClave("/ventas/" + venta + "/cancelacion", clave(), Map.of("confirmado", true,
                    "justificacion", "x"));

            JsonNode todos = arreglo(cajero.get("/caja/pagos-efectivo-pendientes"));
            assertThat(ids(todos)).containsExactly(deAna, uno, dos);
            JsonNode deJuan = arreglo(cajero.get("/caja/pagos-efectivo-pendientes?meseroId=" + juan.getId()));
            assertThat(ids(deJuan)).containsExactly(uno, dos);
            assertThat(deJuan.get(0).get("estadoVenta").asString()).isEqualTo("CANCELADA");
            assertThat(deJuan.get(0).get("monto").asString()).isEqualTo("40.00");
            assertThat(deJuan.get(0).has("fondo")).isFalse();
            assertThat(deJuan.get(1).get("fondo").asString()).isEqualTo("100.00");
        }

        @Test
        @DisplayName("CP-161 Recibe los pagos y el fondo por separado, liquida el fondo y los saca de pendientes")
        void recepcionCompleta() {
            String uno = pagoEnEfectivo(mesero, mesa1);
            String cuenta = cuentaServida(mesero, mesa1);
            entregarFondo(cajero, cuenta, "100");
            String dos = cobrar(mesero, cuenta, "EFECTIVO", null).json().get("pago").get("id").asString();

            var r = recibir(cajero, recepcion(juan.getId(), List.of(dos, uno), "80", "100"));

            assertThat(r.estado()).as(r.texto()).isEqualTo(201);
            assertThat(r.json().get("monto").asString()).isEqualTo("80.00");
            assertThat(r.json().get("montoFondo").asString()).isEqualTo("100.00");
            assertThat(r.json().get("mesero").asString()).isEqualTo("Juan Pérez");
            assertThat(r.json().get("cajero").asString()).isEqualTo("María Cajera");
            assertThat(ids(r.json().get("pagos"))).containsExactly(uno, dos);
            JsonNode fondo = arreglo(cajero.get("/cuentas/" + cuenta + "/fondo-cambio")).get(0);
            assertThat(fondo.get("estado").asString()).isEqualTo("LIQUIDADO");
            assertThat(fondo.get("entregaId").asString()).isEqualTo(r.json().get("id").asString());
            assertThat(cajero.get("/caja/pagos-efectivo-pendientes").texto()).isEqualTo("[]");
            assertThat(sql.queryForObject("SELECT detalle ->> 'fondo' FROM sivant.auditoria WHERE accion = "
                    + "'EFECTIVO_RECIBIDO'", String.class)).isEqualTo("100.00");
        }

        @Test
        @DisplayName("CP-162 Rechaza importes distintos, tarjeta, transferencia, otro mesero y pagos inválidos")
        void rechazos() {
            String cuenta = cuentaServida(mesero, mesa1);
            entregarFondo(cajero, cuenta, "100");
            String pago = cobrar(mesero, cuenta, "EFECTIVO", null).json().get("pago").get("id").asString();
            String tarjeta = cobrar(mesero, cuentaServida(mesero, mesa1), "TARJETA", "T1").json().get("pago").get("id")
                    .asString();
            String transferencia = cobrar(mesero, cuentaServida(mesero, mesa1), "TRANSFERENCIA", null).json().get("pago")
                    .get("id").asString();
            String deAna = pagoEnEfectivo(otroMesero, mesa2);
            Long juanId = juan.getId();

            var distinto = recibir(cajero, recepcion(juanId, List.of(pago), "39.99", "100"));
            assertThat(distinto.codigo()).isEqualTo("MONTO_DISTINTO");
            assertThat(distinto.campo("montoRecibido")).contains("40.00");
            assertThat(recibir(cajero, recepcion(juanId, List.of(pago), "40", null)).codigo()).isEqualTo("FONDO_DISTINTO");
            assertThat(recibir(cajero, recepcion(juanId, List.of(pago), "40", "99")).campo("fondoRecibido"))
                    .contains("100.00");
            for (String otro : List.of(tarjeta, transferencia)) {
                assertThat(recibir(cajero, recepcion(juanId, List.of(pago, otro), "80", "100")).codigo())
                        .isEqualTo("PAGO_NO_EFECTIVO");
            }
            assertThat(recibir(cajero, recepcion(juanId, List.of(pago, deAna), "80", "100")).codigo())
                    .isEqualTo("PAGO_DE_OTRO_MESERO");
            assertThat(recibir(cajero, recepcion(juanId, List.of(pago, "999999"), "40", "100")).campo("pagoIds"))
                    .isNotBlank();
            assertThat(recibir(cajero, recepcion(juanId, List.of(pago, pago), "80", "100")).campo("pagoIds"))
                    .isNotBlank();
            assertThat(recibir(cajero, recepcion(juanId, List.of(), "0", null)).campo("pagoIds")).isNotBlank();
            assertThat(recibir(cajero, recepcion(juanId, List.of(pago), "-40", "100")).campo("montoRecibido"))
                    .isNotBlank();
            assertThat(cajero.post("/caja/entregas-efectivo", recepcion(juanId, List.of(pago), "40", "100"))
                    .campo("Idempotency-Key")).isNotBlank();
            assertThat(contar("entrega_efectivo")).isZero();
            assertThat(estadoFondo()).isEqualTo("APLICADO");
        }

        @Test
        @DisplayName("CP-163 Un pago figura en una sola entrega, también con entregas simultáneas y cruzadas")
        void doble() throws Exception {
            String uno = pagoEnEfectivo(mesero, mesa1);
            String dos = pagoEnEfectivo(mesero, mesa1);
            String clave = clave();
            var cuerpo = recepcion(juan.getId(), List.of(uno), "40", null);
            var primera = cajero.postConClave("/caja/entregas-efectivo", clave, cuerpo);
            assertThat(primera.estado()).isEqualTo(201);
            assertThat(cajero.postConClave("/caja/entregas-efectivo", clave, cuerpo).texto()).isEqualTo(primera.texto());
            assertThat(recibir(cajero, cuerpo).codigo()).isEqualTo("PAGO_ENTREGADO");
            assertThat(recibir(cajero, recepcion(juan.getId(), List.of(dos, uno), "80", null)).codigo())
                    .isEqualTo("PAGO_ENTREGADO");

            for (int i = 0; i < 3; i++) {
                String a = pagoEnEfectivo(mesero, mesa1);
                String b = pagoEnEfectivo(mesero, mesa1);
                List<String> codigos = Collections.synchronizedList(new ArrayList<>());
                List<Callable<Integer>> tareas = new ArrayList<>();
                for (List<String> pagos : List.of(List.of(a, b), List.of(b, a), List.of(b))) {
                    tareas.add(() -> {
                        var r = recibir(cajero, recepcion(juan.getId(), pagos, pagos.size() == 2 ? "80" : "40", null));
                        codigos.add(r.codigo());
                        return r.estado();
                    });
                }
                List<Integer> estados = GestionEmpleadosTest.simultaneas(tareas);
                assertThat(estados).containsOnlyOnce(201).containsOnly(201, 409);
                assertThat(codigos).filteredOn(c -> !c.isEmpty()).containsOnly("PAGO_ENTREGADO");
            }
            assertThat(sql.queryForObject("SELECT count(*) FROM sivant.entrega_efectivo_pago", Integer.class))
                    .isEqualTo(sql.queryForObject("SELECT count(DISTINCT id_pago) FROM sivant.entrega_efectivo_pago",
                            Integer.class));
        }

        @ParameterizedTest(name = "CP-164 {0} no puede consultar ni registrar entregas")
        @ValueSource(strings = {"MESERO", "COCINERO", "ALMACENISTA", "ADMINISTRADOR"})
        void permisos(String rol) {
            String pago = pagoEnEfectivo(mesero, mesa1);
            ClienteApi cliente = rol.equals("MESERO") ? mesero : sesionDe(crear(rol));
            assertThat(cliente.get("/caja/pagos-efectivo-pendientes").estado()).isEqualTo(403);
            assertThat(cliente.get("/caja/fondos").estado()).isEqualTo(403);
            assertThat(recibir(cliente, recepcion(juan.getId(), List.of(pago), "40", null)).estado()).isEqualTo(403);
            assertThat(contar("entrega_efectivo")).isZero();
        }

        @Test
        @DisplayName("CP-165 Un fallo al guardar la entrega revierte la liquidación; la misma clave puede reintentarse")
        void reversion() {
            String cuenta = cuentaServida(mesero, mesa1);
            entregarFondo(cajero, cuenta, "100");
            String pago = cobrar(mesero, cuenta, "EFECTIVO", null).json().get("pago").get("id").asString();
            sql.execute("ALTER TABLE sivant.entrega_efectivo_pago ADD CONSTRAINT ck_fallo_simulado CHECK (false) "
                    + "NOT VALID");
            String clave = clave();
            var cuerpo = recepcion(juan.getId(), List.of(pago), "40", "100");
            try {
                assertThat(cajero.postConClave("/caja/entregas-efectivo", clave, cuerpo).estado()).isEqualTo(500);
                assertThat(contar("entrega_efectivo")).isZero();
                assertThat(estadoFondo()).isEqualTo("APLICADO");
            } finally {
                sql.execute("ALTER TABLE sivant.entrega_efectivo_pago DROP CONSTRAINT ck_fallo_simulado");
            }
            assertThat(cajero.postConClave("/caja/entregas-efectivo", clave, cuerpo).estado()).isEqualTo(201);
            assertThat(estadoFondo()).isEqualTo("LIQUIDADO");
        }
    }

    @Nested
    @DisplayName("Integridad en PostgreSQL")
    class Integridad {

        @ParameterizedTest(name = "CP-166 PostgreSQL rechaza: {0}")
        @ValueSource(strings = {
                "UPDATE sivant.entrega_efectivo SET monto = monto + 1",
                "UPDATE sivant.entrega_efectivo SET monto_fondo = 0",
                "DELETE FROM sivant.entrega_efectivo_pago",
                "INSERT INTO sivant.entrega_efectivo_pago (id_entrega, id_pago) SELECT id_entrega, id_pago FROM "
                        + "sivant.entrega_efectivo_pago",
                "UPDATE sivant.fondo_cambio SET estado = 'PENDIENTE', id_pago = NULL, id_entrega = NULL",
                "UPDATE sivant.fondo_cambio SET id_entrega = NULL",
                "UPDATE sivant.fondo_cambio SET estado = 'ANULADO'",
                "INSERT INTO sivant.fondo_cambio (id_cuenta, id_cajero, id_mesero, monto, fecha_hora) "
                        + "SELECT id_cuenta, id_cajero, id_mesero, 5, now() FROM sivant.fondo_cambio",
                "UPDATE sivant.fondo_cambio SET id_pago = (SELECT id_pago FROM sivant.pago WHERE metodo = 'TARJETA')",
                "INSERT INTO sivant.entrega_efectivo_pago (id_entrega, id_pago) SELECT id_entrega, "
                        + "(SELECT id_pago FROM sivant.pago WHERE metodo = 'TARJETA') FROM sivant.entrega_efectivo"})
        void integridad(String sentencia) {
            String cuenta = cuentaServida(mesero, mesa1);
            entregarFondo(cajero, cuenta, "100");
            String pago = cobrar(mesero, cuenta, "EFECTIVO", null).json().get("pago").get("id").asString();
            cobrar(mesero, cuentaServida(mesero, mesa1), "TARJETA", "T1");
            assertThat(recibir(cajero, recepcion(juan.getId(), List.of(pago), "40", "100")).estado()).isEqualTo(201);
            assertThatThrownBy(() -> sql.update(sentencia)).isInstanceOf(DataAccessException.class);
            assertThat(estadoFondo()).isEqualTo("LIQUIDADO");
        }
    }
}
