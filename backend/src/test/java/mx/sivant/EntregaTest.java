package mx.sivant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataIntegrityViolationException;
import tools.jackson.databind.JsonNode;

@DisplayName("Servicio y consumo")
class EntregaTest extends PruebaServicio {

    ClienteApi almacen;
    String cuenta;
    String capuchino;
    String latte;
    Long cafe;
    Long leche;
    Long aguaExistencia;

    @BeforeEach
    void prepararConsumo() {
        almacen = sesionDe(crear("ALMACENISTA"));
        Long insumoCafe = crearInsumo("Café", "GRAMO");
        Long insumoLeche = crearInsumo("Leche", "MILILITRO");
        cafe = existencia("existencia_insumo WHERE id_insumo = " + insumoCafe);
        leche = existencia("existencia_insumo WHERE id_insumo = " + insumoLeche);
        aguaExistencia = existencia("existencia_producto WHERE id_producto_reventa = " + agua);
        capuchino = preparado("Capuchino", insumoCafe, "18.5", insumoLeche, "180");
        // Receta en orden inverso: dos servicios simultáneos tomarían las existencias cruzadas.
        latte = preparado("Latte", insumoLeche, "250", insumoCafe, "10");
        surtir(cafe, "1000");
        surtir(leche, "1000");
        surtir(aguaExistencia, "10");
        cuenta = cuentaNueva();
    }

    @AfterEach
    void retirarFallos() {
        sql.execute("ALTER TABLE sivant.notificacion DROP CONSTRAINT IF EXISTS ck_fallo_simulado");
        sql.execute("ALTER TABLE sivant.movimiento_inventario DROP CONSTRAINT IF EXISTS ck_fallo_simulado");
    }

    private Long existencia(String donde) {
        return sql.queryForObject("SELECT id_existencia FROM sivant." + donde, Long.class);
    }

    private String preparado(String nombre, Long uno, String cantidadUno, Long dos, String cantidadDos) {
        return admin.post("/productos", Map.of("nombre", nombre, "descripcion", "x", "precioVenta", "50", "tipo",
                "PREPARADO", "unidadMedida", "PIEZA", "receta", List.of(
                        Map.of("insumoId", uno.toString(), "cantidadRequerida", cantidadUno),
                        Map.of("insumoId", dos.toString(), "cantidadRequerida", cantidadDos))))
                .json().get("id").asString();
    }

    private void surtir(Long existencia, String cantidad) {
        assertThat(almacen.postConClave("/inventario/existencias/" + existencia + "/movimientos", clave(),
                Map.of("tipo", "ENTRADA_MANUAL", "cantidad", cantidad)).estado()).isEqualTo(201);
    }

    private String saldo(Long existencia) {
        return almacen.get("/inventario/existencias/" + existencia).json().get("cantidadActual").asString();
    }

    private String comandaLista(Object... lineas) {
        String id = enviar(mesero, cuenta, lineas).json().get("id").asString();
        assertThat(preparar(id, 0).estado()).isEqualTo(200);
        assertThat(lista(id, 1).estado()).isEqualTo(200);
        return id;
    }

    private ClienteApi.Respuesta servir(ClienteApi cliente, String comanda, int version) {
        return servir(cliente, clave(), comanda, version);
    }

    private ClienteApi.Respuesta servir(ClienteApi cliente, String clave, String comanda, int version) {
        return cliente.postConClave("/comandas/" + comanda + "/servicio", clave, Map.of("version", version));
    }

    private int consumos() {
        return sql.queryForObject("SELECT count(*) FROM sivant.movimiento_inventario WHERE tipo = 'CONSUMO_VENTA'",
                Integer.class);
    }

    private String estado(String comanda) {
        return sql.queryForObject("SELECT estado FROM sivant.comanda WHERE id_comanda = ?", String.class,
                Long.valueOf(comanda));
    }

    @Nested
    @DisplayName("RF23 · Marcar pedido servido")
    class Servir {

        @Test
        @DisplayName("CP-109 Registra responsable y fecha, sale de la cola y conserva el total de la cuenta")
        void servida() {
            String id = comandaLista(linea(capuchino, 2, null), linea(agua, 1, null));
            assertThat(mesero.get("/cuentas/" + cuenta).json().get("comandas").get(0).get("acciones").toString())
                    .contains("SERVIR");
            var r = servir(mesero, id, 2);
            assertThat(r.estado()).as(r.texto()).isEqualTo(200);
            assertThat(r.json().get("comanda").get("estado").asString()).isEqualTo("SERVIDA");
            assertThat(r.json().get("comanda").get("version").asInt()).isEqualTo(3);
            assertThat(r.json().get("comanda").get("acciones").size()).isZero();
            assertThat(r.json().get("consumo").size()).isEqualTo(3);
            assertThat(r.texto()).as("sin saldos").doesNotContain("saldo", "cantidadActual");
            assertThat(sql.queryForObject("""
                    SELECT id_empleado FROM sivant.historial_estado_comanda
                    WHERE estado_anterior = 'LISTA' AND estado_nuevo = 'SERVIDA' AND fecha_hora IS NOT NULL
                    """, Long.class)).isEqualTo(juan.getId());
            assertThat(cocinero.get("/cocina/cola").texto()).isEqualTo("[]");
            assertThat(mesero.get("/cuentas/" + cuenta).json().get("total").asString()).isEqualTo("120.00");
        }

        @Test
        @DisplayName("CP-110 Solo se sirve una comanda lista, con su versión, por el mesero responsable")
        void invalidas() {
            String id = enviar(mesero, cuenta, linea(agua, 1, null)).json().get("id").asString();
            assertThat(servir(mesero, id, 0).codigo()).isEqualTo("TRANSICION_INVALIDA");
            preparar(id, 0);
            assertThat(servir(mesero, id, 1).codigo()).isEqualTo("TRANSICION_INVALIDA");
            lista(id, 1);
            assertThat(servir(mesero, id, 1).codigo()).isEqualTo("VERSION_OBSOLETA");
            assertThat(servir(otroMesero, id, 2).estado()).isEqualTo(404);
            assertThat(servir(mesero, "999999", 2).estado()).isEqualTo(404);
            assertThat(mesero.post("/comandas/" + id + "/servicio", Map.of("version", 2)).campo("Idempotency-Key"))
                    .isNotBlank();
            assertThat(estado(id)).isEqualTo("LISTA");
            assertThat(consumos()).isZero();
        }

        @Test
        @DisplayName("CP-111 Una comanda servida no se edita ni se cancela, y su cuenta debe cobrarse")
        void despues() {
            JsonNode comanda = enviar(mesero, cuenta, linea(agua, 1, null)).json();
            String id = comanda.get("id").asString();
            preparar(id, 0);
            lista(id, 1);
            servir(mesero, id, 2);
            assertThat(editar(mesero, id, 3, detalle(comanda.get("detalles").get(0), 5)).codigo())
                    .isEqualTo("COMANDA_NO_EDITABLE");
            assertThat(cancelar(cajero, id, 3, "x").codigo()).isEqualTo("COMANDA_NO_CANCELABLE");
            assertThat(mesero.postConClave("/cuentas/" + cuenta + "/cierre", clave(), Map.of("version", 1)).codigo())
                    .isEqualTo("CUENTA_CON_COMANDAS");
        }

        @ParameterizedTest(name = "CP-112 {0} no puede marcar servida")
        @ValueSource(strings = {"CAJERO", "COCINERO", "ALMACENISTA", "ADMINISTRADOR"})
        void permisos(String rol) {
            String id = comandaLista(linea(agua, 1, null));
            assertThat(servir(sesionDe(crear(rol)), id, 2).estado()).isEqualTo(403);
            assertThat(estado(id)).isEqualTo("LISTA");
            assertThat(saldo(aguaExistencia)).isEqualTo("10");
        }
    }

    @Nested
    @DisplayName("RF24 · Descontar inventario")
    class Descontar {

        @Test
        @DisplayName("CP-113 Descuenta receta por unidades y reventa, con un movimiento por detalle y existencia")
        void consumo() {
            String id = comandaLista(linea(capuchino, 3, null), linea(agua, 2, null));
            assertThat(saldo(cafe)).as("antes de servir no hay descuento").isEqualTo("1000");
            servir(mesero, id, 2);
            assertThat(saldo(cafe)).isEqualTo("944.5");
            assertThat(saldo(leche)).isEqualTo("460");
            assertThat(saldo(aguaExistencia)).isEqualTo("8");
            assertThat(consumos()).isEqualTo(3);
            assertThat(avisos(almacen, "")).isZero();
            var fila = almacen.get("/inventario/existencias/" + cafe + "/movimientos?tipo=CONSUMO_VENTA").json()
                    .get("contenido").get(0);
            assertThat(fila.get("cambio").asString()).isEqualTo("-55.5");
            assertThat(fila.get("saldoAnterior").asString()).isEqualTo("1000");
            assertThat(fila.get("saldoPosterior").asString()).isEqualTo("944.5");
            assertThat(fila.get("comandaId").asString()).isEqualTo(id);
            assertThat(fila.get("empleado").asString()).isEqualTo("Juan Pérez");
            assertThat(sql.queryForObject("SELECT jsonb_array_length(detalle -> 'consumo') FROM sivant.auditoria "
                    + "WHERE accion = 'COMANDA_SERVIDA'", Integer.class)).isEqualTo(3);
        }

        @Test
        @DisplayName("CP-114 Varios detalles del mismo insumo se comparan juntos y generan una sola alerta")
        void demandaAgrupada() {
            // Leche: 3×180 + 2×250 = 1040 > 1000, aunque cada detalle por separado alcanza.
            String id = comandaLista(linea(capuchino, 3, null), linea(latte, 2, null));
            var r = servir(mesero, id, 2);
            assertThat(r.estado()).isEqualTo(200);
            assertThat(saldo(leche)).isEqualTo("-40");
            assertThat(saldo(cafe)).isEqualTo("924.5");
            assertThat(avisos(almacen, "")).isEqualTo(1);
            var aviso = almacen.get("/notificaciones").json().get("contenido").get(0);
            assertThat(aviso.get("tipo").asString()).isEqualTo("ALERTA_INVENTARIO");
            assertThat(aviso.get("mensaje").asString()).contains("Leche", "comanda " + id);
            assertThat(aviso.get("contexto").get("faltante").asString()).isEqualTo("40");
            assertThat(aviso.get("contexto").get("existenciaId").asString()).isEqualTo(leche.toString());
            assertThat(sql.queryForList("""
                    SELECT saldo_anterior - saldo_posterior FROM sivant.movimiento_inventario
                    WHERE tipo = 'CONSUMO_VENTA' AND id_existencia = ? ORDER BY id_movimiento
                    """, String.class, leche)).hasSize(2);
            assertThat(sql.queryForObject("""
                    SELECT bool_and(m.saldo_posterior = coalesce(s.saldo_anterior, e.cantidad_actual)) FROM (
                        SELECT *, lead(id_movimiento) OVER (ORDER BY id_movimiento) AS siguiente
                        FROM sivant.movimiento_inventario WHERE id_existencia = ?) m
                    JOIN sivant.existencia_inventario e USING (id_existencia)
                    LEFT JOIN sivant.movimiento_inventario s ON s.id_movimiento = m.siguiente
                    """, Boolean.class, leche)).as("los saldos se encadenan").isTrue();
        }

        @Test
        @DisplayName("CP-115 Saldo insuficiente: permite servir, deja negativo y alerta por cada servicio; justo no alerta")
        void insuficiente() {
            String justa = comandaLista(linea(agua, 10, null));
            servir(mesero, justa, 2);
            assertThat(saldo(aguaExistencia)).isEqualTo("0");
            assertThat(avisos(almacen, "")).isZero();
            for (int i = 1; i <= 2; i++) {
                String id = comandaLista(linea(agua, 3, null));
                var r = servir(mesero, id, 2);
                assertThat(r.estado()).isEqualTo(200);
                assertThat(r.json().get("consumo").get(0).get("insuficiente").asBoolean()).isTrue();
                assertThat(saldo(aguaExistencia)).isEqualTo(String.valueOf(-3 * i));
                assertThat(avisos(almacen, "")).isEqualTo(i);
            }
            assertThat(almacen.get("/notificaciones").json().get("contenido").get(0).get("contexto").get("faltante")
                    .asString()).as("una existencia negativa no aporta").isEqualTo("3");
            for (ClienteApi c : List.of(mesero, cocinero, cajero, admin)) {
                assertThat(avisosDe(c, "ALERTA_INVENTARIO")).isZero();
            }
            assertThat(almacen.get("/inventario/existencias?nivelBajo=true").texto()).contains("\"cantidadActual\":\"-6\"");
        }

        private long avisosDe(ClienteApi c, String tipo) {
            long total = 0;
            for (JsonNode n : c.get("/notificaciones").json().get("contenido")) {
                total += n.get("tipo").asString().equals(tipo) ? 1 : 0;
            }
            return total;
        }

        @Test
        @DisplayName("CP-116 Servir dos veces, en serie o a la vez, aplica un solo descuento")
        void dobleServicio() throws Exception {
            String id = comandaLista(linea(agua, 2, null));
            String clave = clave();
            var una = servir(mesero, clave, id, 2);
            assertThat(servir(mesero, clave, id, 2).texto()).isEqualTo(una.texto());
            assertThat(servir(mesero, id, 2).codigo()).isEqualTo("TRANSICION_INVALIDA");
            assertThat(servir(mesero, id, 3).codigo()).isEqualTo("TRANSICION_INVALIDA");
            assertThat(saldo(aguaExistencia)).isEqualTo("8");

            String otra = comandaLista(linea(agua, 2, null));
            assertThat(GestionEmpleadosTest.simultaneas(List.of(
                    () -> servir(mesero, otra, 2).estado(),
                    () -> servir(mesero, otra, 2).estado(),
                    () -> servir(mesero, otra, 2).estado()))).containsExactlyInAnyOrder(200, 409, 409);
            assertThat(saldo(aguaExistencia)).isEqualTo("6");
            assertThat(consumos()).isEqualTo(2);
        }

        @Test
        @DisplayName("CP-117 Servicios y salidas simultáneos con existencias cruzadas no se bloquean ni pierden consumo")
        void simultaneos() throws Exception {
            String otraCuenta = abrir(otroMesero, mesa2).json().get("id").asString();
            for (int i = 0; i < 4; i++) {
                String a = comandaLista(linea(capuchino, 1, null));
                String b = enviar(otroMesero, otraCuenta, linea(latte, 1, null)).json().get("id").asString();
                preparar(b, 0);
                lista(b, 1);
                List<Callable<Integer>> tareas = List.of(
                        () -> servir(mesero, a, 2).estado(),
                        () -> servir(otroMesero, b, 2).estado(),
                        () -> almacen.postConClave("/inventario/existencias/" + cafe + "/movimientos", clave(),
                                Map.of("tipo", "SALIDA_MANUAL", "cantidad", "1", "causa", "MERMA")).estado());
                assertThat(GestionEmpleadosTest.simultaneas(tareas)).containsExactlyInAnyOrder(200, 200, 201);
            }
            assertThat(saldo(cafe)).as("1000 - 4×(18.5 + 10 + 1)").isEqualTo("882");
            assertThat(saldo(leche)).as("1000 - 4×(180 + 250)").isEqualTo("-720");
        }

        @ParameterizedTest(name = "CP-118 Un fallo en {0} revierte transición, movimientos y alertas")
        @ValueSource(strings = {"notificacion", "movimiento_inventario"})
        void reversion(String tabla) {
            String id = comandaLista(linea(capuchino, 1, null), linea(agua, 50, null));
            sql.execute("ALTER TABLE sivant." + tabla + " ADD CONSTRAINT ck_fallo_simulado CHECK (false) NOT VALID");
            String clave = clave();
            assertThat(servir(mesero, clave, id, 2).estado()).isEqualTo(500);
            assertThat(estado(id)).isEqualTo("LISTA");
            assertThat(saldo(cafe)).isEqualTo("1000");
            assertThat(saldo(aguaExistencia)).isEqualTo("10");
            assertThat(consumos()).isZero();
            assertThat(avisos(almacen, "")).isZero();
            assertThat(sql.queryForObject("SELECT count(*) FROM sivant.historial_estado_comanda WHERE estado_nuevo = "
                    + "'SERVIDA'", Integer.class)).isZero();
            retirarFallos();
            assertThat(servir(mesero, clave, id, 2).estado()).as("la misma clave puede reintentarse").isEqualTo(200);
            assertThat(saldo(aguaExistencia)).isEqualTo("-40");
        }

        @Test
        @DisplayName("CP-119 El consumo vuelve obsoleta una solicitud de ajuste pendiente")
        void ajustePendiente() {
            String solicitud = almacen.postConClave("/inventario/ajustes", clave(), Map.of("existenciaId",
                    aguaExistencia.toString(), "cantidadSolicitada", "9", "justificacion", "Conteo")).json().get("id")
                    .asString();
            servir(mesero, comandaLista(linea(agua, 1, null)), 2);
            assertThat(admin.postConClave("/inventario/ajustes/" + solicitud + "/resolucion", clave(),
                    Map.of("decision", "AUTORIZADA")).codigo()).isEqualTo("EXISTENCIA_CAMBIADA");
        }

        @Test
        @DisplayName("CP-120 PostgreSQL exige el detalle de origen y un solo consumo por detalle y existencia")
        void restricciones() {
            String id = comandaLista(linea(agua, 1, null));
            servir(mesero, id, 2);
            String insertar = """
                    INSERT INTO sivant.movimiento_inventario (id_existencia, id_empleado, id_detalle_comanda, tipo, cantidad,
                        saldo_anterior, saldo_posterior, fecha_hora)
                    SELECT id_existencia, id_empleado, %s, tipo, cantidad, saldo_anterior, saldo_posterior, now()
                    FROM sivant.movimiento_inventario WHERE tipo = 'CONSUMO_VENTA'
                    """;
            assertThatThrownBy(() -> sql.update(insertar.formatted("id_detalle_comanda")))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> sql.update(insertar.formatted("NULL")))
                    .isInstanceOf(DataIntegrityViolationException.class);
            assertThat(consumos()).isEqualTo(1);
        }
    }
}
