package mx.sivant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;
import tools.jackson.databind.JsonNode;

@DisplayName("Cobro, historial y cancelación de ventas")
class VentasTest extends PruebaServicio {

    static final ZoneId ZONA = ZoneId.of("America/Mexico_City");
    /** Número de prueba publicado que cumple Luhn. */
    static final String NUMERO_TARJETA = "4111-1111-1111-1111";

    @AfterEach
    void retirarFallos() {
        for (String tabla : List.of("pago", "cancelacion_venta")) {
            sql.execute("ALTER TABLE sivant." + tabla + " DROP CONSTRAINT IF EXISTS ck_fallo_simulado");
        }
    }

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

    /** Dos aguas y una galleta servidas: 55.50. */
    private String cuentaServida(ClienteApi cliente, String mesa) {
        String cuenta = abrirEn(cliente, mesa);
        servida(cliente, cuenta, linea(agua, 2, null), linea(galleta, 1, null));
        return cuenta;
    }

    private static Map<String, Object> pago(int version, String metodo, String referencia, String total) {
        Map<String, Object> cuerpo = new HashMap<>(Map.of("versionCuenta", version, "metodo", metodo,
                "totalEsperado", total));
        if (referencia != null) {
            cuerpo.put("referenciaExterna", referencia);
        }
        return cuerpo;
    }

    private ClienteApi.Respuesta cobrar(ClienteApi cliente, String cuenta, Map<String, Object> cuerpo) {
        return cliente.postConClave("/cuentas/" + cuenta + "/cobro", clave(), cuerpo);
    }

    private ClienteApi.Respuesta cobrar(String cuenta, String metodo, String referencia) {
        return cobrar(mesero, cuenta, pago(1, metodo, referencia, "55.50"));
    }

    private String venta(ClienteApi cliente, String mesa, String metodo, String referencia) {
        var r = cobrar(cliente, cuentaServida(cliente, mesa), pago(1, metodo, referencia, "55.50"));
        assertThat(r.estado()).as(r.texto()).isEqualTo(201);
        return r.json().get("venta").get("id").asString();
    }

    private String venta(String metodo, String referencia) {
        return venta(mesero, mesa1, metodo, referencia);
    }

    private static Map<String, Object> devolucion(String referencia) {
        Map<String, Object> cuerpo = new HashMap<>(Map.of("confirmado", true, "justificacion", " Cobro equivocado "));
        if (referencia != null) {
            cuerpo.put("referenciaReversion", referencia);
        }
        return cuerpo;
    }

    private ClienteApi.Respuesta cancelarVenta(ClienteApi cliente, String venta, Map<String, Object> cuerpo) {
        return cliente.postConClave("/ventas/" + venta + "/cancelacion", clave(), cuerpo);
    }

    private int contar(String tabla) {
        return sql.queryForObject("SELECT count(*) FROM sivant." + tabla, Integer.class);
    }

    private void sinCobrar(String cuenta) {
        JsonNode c = mesero.get("/cuentas/" + cuenta).json();
        assertThat(c.get("estado").asString()).isEqualTo("ABIERTA");
        assertThat(c.has("cobro")).isFalse();
        assertThat(contar("venta")).isZero();
        assertThat(contar("pago")).isZero();
    }

    private static String acciones(JsonNode recurso) {
        return recurso.get("acciones").toString();
    }

    @Nested
    @DisplayName("RF04 · Cobrar cuenta")
    class Cobrar {

        @Test
        @DisplayName("CP-122 Cobra las comandas no canceladas con sus precios, cierra la cuenta y libera la mesa")
        void efectivo() {
            String cuenta = abrirEn(mesero, mesa1);
            servida(mesero, cuenta, linea(agua, 2, null), linea(galleta, 1, null));
            String otra = enviar(mesero, cuenta, linea(galleta, 4, null)).json().get("id").asString();
            assertThat(cancelar(cajero, otra, 0, "Se equivocó").estado()).isEqualTo(200);
            admin.patch("/productos/" + agua, Map.of("precioVenta", "99.00"));
            JsonNode antes = mesero.get("/cuentas/" + cuenta).json();
            assertThat(acciones(antes)).contains("COBRAR");

            var r = cobrar(mesero, cuenta, pago(antes.get("version").asInt(), "EFECTIVO", null, "55.50"));

            assertThat(r.estado()).as(r.texto()).isEqualTo(201);
            assertThat(r.json().get("venta").get("estado").asString()).isEqualTo("COMPLETADA");
            assertThat(r.json().get("venta").get("total").asString()).isEqualTo("55.50");
            assertThat(r.json().get("pago").get("monto").asString()).isEqualTo("55.50");
            assertThat(r.json().get("pago").get("metodo").asString()).isEqualTo("EFECTIVO");
            assertThat(r.json().get("pago").has("referenciaExterna")).isFalse();
            JsonNode c = r.json().get("cuenta");
            assertThat(c.get("estado").asString()).isEqualTo("CERRADA");
            assertThat(c.get("acciones").size()).isZero();
            assertThat(c.get("cobro").get("total").asString()).isEqualTo("55.50");
            assertThat(c.get("fechaHoraCierre").asString()).isEqualTo(r.json().get("venta").get("fechaHora").asString());
            assertThat(sql.queryForObject("SELECT id_mesero FROM sivant.pago", Long.class)).isEqualTo(juan.getId());
            assertThat(sql.queryForObject("SELECT detalle ->> 'total' FROM sivant.auditoria WHERE accion = "
                    + "'VENTA_COBRADA' AND id_empleado = ?", String.class, juan.getId())).isEqualTo("55.50");
            assertThat(abrir(otroMesero, mesa1).estado()).as("la mesa quedó libre").isEqualTo(201);
        }

        @Test
        @DisplayName("CP-123 No se cobra con comandas por servir ni sin comandas vigentes")
        void pendientes() {
            String cuenta = abrirEn(mesero, mesa1);
            assertThat(cobrar(mesero, cuenta, pago(0, "EFECTIVO", null, "0.00")).codigo())
                    .isEqualTo("CUENTA_SIN_COMANDAS");
            servida(mesero, cuenta, linea(agua, 1, null));
            String id = enviar(mesero, cuenta, linea(agua, 1, null)).json().get("id").asString();
            for (int version = 0; version <= 2; version++) {
                assertThat(cobrar(mesero, cuenta, pago(2, "EFECTIVO", null, "40.00")).codigo())
                        .isEqualTo("COMANDAS_PENDIENTES");
                assertThat(acciones(mesero.get("/cuentas/" + cuenta).json())).doesNotContain("COBRAR");
                if (version == 0) {
                    preparar(id, 0);
                } else if (version == 1) {
                    lista(id, 1);
                }
            }
            sinCobrar(cuenta);
            assertThat(cancelar(cajero, id, 2, "Ya no la quiere").estado()).isEqualTo(200);
            assertThat(cobrar(mesero, cuenta, pago(3, "EFECTIVO", null, "20.00")).estado()).isEqualTo(201);

            String cancelada = abrirEn(mesero, mesa2);
            String unica = enviar(mesero, cancelada, linea(agua, 1, null)).json().get("id").asString();
            cancelar(cajero, unica, 0, "x");
            assertThat(cobrar(mesero, cancelada, pago(2, "EFECTIVO", null, "0.00")).codigo())
                    .isEqualTo("CUENTA_SIN_COMANDAS");
        }

        @Test
        @DisplayName("CP-124 Rechaza versión anterior, total distinto y total en cero")
        void cambios() {
            String cuenta = cuentaServida(mesero, mesa1);
            assertThat(cobrar(mesero, cuenta, pago(0, "EFECTIVO", null, "55.50")).codigo()).isEqualTo("VERSION_OBSOLETA");
            assertThat(cobrar(mesero, cuenta, pago(1, "EFECTIVO", null, "55.49")).codigo()).isEqualTo("TOTAL_CAMBIADO");
            sinCobrar(cuenta);
            admin.patch("/productos/" + agua, Map.of("precioVenta", "0.00"));
            String cortesia = abrirEn(mesero, mesa2);
            servida(mesero, cortesia, linea(agua, 1, null));
            assertThat(acciones(mesero.get("/cuentas/" + cortesia).json())).doesNotContain("COBRAR");
            assertThat(cobrar(mesero, cortesia, pago(1, "EFECTIVO", null, "0.00")).codigo()).isEqualTo("TOTAL_INVALIDO");
        }

        @Test
        @DisplayName("CP-125 Solo cobra el mesero responsable; exige clave de idempotencia")
        void responsable() {
            String cuenta = cuentaServida(mesero, mesa1);
            assertThat(cobrar(otroMesero, cuenta, pago(1, "EFECTIVO", null, "55.50")).estado()).isEqualTo(404);
            assertThat(cobrar(mesero, "999999", pago(1, "EFECTIVO", null, "55.50")).estado()).isEqualTo(404);
            assertThat(mesero.post("/cuentas/" + cuenta + "/cobro", pago(1, "EFECTIVO", null, "55.50"))
                    .campo("Idempotency-Key")).isNotBlank();
            sinCobrar(cuenta);
        }

        @ParameterizedTest(name = "CP-126 {0} no puede cobrar")
        @ValueSource(strings = {"CAJERO", "COCINERO", "ALMACENISTA", "ADMINISTRADOR"})
        void permisos(String rol) {
            String cuenta = cuentaServida(mesero, mesa1);
            assertThat(cobrar(sesionDe(crear(rol)), cuenta, pago(1, "EFECTIVO", null, "55.50")).estado()).isEqualTo(403);
            sinCobrar(cuenta);
        }

        @Test
        @DisplayName("CP-127 Datos inválidos: 400 con el error de cada campo")
        void invalidos() {
            String cuenta = cuentaServida(mesero, mesa1);
            var vacio = cobrar(mesero, cuenta, Map.of());
            assertThat(vacio.estado()).isEqualTo(400);
            for (String campo : List.of("versionCuenta", "metodo", "totalEsperado")) {
                assertThat(vacio.campo(campo)).as(campo).isNotBlank();
            }
            assertThat(cobrar(mesero, cuenta, pago(1, "CHEQUE", null, "55.50")).estado()).isEqualTo(400);
            for (String total : List.of("-1", "55.505", "1e3", "")) {
                assertThat(cobrar(mesero, cuenta, pago(1, "EFECTIVO", null, total)).campo("totalEsperado")).as(total)
                        .isNotBlank();
            }
            for (String referencia : List.of("A B", "abc$", "x".repeat(41))) {
                assertThat(cobrar(mesero, cuenta, pago(1, "TARJETA", referencia, "55.50")).campo("referenciaExterna"))
                        .as(referencia).isNotBlank();
            }
            sinCobrar(cuenta);
        }

        @Test
        @DisplayName("CP-128 Cobrar dos veces, en serie o a la vez, produce una sola venta con un solo pago")
        void dobleCobro() throws Exception {
            String cuenta = cuentaServida(mesero, mesa1);
            String clave = clave();
            var cuerpo = pago(1, "TARJETA", "A1", "55.50");
            var una = mesero.postConClave("/cuentas/" + cuenta + "/cobro", clave, cuerpo);
            assertThat(una.estado()).isEqualTo(201);
            assertThat(mesero.postConClave("/cuentas/" + cuenta + "/cobro", clave, cuerpo).texto()).isEqualTo(una.texto());
            assertThat(mesero.postConClave("/cuentas/" + cuenta + "/cobro", clave, pago(1, "TARJETA", "A2", "55.50"))
                    .codigo()).isEqualTo("IDEMPOTENCIA_CONFLICTO");
            assertThat(cobrar(cuenta, "TARJETA", "A1").codigo()).isEqualTo("CUENTA_CERRADA");
            assertThat(contar("pago")).isEqualTo(1);

            String otra = cuentaServida(mesero, mesa1);
            assertThat(GestionEmpleadosTest.simultaneas(List.of(
                    () -> cobrar(otra, "EFECTIVO", null).estado(),
                    () -> cobrar(otra, "TARJETA", "B1").estado(),
                    () -> cobrar(otra, "TRANSFERENCIA", null).estado()))).containsExactlyInAnyOrder(201, 409, 409);
            assertThat(contar("venta")).isEqualTo(2);
            assertThat(contar("pago")).isEqualTo(2);
        }

        @Test
        @DisplayName("CP-129 Cobrar a la vez que se envía otra comanda: gana uno; después del cobro nada cambia")
        void simultaneos() throws Exception {
            for (int i = 0; i < 4; i++) {
                String cuenta = cuentaServida(mesero, mesa1);
                List<Integer> estados = GestionEmpleadosTest.simultaneas(List.of(
                        () -> cobrar(cuenta, "EFECTIVO", null).estado(),
                        () -> enviar(mesero, cuenta, linea(agua, 1, null)).estado()));
                assertThat(estados).containsExactlyInAnyOrder(201, 409);
                JsonNode c = mesero.get("/cuentas/" + cuenta).json();
                if (estados.getFirst() == 201) {
                    assertThat(c.get("comandas").size()).isEqualTo(1);
                } else {
                    String id = c.get("comandas").get(1).get("id").asString();
                    assertThat(cancelar(cajero, id, 0, "x").estado()).isEqualTo(200);
                    assertThat(cobrar(mesero, cuenta, pago(3, "EFECTIVO", null, "55.50")).estado()).isEqualTo(201);
                }
                String servida = c.get("comandas").get(0).get("id").asString();
                assertThat(cancelar(cajero, servida, 3, "x").codigo()).isEqualTo("CUENTA_CERRADA");
                assertThat(enviar(mesero, cuenta, linea(agua, 1, null)).codigo()).isEqualTo("CUENTA_CERRADA");
            }
        }

        @Test
        @DisplayName("CP-130 Un fallo al guardar el pago revierte venta y cierre; la misma clave registra una sola vez")
        void reversion() {
            String cuenta = cuentaServida(mesero, mesa1);
            sql.execute("ALTER TABLE sivant.pago ADD CONSTRAINT ck_fallo_simulado CHECK (false) NOT VALID");
            String clave = clave();
            var cuerpo = pago(1, "TARJETA", "900123", "55.50");
            assertThat(mesero.postConClave("/cuentas/" + cuenta + "/cobro", clave, cuerpo).estado()).isEqualTo(500);
            sinCobrar(cuenta);
            assertThat(sql.queryForObject("SELECT count(*) FROM sivant.auditoria WHERE accion = 'VENTA_COBRADA'",
                    Integer.class)).isZero();
            retirarFallos();
            for (int i = 0; i < 2; i++) {
                assertThat(mesero.postConClave("/cuentas/" + cuenta + "/cobro", clave, cuerpo).estado()).isEqualTo(201);
            }
            assertThat(contar("pago")).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("RF05 · Cobro con tarjeta")
    class Tarjeta {

        @Test
        @DisplayName("CP-131 Referencia obligatoria en tarjeta, opcional en transferencia y ausente en efectivo")
        void referencia() {
            String cuenta = cuentaServida(mesero, mesa1);
            for (String referencia : new String[] {null, "", "   "}) {
                assertThat(cobrar(cuenta, "TARJETA", referencia).campo("referenciaExterna")).isNotBlank();
            }
            assertThat(cobrar(cuenta, "EFECTIVO", "123").campo("referenciaExterna")).isNotBlank();
            sinCobrar(cuenta);
            var r = cobrar(cuenta, "TARJETA", " ab-12 ");
            assertThat(r.estado()).as(r.texto()).isEqualTo(201);
            assertThat(r.json().get("pago").get("referenciaExterna").asString()).isEqualTo("AB-12");
            assertThat(cobrar(cuentaServida(mesero, mesa1), "TRANSFERENCIA", null).estado()).isEqualTo(201);
            assertThat(cobrar(cuentaServida(mesero, mesa1), "TRANSFERENCIA", "SPEI9").json().get("pago")
                    .get("referenciaExterna").asString()).isEqualTo("SPEI9");
        }

        @Test
        @DisplayName("CP-132 Rechaza el número de la tarjeta y no lo conserva")
        void numeroDeTarjeta() {
            String cuenta = cuentaServida(mesero, mesa1);
            var r = cobrar(cuenta, "TARJETA", NUMERO_TARJETA);
            assertThat(r.estado()).isEqualTo(400);
            assertThat(r.texto()).doesNotContain("4111");
            sinCobrar(cuenta);
            assertThat(sql.queryForObject("SELECT count(*) FROM sivant.auditoria WHERE detalle::text LIKE '%4111%'",
                    Integer.class)).isZero();
            assertThat(cobrar(cuenta, "TARJETA", "123456789012").estado()).as("folio de 12 dígitos").isEqualTo(201);
        }

        @Test
        @DisplayName("CP-133 La referencia no se repite con el mismo método y día; sí con otro método u otro día")
        void repetida() {
            assertThat(cobrar(cuentaServida(mesero, mesa1), "TARJETA", "778899").estado()).isEqualTo(201);
            String cuenta = cuentaServida(mesero, mesa1);
            var r = cobrar(cuenta, "TARJETA", "778899");
            assertThat(r.codigo()).isEqualTo("REFERENCIA_REPETIDA");
            assertThat(r.campo("referenciaExterna")).isNotBlank();
            assertThat(mesero.get("/cuentas/" + cuenta).json().get("estado").asString()).isEqualTo("ABIERTA");
            assertThat(contar("venta")).isEqualTo(1);
            assertThat(cobrar(cuenta, "TRANSFERENCIA", "778899").estado()).isEqualTo(201);

            reloj.avanzar(Duration.ofDays(1));
            mesero = sesionDe(juan);
            cocinero = sesionDe(crear("COCINERO"));
            assertThat(cobrar(cuentaServida(mesero, mesa1), "TARJETA", "778899").estado()).isEqualTo(201);
        }

        @Test
        @DisplayName("CP-134 La misma referencia en dos cuentas a la vez se registra una sola vez")
        void repetidaSimultanea() throws Exception {
            String una = cuentaServida(mesero, mesa1);
            String otra = cuentaServida(otroMesero, mesa2);
            assertThat(GestionEmpleadosTest.simultaneas(List.of(
                    () -> cobrar(una, "TARJETA", "5050").estado(),
                    () -> cobrar(otroMesero, otra, pago(1, "TARJETA", "5050", "55.50")).estado())))
                    .containsExactlyInAnyOrder(201, 409);
            assertThat(contar("pago")).isEqualTo(1);
        }

        @Test
        @DisplayName("CP-135 Si la cuenta cambió después del cargo, no se registra y la referencia queda libre")
        void cambioTrasCargo() {
            String cuenta = cuentaServida(mesero, mesa1);
            servida(mesero, cuenta, linea(agua, 1, null));
            var r = cobrar(cuenta, "TARJETA", "616161");
            assertThat(r.codigo()).isEqualTo("VERSION_OBSOLETA");
            sinCobrar(cuenta);
            assertThat(cobrar(mesero, cuenta, pago(2, "TARJETA", "616161", "75.50")).estado()).isEqualTo(201);
        }
    }

    @Nested
    @DisplayName("RF06 · Consultar ventas")
    class Consultar {

        private void mover(String venta, OffsetDateTime fecha) {
            sql.update("UPDATE sivant.venta SET fecha_hora = ? WHERE id_venta = ?", fecha, Long.valueOf(venta));
        }

        private List<String> ids(ClienteApi cliente, String filtro) {
            var r = cliente.get("/ventas?" + filtro);
            assertThat(r.estado()).as(r.texto()).isEqualTo(200);
            List<String> ids = new ArrayList<>();
            r.json().get("contenido").forEach(v -> ids.add(v.get("id").asString()));
            return ids;
        }

        private LocalDate hoy() {
            return LocalDate.ofInstant(reloj.instant(), ZONA);
        }

        @Test
        @DisplayName("CP-136 Lista completadas y canceladas, la más reciente primero, con filtros combinables y páginas")
        void filtros() {
            String efectivo = venta("EFECTIVO", null);
            String tarjeta = venta(otroMesero, mesa2, "TARJETA", "T1");
            String transferencia = venta("TRANSFERENCIA", null);
            assertThat(cancelarVenta(admin, tarjeta, devolucion("R1")).estado()).isEqualTo(200);
            Long ana = sql.queryForObject("SELECT id_empleado FROM sivant.empleado WHERE correo = 'ana@sivant.test'",
                    Long.class);
            assertThat(ids(admin, "")).containsExactly(transferencia, tarjeta, efectivo);
            assertThat(ids(cajero, "estado=CANCELADA")).containsExactly(tarjeta);
            assertThat(ids(cajero, "estado=COMPLETADA")).containsExactly(transferencia, efectivo);
            assertThat(ids(cajero, "metodo=EFECTIVO")).containsExactly(efectivo);
            assertThat(ids(cajero, "meseroId=" + ana)).containsExactly(tarjeta);
            assertThat(ids(cajero, "mesaId=" + mesa1)).containsExactly(transferencia, efectivo);
            assertThat(ids(cajero, "mesaId=" + mesa1 + "&metodo=TARJETA")).isEmpty();
            var pagina = cajero.get("/ventas?size=2&page=1").json();
            assertThat(pagina.get("totalElementos").asInt()).isEqualTo(3);
            assertThat(pagina.get("contenido").size()).isEqualTo(1);
            JsonNode fila = cajero.get("/ventas?estado=CANCELADA").json().get("contenido").get(0);
            assertThat(fila.get("total").asString()).isEqualTo("55.50");
            assertThat(fila.get("mesero").asString()).isEqualTo("Ana Mesera");
            assertThat(fila.get("mesa").asInt()).isEqualTo(2);
            JsonNode opciones = cajero.get("/ventas/opciones").json();
            assertThat(opciones.get("meseros").toString()).containsSubsequence("Ana Mesera", "Juan Pérez");
            assertThat(opciones.get("mesas").toString()).containsSubsequence("Mesa 1", "Mesa 2");
        }

        @Test
        @DisplayName("CP-137 Los días se interpretan en la zona del negocio e incluyen completo el último")
        void dias() {
            String venta = venta("EFECTIVO", null);
            LocalDate dia = hoy().minusDays(10);
            mover(venta, dia.plusDays(1).atStartOfDay(ZONA).minusSeconds(1).toOffsetDateTime());
            assertThat(ids(cajero, "desde=" + dia + "&hasta=" + dia)).containsExactly(venta);
            assertThat(ids(cajero, "desde=" + dia.plusDays(1))).isEmpty();
            mover(venta, dia.atStartOfDay(ZONA).toOffsetDateTime());
            assertThat(ids(cajero, "desde=" + dia + "&hasta=" + dia)).containsExactly(venta);
            assertThat(ids(cajero, "hasta=" + dia.minusDays(1))).isEmpty();
        }

        @Test
        @DisplayName("CP-138 Solo se consultan y cancelan los últimos 24 meses; lo anterior se conserva")
        void ventana() {
            String dentro = venta("EFECTIVO", null);
            String fuera = venta("EFECTIVO", null);
            OffsetDateTime limite = hoy().minusMonths(24).atStartOfDay(ZONA).toOffsetDateTime();
            mover(dentro, limite);
            mover(fuera, limite.minusSeconds(1));
            assertThat(ids(admin, "")).containsExactly(dentro);
            assertThat(ids(admin, "desde=2000-01-01")).containsExactly(dentro);
            assertThat(admin.get("/ventas/" + dentro).estado()).isEqualTo(200);
            assertThat(admin.get("/ventas/" + fuera).estado()).isEqualTo(404);
            assertThat(cancelarVenta(admin, fuera, devolucion(null)).estado()).isEqualTo(404);
            assertThat(contar("venta")).isEqualTo(2);
        }

        @Test
        @DisplayName("CP-139 El detalle muestra pago, comandas y cancelaciones; solo el administrador puede cancelar")
        void detalle() {
            String cuenta = abrirEn(mesero, mesa1);
            servida(mesero, cuenta, linea(agua, 2, "Fría"));
            String otra = enviar(mesero, cuenta, linea(galleta, 1, null)).json().get("id").asString();
            cancelar(cajero, otra, 0, "Se equivocó");
            String id = cobrar(mesero, cuenta, pago(3, "TARJETA", "Z9", "40.00")).json().get("venta").get("id").asString();
            JsonNode v = cajero.get("/ventas/" + id).json();
            assertThat(v.get("cuentaId").asString()).isEqualTo(cuenta);
            assertThat(v.get("mesa").asInt()).isEqualTo(1);
            assertThat(v.get("mesero").asString()).isEqualTo("Juan Pérez");
            assertThat(v.get("total").asString()).isEqualTo("40.00");
            assertThat(v.get("pago").get("referenciaExterna").asString()).isEqualTo("Z9");
            assertThat(v.get("comandas").size()).isEqualTo(2);
            assertThat(v.get("comandas").get(0).get("detalles").get(0).get("precioUnitario").asString()).isEqualTo("20.00");
            assertThat(v.get("comandas").get(1).get("cancelacion").get("justificacion").asString())
                    .isEqualTo("Se equivocó");
            assertThat(v.has("cancelacion")).isFalse();
            assertThat(v.get("acciones").size()).isZero();
            assertThat(acciones(admin.get("/ventas/" + id).json())).contains("CANCELAR");
            assertThat(cajero.get("/ventas/999999").estado()).isEqualTo(404);
        }

        @ParameterizedTest(name = "CP-140 {0} no puede consultar ventas")
        @ValueSource(strings = {"MESERO", "COCINERO", "ALMACENISTA"})
        void permisos(String rol) {
            String id = venta("EFECTIVO", null);
            ClienteApi cliente = sesionDe(crear(rol));
            for (String ruta : List.of("/ventas", "/ventas/" + id, "/ventas/opciones")) {
                assertThat(cliente.get(ruta).estado()).as(ruta).isEqualTo(403);
            }
            assertThat(anonimo().get("/ventas").estado()).isEqualTo(401);
        }

        @ParameterizedTest(name = "CP-141 Filtro inválido {0}")
        @ValueSource(strings = {"desde=ayer", "estado=X", "metodo=CHEQUE", "meseroId=x", "mesaId=x", "page=-1", "size=101",
                "desde=2026-02-02&hasta=2026-02-01"})
        void invalidos(String filtro) {
            assertThat(cajero.get("/ventas?" + filtro).estado()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("RF22 · Cancelar venta")
    class Cancelar {

        @Test
        @DisplayName("CP-142 Conserva pago, cuenta, comandas y consumos; registra devolución, responsable y auditoría")
        void efectivo() {
            String cuenta = cuentaServida(mesero, mesa1);
            String id = cobrar(cuenta, "EFECTIVO", null).json().get("venta").get("id").asString();
            var movimientos = sql.queryForList("SELECT * FROM sivant.movimiento_inventario ORDER BY 1");
            var existencias = sql.queryForList("SELECT * FROM sivant.existencia_inventario ORDER BY 1");
            var pagos = sql.queryForList("SELECT * FROM sivant.pago");
            var antes = sql.queryForList("SELECT * FROM sivant.cuenta");

            var r = cancelarVenta(admin, id, devolucion(null));

            assertThat(r.estado()).as(r.texto()).isEqualTo(200);
            JsonNode v = r.json();
            assertThat(v.get("estado").asString()).isEqualTo("CANCELADA");
            assertThat(v.get("acciones").size()).isZero();
            assertThat(v.get("total").asString()).isEqualTo("55.50");
            assertThat(v.get("pago").get("monto").asString()).isEqualTo("55.50");
            assertThat(v.get("cancelacion").get("montoDevuelto").asString()).isEqualTo("55.50");
            assertThat(v.get("cancelacion").get("justificacion").asString()).isEqualTo("Cobro equivocado");
            assertThat(v.get("cancelacion").get("administrador").asString()).isEqualTo("Persona ADMINISTRADOR");
            assertThat(v.get("cancelacion").has("referenciaReversion")).isFalse();
            assertThat(v.get("comandas").get(0).get("estado").asString()).isEqualTo("SERVIDA");
            assertThat(sql.queryForList("SELECT * FROM sivant.movimiento_inventario ORDER BY 1")).isEqualTo(movimientos);
            assertThat(sql.queryForList("SELECT * FROM sivant.existencia_inventario ORDER BY 1")).isEqualTo(existencias);
            assertThat(sql.queryForList("SELECT * FROM sivant.pago")).isEqualTo(pagos);
            assertThat(sql.queryForList("SELECT * FROM sivant.cuenta")).isEqualTo(antes);
            assertThat(sql.queryForObject("SELECT detalle ->> 'montoDevuelto' FROM sivant.auditoria WHERE accion = "
                    + "'VENTA_CANCELADA' AND id_empleado = ? AND id_registro = ?", String.class, administrador.getId(),
                    Long.valueOf(id))).isEqualTo("55.50");
            assertThat(mesero.get("/cuentas/" + cuenta).json().get("cobro").get("estado").asString())
                    .isEqualTo("CANCELADA");
        }

        @ParameterizedTest(name = "CP-143 {0} exige la referencia de la reversión")
        @CsvSource({"TARJETA,C1", "TRANSFERENCIA,"})
        void reversion(String metodo, String cobro) {
            String id = venta(metodo, cobro);
            for (String referencia : new String[] {null, "", "  ", "con espacio"}) {
                assertThat(cancelarVenta(admin, id, devolucion(referencia)).campo("referenciaReversion"))
                        .as("«%s»", referencia).isNotBlank();
            }
            assertThat(cajero.get("/ventas/" + id).json().get("estado").asString()).isEqualTo("COMPLETADA");
            assertThat(cancelarVenta(admin, id, devolucion(" rev-1 ")).json().get("cancelacion")
                    .get("referenciaReversion").asString()).isEqualTo("REV-1");
        }

        @Test
        @DisplayName("CP-144 El efectivo no lleva referencia y la tarjeta no admite su número")
        void referenciaIndebida() {
            assertThat(cancelarVenta(admin, venta("EFECTIVO", null), devolucion("X1")).campo("referenciaReversion"))
                    .isNotBlank();
            assertThat(cancelarVenta(admin, venta("TARJETA", "C1"), devolucion(NUMERO_TARJETA))
                    .campo("referenciaReversion")).isNotBlank();
            assertThat(contar("cancelacion_venta")).isZero();
        }

        @Test
        @DisplayName("CP-145 Exige confirmación, justificación, clave y una venta existente")
        void invalidos() {
            String id = venta("EFECTIVO", null);
            assertThat(cancelarVenta(admin, id, Map.of("confirmado", false, "justificacion", "x")).campo("confirmado"))
                    .isNotBlank();
            assertThat(cancelarVenta(admin, id, Map.of("justificacion", "x")).campo("confirmado")).isNotBlank();
            for (String motivo : List.of("", "   ", "x".repeat(501))) {
                assertThat(cancelarVenta(admin, id, Map.of("confirmado", true, "justificacion", motivo))
                        .campo("justificacion")).isNotBlank();
            }
            assertThat(admin.post("/ventas/" + id + "/cancelacion", devolucion(null)).campo("Idempotency-Key"))
                    .isNotBlank();
            assertThat(cancelarVenta(admin, "999999", devolucion(null)).estado()).isEqualTo(404);
            assertThat(contar("cancelacion_venta")).isZero();
        }

        @ParameterizedTest(name = "CP-146 {0} no puede cancelar una venta")
        @ValueSource(strings = {"MESERO", "CAJERO", "COCINERO", "ALMACENISTA"})
        void permisos(String rol) {
            String id = venta("EFECTIVO", null);
            assertThat(cancelarVenta(sesionDe(crear(rol)), id, devolucion(null)).estado()).isEqualTo(403);
            assertThat(contar("cancelacion_venta")).isZero();
        }

        @Test
        @DisplayName("CP-147 Cancelar dos veces, en serie o a la vez, registra una sola devolución")
        void dobleCancelacion() throws Exception {
            String id = venta("EFECTIVO", null);
            String clave = clave();
            var una = admin.postConClave("/ventas/" + id + "/cancelacion", clave, devolucion(null));
            assertThat(admin.postConClave("/ventas/" + id + "/cancelacion", clave, devolucion(null)).texto())
                    .isEqualTo(una.texto());
            assertThat(cancelarVenta(admin, id, devolucion(null)).codigo()).isEqualTo("VENTA_CANCELADA");

            String otra = venta("TARJETA", "C1");
            List<String> codigos = java.util.Collections.synchronizedList(new ArrayList<>());
            List<java.util.concurrent.Callable<Integer>> tareas = new ArrayList<>();
            for (String referencia : List.of("R1", "R2", "R3")) {
                tareas.add(() -> {
                    var r = cancelarVenta(admin, otra, devolucion(referencia));
                    codigos.add(r.codigo());
                    return r.estado();
                });
            }
            assertThat(GestionEmpleadosTest.simultaneas(tareas)).containsExactlyInAnyOrder(200, 409, 409);
            assertThat(codigos).as("las demás ven la venta ya cancelada").containsExactlyInAnyOrder("", "VENTA_CANCELADA",
                    "VENTA_CANCELADA");
            assertThat(contar("cancelacion_venta")).isEqualTo(2);
        }

        @Test
        @DisplayName("CP-148 La referencia de una reversión no se repite el mismo día")
        void referenciaRepetida() {
            String una = venta("TARJETA", "C1");
            String otra = venta("TARJETA", "C2");
            assertThat(cancelarVenta(admin, una, devolucion("R1")).estado()).isEqualTo(200);
            assertThat(cancelarVenta(admin, otra, devolucion("r1")).codigo()).isEqualTo("REFERENCIA_REPETIDA");
            assertThat(cajero.get("/ventas/" + otra).json().get("estado").asString()).isEqualTo("COMPLETADA");
        }

        @Test
        @DisplayName("CP-149 Un fallo al guardar la devolución deja la venta completada; la clave puede reintentarse")
        void reversionPorFallo() {
            String id = venta("EFECTIVO", null);
            sql.execute("ALTER TABLE sivant.cancelacion_venta ADD CONSTRAINT ck_fallo_simulado CHECK (false) NOT VALID");
            String clave = clave();
            assertThat(admin.postConClave("/ventas/" + id + "/cancelacion", clave, devolucion(null)).estado())
                    .isEqualTo(500);
            assertThat(cajero.get("/ventas/" + id).json().get("estado").asString()).isEqualTo("COMPLETADA");
            retirarFallos();
            assertThat(admin.postConClave("/ventas/" + id + "/cancelacion", clave, devolucion(null)).estado())
                    .isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("Integridad en PostgreSQL")
    class Integridad {

        @ParameterizedTest(name = "CP-150 PostgreSQL rechaza: {0}")
        @ValueSource(strings = {
                "DELETE FROM sivant.pago",
                "UPDATE sivant.pago SET monto = 1",
                "UPDATE sivant.venta SET total = 1",
                "UPDATE sivant.venta SET estado = 'CANCELADA'",
                "UPDATE sivant.pago SET referencia_externa = NULL",
                "UPDATE sivant.pago SET metodo = 'EFECTIVO'",
                "UPDATE sivant.pago SET referencia_externa = ' '",
                "INSERT INTO sivant.venta (id_cuenta, fecha_hora, total) SELECT id_cuenta, now(), total FROM sivant.venta",
                "INSERT INTO sivant.pago (id_venta, id_mesero, fecha_hora, monto, metodo) "
                        + "SELECT id_venta, id_mesero, now(), monto, 'EFECTIVO' FROM sivant.pago",
                "INSERT INTO sivant.cancelacion_venta (id_venta, id_administrador, fecha_hora, justificacion, "
                        + "monto_devuelto, referencia_reversion) "
                        + "SELECT id_venta, id_mesero, now(), 'x', monto, 'R1' FROM sivant.pago"})
        void integridad(String sentencia) {
            venta("TARJETA", "C1");
            assertThatThrownBy(() -> sql.update(sentencia)).isInstanceOf(DataAccessException.class);
            assertThat(contar("pago")).isEqualTo(1);
        }

        @Test
        @DisplayName("CP-151 Una venta sin pago o sobre una cuenta abierta no puede confirmarse")
        void sinPago() {
            Long cuenta = Long.valueOf(cuentaServida(mesero, mesa1));
            String insertar = "INSERT INTO sivant.venta (id_cuenta, fecha_hora, total) VALUES (?, now(), 55.50)";
            assertThatThrownBy(() -> sql.update(insertar, cuenta)).isInstanceOf(DataAccessException.class);
            assertThatThrownBy(() -> transaccion.executeWithoutResult(t -> {
                sql.update(insertar, cuenta);
                sql.update("INSERT INTO sivant.pago (id_venta, id_mesero, fecha_hora, monto, metodo) "
                        + "SELECT id_venta, ?, now(), 55.50, 'EFECTIVO' FROM sivant.venta", juan.getId());
            })).hasStackTraceContaining("no cuadra con su cuenta");
            assertThat(contar("venta")).isZero();
        }
    }
}
