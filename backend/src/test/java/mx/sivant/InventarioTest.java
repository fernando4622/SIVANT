package mx.sivant;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.springframework.dao.DataIntegrityViolationException;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("Inventario")
class InventarioTest extends PruebaIntegracion {

    ClienteApi admin;
    ClienteApi almacen;
    String cafe;
    String leche;

    @BeforeEach
    void preparar() {
        admin = sesionDe(administrador);
        almacen = sesionDe(crear("ALMACENISTA"));
        cafe = existenciaDe(crearInsumo("Café en grano", "GRAMO"));
        leche = existenciaDe(crearInsumo("Leche", "MILILITRO"));
    }

    private String existenciaDe(Long insumo) {
        return sql.queryForObject("SELECT id_existencia FROM sivant.existencia_insumo WHERE id_insumo = ?", Long.class,
                insumo).toString();
    }

    private static String clave() {
        return UUID.randomUUID().toString();
    }

    private ClienteApi.Respuesta mover(String existencia, String tipo, String cantidad) {
        return mover(clave(), existencia, tipo, cantidad);
    }

    private ClienteApi.Respuesta mover(String clave, String existencia, String tipo, String cantidad) {
        Map<String, Object> cuerpo = new java.util.HashMap<>(Map.of("tipo", tipo, "cantidad", cantidad));
        if (tipo.equals("SALIDA_MANUAL")) {
            cuerpo.put("causa", "MERMA");
        }
        return almacen.postConClave("/inventario/existencias/" + existencia + "/movimientos", clave, cuerpo);
    }

    private ClienteApi.Respuesta solicitar(String existencia, String cantidad, String justificacion) {
        return almacen.postConClave("/inventario/ajustes", clave(), Map.of("existenciaId", existencia,
                "cantidadSolicitada", cantidad, "justificacion", justificacion));
    }

    private ClienteApi.Respuesta resolver(String solicitud, String decision) {
        return admin.postConClave("/inventario/ajustes/" + solicitud + "/resolucion", clave(),
                Map.of("decision", decision));
    }

    private String saldo(String existencia) {
        return admin.get("/inventario/existencias/" + existencia).json().get("cantidadActual").asString();
    }

    private int contar(String tabla) {
        return sql.queryForObject("SELECT count(*) FROM sivant." + tabla, Integer.class);
    }

    @Nested
    @DisplayName("RF16 · Consultar inventario")
    class Consultar {

        @Test
        @DisplayName("CP-54 Lista insumos y reventa con unidad, existencia, mínimo y nivel bajo; filtra y busca")
        void existencias() {
            admin.post("/productos", Map.of("nombre", "Agua", "descripcion", "x", "precioVenta", "20", "tipo",
                    "REVENTA", "unidadMedida", "PIEZA", "nivelMinimo", "0"));
            mover(cafe, "ENTRADA_MANUAL", "500.25");
            var r = almacen.get("/inventario/existencias");
            assertThat(r.json().get("totalElementos").asInt()).isEqualTo(3);
            var fila = almacen.get("/inventario/existencias?q=CAFÉ").json().get("contenido").get(0);
            assertThat(fila.get("cantidadActual").asString()).isEqualTo("500.25");
            assertThat(fila.get("nivelMinimo").asString()).isEqualTo("10");
            assertThat(fila.get("unidadMedida").asString()).isEqualTo("GRAMO");
            assertThat(fila.get("nivelBajo").asBoolean()).isFalse();
            assertThat(almacen.get("/inventario/existencias?tipo=PRODUCTO").json().get("totalElementos").asInt())
                    .isEqualTo(1);
            assertThat(almacen.get("/inventario/existencias?nivelBajo=true").json().get("totalElementos").asInt())
                    .as("Leche y Agua: cantidad igual o menor al mínimo").isEqualTo(2);
            assertThat(admin.get("/inventario/existencias?q=%25").json().get("totalElementos").asInt()).isZero();
            assertThat(admin.get("/inventario/existencias/999999").estado()).isEqualTo(404);
        }

        @Test
        @DisplayName("CP-55 El historial filtra por artículo, tipo y día en la zona del negocio")
        void historial() {
            mover(cafe, "ENTRADA_MANUAL", "10");
            mover(cafe, "SALIDA_MANUAL", "4");
            mover(leche, "ENTRADA_MANUAL", "7");
            var r = admin.get("/inventario/existencias/" + cafe + "/movimientos");
            assertThat(r.json().get("totalElementos").asInt()).isEqualTo(2);
            var ultimo = r.json().get("contenido").get(0);
            assertThat(ultimo.get("tipo").asString()).isEqualTo("SALIDA_MANUAL");
            assertThat(ultimo.get("cambio").asString()).isEqualTo("-4");
            assertThat(ultimo.get("saldoAnterior").asString()).isEqualTo("10");
            assertThat(ultimo.get("saldoPosterior").asString()).isEqualTo("6");
            assertThat(ultimo.get("empleado").asString()).isEqualTo("Persona ALMACENISTA");
            assertThat(total("q=leche")).isEqualTo(1);
            assertThat(total("tipo=ENTRADA_MANUAL")).isEqualTo(2);
            LocalDate hoy = LocalDate.ofInstant(reloj.instant(), ZoneId.of("America/Mexico_City"));
            assertThat(total("desde=" + hoy.minusDays(1) + "&hasta=" + hoy)).isEqualTo(3);
            assertThat(total("hasta=" + hoy.minusDays(2))).isZero();
            assertThat(admin.get("/inventario/movimientos?desde=" + hoy + "&hasta=" + hoy.minusDays(1)).campo("hasta"))
                    .isNotBlank();
            assertThat(admin.get("/inventario/movimientos?desde=ayer").estado()).isEqualTo(400);
        }

        private int total(String filtro) {
            return admin.get("/inventario/movimientos?" + filtro).json().get("totalElementos").asInt();
        }

        @Test
        @DisplayName("CP-56 El límite del día usa la medianoche de Ciudad de México")
        void limiteDelDia() {
            mover(cafe, "ENTRADA_MANUAL", "1");
            // 05:59:59Z del 11 de marzo son las 23:59:59 del día 10 en Ciudad de México.
            sql.update("UPDATE sivant.movimiento_inventario SET fecha_hora = ?::timestamptz",
                    Instant.parse("2026-03-11T05:59:59Z").toString());
            assertThat(total("desde=2026-03-10&hasta=2026-03-10")).isEqualTo(1);
            assertThat(total("desde=2026-03-11&hasta=2026-03-11")).isZero();
        }
    }

    @Nested
    @DisplayName("RF17 · Registrar movimiento de inventario")
    class Movimientos {

        @Test
        @DisplayName("CP-57 Entradas y salidas actualizan saldo, versión, historial y auditoría")
        void entradaYSalida() {
            var entrada = mover(cafe, "ENTRADA_MANUAL", "100.5");
            assertThat(entrada.estado()).isEqualTo(201);
            assertThat(entrada.json().get("existencia").get("cantidadActual").asString()).isEqualTo("100.5");
            assertThat(entrada.json().get("existencia").get("version").asInt()).isEqualTo(1);
            assertThat(entrada.json().get("movimiento").get("saldoPosterior").asString()).isEqualTo("100.5");
            assertThat(mover(cafe, "SALIDA_MANUAL", "100.5").estado()).isEqualTo(201);
            assertThat(saldo(cafe)).isEqualTo("0");
            assertThat(sql.queryForObject("SELECT count(*) FROM sivant.auditoria WHERE accion = 'MOVIMIENTO_REGISTRADO'",
                    Integer.class)).isEqualTo(2);
        }

        @Test
        @DisplayName("CP-58 Una salida mayor que lo disponible se rechaza y el saldo queda intacto")
        void salidaExcedida() {
            mover(cafe, "ENTRADA_MANUAL", "5");
            var r = mover(cafe, "SALIDA_MANUAL", "5.000001");
            assertThat(r.estado()).isEqualTo(409);
            assertThat(r.codigo()).isEqualTo("EXISTENCIA_INSUFICIENTE");
            assertThat(saldo(cafe)).isEqualTo("5");
            assertThat(contar("movimiento_inventario")).isEqualTo(1);
        }

        @Test
        @DisplayName("CP-59 Salidas simultáneas nunca exceden la existencia")
        void salidasSimultaneas() throws Exception {
            mover(cafe, "ENTRADA_MANUAL", "10");
            List<Callable<Integer>> tareas = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                tareas.add(() -> mover(cafe, "SALIDA_MANUAL", "4").estado());
            }
            assertThat(GestionEmpleadosTest.simultaneas(tareas)).containsExactlyInAnyOrder(201, 201, 409, 409, 409, 409);
            assertThat(saldo(cafe)).isEqualTo("2");
        }

        @Test
        @DisplayName("CP-60 Una existencia negativa se recupera con entradas")
        void recuperaNegativa() {
            sql.update("UPDATE sivant.existencia_inventario SET cantidad_actual = -3 WHERE id_existencia = ?",
                    Long.valueOf(cafe));
            assertThat(mover(cafe, "SALIDA_MANUAL", "1").estado()).isEqualTo(409);
            assertThat(mover(cafe, "ENTRADA_MANUAL", "2").estado()).isEqualTo(201);
            assertThat(saldo(cafe)).isEqualTo("-1");
        }

        @Test
        @DisplayName("CP-61 Datos inválidos, clave ausente o existencia inexistente")
        void invalidos() {
            for (String cantidad : List.of("0", "0.00", "-1", "abc", "1.1234567", "")) {
                assertThat(mover(cafe, "ENTRADA_MANUAL", cantidad).campo("cantidad")).as(cantidad).isNotBlank();
            }
            assertThat(mover(cafe, "AJUSTE", "1").estado()).isEqualTo(400);
            assertThat(mover(cafe, "CONSUMO_VENTA", "1").estado()).isEqualTo(400);
            assertThat(mover("999999", "ENTRADA_MANUAL", "1").estado()).isEqualTo(404);
            assertThat(mover(cafe, "ENTRADA_MANUAL", "999999999999").estado()).isEqualTo(201);
            assertThat(mover(cafe, "ENTRADA_MANUAL", "1").campo("cantidad")).isNotBlank();
            assertThat(almacen.post("/inventario/existencias/" + cafe + "/movimientos",
                    Map.of("tipo", "ENTRADA_MANUAL", "cantidad", "1")).campo("Idempotency-Key")).isNotBlank();
        }

        @Test
        @DisplayName("CP-62 Repetir con la misma clave no aplica dos veces; con otros datos, 409")
        void idempotencia() throws Exception {
            String clave = clave();
            var primera = mover(clave, cafe, "ENTRADA_MANUAL", "8");
            var repetida = mover(clave, cafe, "ENTRADA_MANUAL", "8.00");
            assertThat(repetida.estado()).isEqualTo(201);
            assertThat(repetida.texto()).isEqualTo(primera.texto());
            assertThat(mover(clave, cafe, "ENTRADA_MANUAL", "9").codigo()).isEqualTo("IDEMPOTENCIA_CONFLICTO");
            assertThat(saldo(cafe)).isEqualTo("8");

            String simultanea = clave();
            List<Callable<Integer>> tareas = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                tareas.add(() -> mover(simultanea, leche, "ENTRADA_MANUAL", "3").estado());
            }
            assertThat(GestionEmpleadosTest.simultaneas(tareas)).containsOnly(201);
            assertThat(saldo(leche)).isEqualTo("3");
        }

        @Test
        @DisplayName("CP-63 Una clave cuya operación falló puede reintentarse")
        void claveLibre() {
            String clave = clave();
            assertThat(mover(clave, cafe, "SALIDA_MANUAL", "2").estado()).isEqualTo(409);
            mover(cafe, "ENTRADA_MANUAL", "2");
            assertThat(mover(clave, cafe, "SALIDA_MANUAL", "2").estado()).isEqualTo(201);
        }
    }

    @Nested
    @DisplayName("RF18 · Ajustar inventario")
    class Solicitar {

        @Test
        @DisplayName("CP-64 La solicitud queda pendiente y no cambia la existencia")
        void pendiente() {
            mover(cafe, "ENTRADA_MANUAL", "20");
            var r = solicitar(cafe, "15.5", " Conteo físico ");
            assertThat(r.estado()).isEqualTo(201);
            assertThat(r.json().get("estado").asString()).isEqualTo("PENDIENTE");
            assertThat(r.json().get("cantidadAnterior").asString()).isEqualTo("20");
            assertThat(r.json().get("cantidadSolicitada").asString()).isEqualTo("15.5");
            assertThat(r.json().get("justificacion").asString()).isEqualTo("Conteo físico");
            assertThat(r.json().has("administrador")).isFalse();
            assertThat(saldo(cafe)).isEqualTo("20");
            assertThat(admin.get("/inventario/ajustes?estado=PENDIENTE&existenciaId=" + cafe).json()
                    .get("totalElementos").asInt()).isEqualTo(1);
            assertThat(admin.get("/inventario/ajustes?estado=AUTORIZADA").json().get("totalElementos").asInt()).isZero();
        }

        @Test
        @DisplayName("CP-65 Sin diferencia, sin justificación, cantidad negativa o existencia inexistente: 400")
        void invalida() {
            mover(cafe, "ENTRADA_MANUAL", "20");
            assertThat(solicitar(cafe, "20.000", "Igual").campo("cantidadSolicitada")).isNotBlank();
            assertThat(solicitar(cafe, "5", "  ").campo("justificacion")).isNotBlank();
            assertThat(solicitar(cafe, "-5", "x").campo("cantidadSolicitada")).isNotBlank();
            assertThat(solicitar("999999", "5", "x").campo("existenciaId")).isNotBlank();
            assertThat(almacen.post("/inventario/ajustes", Map.of("existenciaId", cafe, "cantidadSolicitada", "1",
                    "justificacion", "x")).campo("Idempotency-Key")).isNotBlank();
            assertThat(contar("solicitud_ajuste")).isZero();
        }
    }

    @Nested
    @DisplayName("RF15 · Autorizar ajuste de inventario")
    class Resolver {

        String solicitud;

        @BeforeEach
        void solicitud() {
            mover(cafe, "ENTRADA_MANUAL", "20");
            solicitud = solicitar(cafe, "15", "Conteo físico").json().get("id").asString();
        }

        @Test
        @DisplayName("CP-66 Autorizar fija el saldo solicitado y registra un movimiento auditable")
        void autorizar() {
            var r = resolver(solicitud, "AUTORIZADA");
            assertThat(r.estado()).isEqualTo(200);
            assertThat(r.json().get("estado").asString()).isEqualTo("AUTORIZADA");
            assertThat(r.json().get("administrador").asString()).isEqualTo("Persona ADMINISTRADOR");
            assertThat(r.json().get("fechaHoraResolucion").asString()).isNotBlank();
            assertThat(saldo(cafe)).isEqualTo("15");
            var mov = admin.get("/inventario/movimientos?tipo=AJUSTE").json().get("contenido").get(0);
            assertThat(mov.get("cambio").asString()).isEqualTo("-5");
            assertThat(mov.get("solicitudId").asString()).isEqualTo(solicitud);
            assertThat(mov.get("motivo").asString()).isEqualTo("Conteo físico");
        }

        @Test
        @DisplayName("CP-67 Rechazar conserva el saldo y queda en el historial con responsable")
        void rechazar() {
            assertThat(resolver(solicitud, "RECHAZADA").json().get("estado").asString()).isEqualTo("RECHAZADA");
            assertThat(saldo(cafe)).isEqualTo("20");
            var fila = admin.get("/inventario/existencias/" + cafe + "/movimientos?tipo=AJUSTE_RECHAZADO").json()
                    .get("contenido").get(0);
            assertThat(fila.get("cambio").asString()).isEqualTo("-5");
            assertThat(fila.has("saldoPosterior")).isFalse();
            assertThat(fila.get("empleado").asString()).isEqualTo("Persona ADMINISTRADOR");
            assertThat(sql.queryForObject("SELECT count(*) FROM sivant.movimiento_inventario WHERE tipo = 'AJUSTE'",
                    Integer.class)).isZero();
        }

        @Test
        @DisplayName("CP-68 Si la existencia cambió, autorizar da 409; se puede rechazar y pedir otra")
        void existenciaCambiada() {
            mover(cafe, "ENTRADA_MANUAL", "1");
            mover(cafe, "SALIDA_MANUAL", "1");
            assertThat(admin.get("/inventario/ajustes/" + solicitud).json().get("existenciaCambiada").asBoolean())
                    .isTrue();
            assertThat(resolver(solicitud, "AUTORIZADA").codigo()).isEqualTo("EXISTENCIA_CAMBIADA");
            assertThat(admin.get("/inventario/ajustes/" + solicitud).json().get("estado").asString())
                    .isEqualTo("PENDIENTE");
            assertThat(saldo(cafe)).isEqualTo("20");
            assertThat(resolver(solicitud, "RECHAZADA").estado()).isEqualTo(200);
            String nueva = solicitar(cafe, "15", "Nuevo conteo").json().get("id").asString();
            assertThat(resolver(nueva, "AUTORIZADA").estado()).isEqualTo(200);
            assertThat(saldo(cafe)).isEqualTo("15");
        }

        @Test
        @DisplayName("CP-69 Resoluciones simultáneas: una se aplica y hay a lo sumo un movimiento")
        void simultaneas() throws Exception {
            ClienteApi otroAdmin = sesionDe(crear("ADMINISTRADOR"));
            String ruta = "/inventario/ajustes/" + solicitud + "/resolucion";
            List<Integer> estados = GestionEmpleadosTest.simultaneas(List.of(
                    () -> admin.postConClave(ruta, clave(), Map.of("decision", "AUTORIZADA")).estado(),
                    () -> otroAdmin.postConClave(ruta, clave(), Map.of("decision", "AUTORIZADA")).estado(),
                    () -> admin.postConClave(ruta, clave(), Map.of("decision", "RECHAZADA")).estado()));
            assertThat(estados).containsExactlyInAnyOrder(200, 409, 409);
            assertThat(sql.queryForObject("SELECT count(*) FROM sivant.movimiento_inventario WHERE tipo = 'AJUSTE'",
                    Integer.class)).isLessThanOrEqualTo(1);
        }

        @Test
        @DisplayName("CP-70 No se resuelve dos veces, ni con decisión inválida, ni una inexistente")
        void invalida() {
            assertThat(resolver(solicitud, "PENDIENTE").estado()).isEqualTo(400);
            assertThat(resolver("999999", "AUTORIZADA").estado()).isEqualTo(404);
            resolver(solicitud, "AUTORIZADA");
            assertThat(resolver(solicitud, "RECHAZADA").codigo()).isEqualTo("AJUSTE_RESUELTO");
            assertThat(saldo(cafe)).isEqualTo("15");
        }
    }

    @ParameterizedTest(name = "CP-71 Permisos de inventario para {0}")
    @ValueSource(strings = {"MESERO", "CAJERO", "COCINERO", "ALMACENISTA", "ADMINISTRADOR"})
    void permisos(String rol) {
        mover(cafe, "ENTRADA_MANUAL", "20");
        String solicitud = solicitar(cafe, "15", "Conteo").json().get("id").asString();
        ClienteApi c = sesionDe(crear(rol));
        boolean consulta = rol.equals("ALMACENISTA") || rol.equals("ADMINISTRADOR");
        for (String ruta : List.of("existencias", "existencias/" + cafe, "existencias/" + cafe + "/movimientos",
                "movimientos", "ajustes", "ajustes/" + solicitud)) {
            assertThat(c.get("/inventario/" + ruta).estado()).as(ruta).isEqualTo(consulta ? 200 : 403);
        }
        boolean opera = rol.equals("ALMACENISTA");
        assertThat(c.postConClave("/inventario/existencias/" + leche + "/movimientos", clave(),
                Map.of("tipo", "ENTRADA_MANUAL", "cantidad", "1")).estado()).isEqualTo(opera ? 201 : 403);
        assertThat(c.postConClave("/inventario/ajustes", clave(), Map.of("existenciaId", leche,
                "cantidadSolicitada", "9", "justificacion", "x")).estado()).isEqualTo(opera ? 201 : 403);
        assertThat(c.postConClave("/inventario/ajustes/" + solicitud + "/resolucion", clave(),
                Map.of("decision", "RECHAZADA")).estado()).isEqualTo(rol.equals("ADMINISTRADOR") ? 200 : 403);
        assertThat(saldo(cafe)).isEqualTo("20");
    }

    @Test
    @DisplayName("CP-72 La sesión de un almacenista desactivado no puede mover inventario")
    void desactivado() {
        var e = crear("ALMACENISTA");
        ClienteApi c = sesionDe(e);
        admin.post("/empleados/" + e.getId() + "/desactivacion", Map.of("confirmado", true));
        assertThat(c.postConClave("/inventario/existencias/" + cafe + "/movimientos", clave(),
                Map.of("tipo", "ENTRADA_MANUAL", "cantidad", "1")).estado()).isEqualTo(401);
        assertThat(saldo(cafe)).isEqualTo("0");
    }

    @Nested
    @DisplayName("RF16 y RF17 · Causa de salida y nivel mínimo")
    class CausaYMinimo {

        private ClienteApi.Respuesta salida(Map<String, Object> cuerpo) {
            return almacen.postConClave("/inventario/existencias/" + cafe + "/movimientos", clave(), cuerpo);
        }

        @Test
        @DisplayName("CP-172 Toda salida declara su causa; «Otro» exige describirla; las entradas no llevan causa")
        void causa() {
            mover(cafe, "ENTRADA_MANUAL", "50");
            assertThat(salida(Map.of("tipo", "SALIDA_MANUAL", "cantidad", "1")).campo("causa")).isNotBlank();
            assertThat(salida(Map.of("tipo", "SALIDA_MANUAL", "cantidad", "1", "causa", "OTRO")).campo("nota"))
                    .isNotBlank();
            assertThat(salida(Map.of("tipo", "SALIDA_MANUAL", "cantidad", "1", "causa", "OTRO", "nota", "   "))
                    .campo("nota")).isNotBlank();
            assertThat(salida(Map.of("tipo", "SALIDA_MANUAL", "cantidad", "1", "causa", "ROBO")).estado()).isEqualTo(400);
            assertThat(salida(Map.of("tipo", "ENTRADA_MANUAL", "cantidad", "1", "causa", "MERMA")).campo("causa"))
                    .isNotBlank();
            assertThat(salida(Map.of("tipo", "SALIDA_MANUAL", "cantidad", "1", "causa", "OTRO", "nota", "x".repeat(301)))
                    .campo("nota")).isNotBlank();
            assertThat(saldo(cafe)).as("ningún intento cambió el saldo").isEqualTo("50");

            var r = salida(Map.of("tipo", "SALIDA_MANUAL", "cantidad", "2", "causa", "CONSUMO_PERSONAL",
                    "nota", " Comida del turno "));
            assertThat(r.estado()).as(r.texto()).isEqualTo(201);
            assertThat(r.json().get("movimiento").get("causa").asString()).isEqualTo("CONSUMO_PERSONAL");
            assertThat(r.json().get("movimiento").get("motivo").asString()).isEqualTo("Comida del turno");
            assertThat(salida(Map.of("tipo", "SALIDA_MANUAL", "cantidad", "1", "causa", "OTRO", "nota", "Muestra"))
                    .estado()).isEqualTo(201);
            var historial = almacen.get("/inventario/existencias/" + cafe + "/movimientos?tipo=SALIDA_MANUAL").json()
                    .get("contenido");
            assertThat(historial.get(0).get("causa").asString()).isEqualTo("OTRO");
            assertThat(historial.get(1).get("causa").asString()).isEqualTo("CONSUMO_PERSONAL");
            assertThat(sql.queryForObject("SELECT detalle ->> 'causa' FROM sivant.auditoria WHERE accion = "
                    + "'MOVIMIENTO_REGISTRADO' ORDER BY id_auditoria DESC LIMIT 1", String.class)).isEqualTo("OTRO");
            assertThatThrownBy(() -> sql.update("UPDATE sivant.movimiento_inventario SET causa = NULL WHERE tipo = "
                    + "'SALIDA_MANUAL'")).isInstanceOf(DataIntegrityViolationException.class);
            assertThatThrownBy(() -> sql.update("UPDATE sivant.movimiento_inventario SET motivo = NULL WHERE causa = "
                    + "'OTRO'")).isInstanceOf(DataIntegrityViolationException.class);
        }

        @Test
        @DisplayName("CP-173 El almacenista cambia el nivel mínimo: recalcula el nivel bajo sin cambiar saldo ni versión")
        void nivelMinimo() {
            // El insumo de prueba inicia con mínimo 10: con 20 no está bajo.
            mover(cafe, "ENTRADA_MANUAL", "20");
            JsonNode antes = almacen.get("/inventario/existencias/" + cafe).json();
            String solicitud = solicitar(cafe, "8", "Conteo").json().get("id").asString();
            assertThat(antes.get("nivelBajo").asBoolean()).isFalse();

            var r = almacen.patch("/inventario/existencias/" + cafe, Map.of("nivelMinimo", "25.5"));

            assertThat(r.estado()).as(r.texto()).isEqualTo(200);
            assertThat(r.json().get("nivelMinimo").asString()).isEqualTo("25.5");
            assertThat(r.json().get("nivelBajo").asBoolean()).isTrue();
            assertThat(r.json().get("cantidadActual").asString()).isEqualTo("20");
            assertThat(r.json().get("version").asInt()).isEqualTo(antes.get("version").asInt());
            assertThat(almacen.get("/inventario/existencias?nivelBajo=true").texto()).contains("Café en grano");
            assertThat(sql.queryForObject("SELECT detalle ->> 'nuevo' FROM sivant.auditoria WHERE accion = "
                    + "'NIVEL_MINIMO_EDITADO'", String.class)).isEqualTo("25.5");
            assertThat(admin.postConClave("/inventario/ajustes/" + solicitud + "/resolucion", clave(),
                    Map.of("decision", "AUTORIZADA")).estado()).as("la solicitud pendiente sigue vigente").isEqualTo(200);

            almacen.patch("/inventario/existencias/" + cafe, Map.of("nivelMinimo", "25.5"));
            assertThat(sql.queryForObject("SELECT count(*) FROM sivant.auditoria WHERE accion = 'NIVEL_MINIMO_EDITADO'",
                    Integer.class)).as("sin cambio no se audita").isEqualTo(1);
            for (String valor : List.of("-1", "abc", "", "1.0000001")) {
                assertThat(almacen.patch("/inventario/existencias/" + cafe, Map.of("nivelMinimo", valor))
                        .campo("nivelMinimo")).as(valor).isNotBlank();
            }
            assertThat(almacen.patch("/inventario/existencias/999999", Map.of("nivelMinimo", "1")).estado())
                    .isEqualTo(404);
            assertThat(admin.patch("/inventario/existencias/" + cafe, Map.of("nivelMinimo", "1")).estado())
                    .isEqualTo(403);
            assertThat(almacen.patch("/inventario/existencias/" + cafe, Map.of("nivelMinimo", "0")).json()
                    .get("nivelBajo").asBoolean()).isFalse();
        }
    }
}
