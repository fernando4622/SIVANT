package mx.sivant;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * RNF01 a RNF03. Se ejecuta aparte porque carga datos de varios años: {@code -Dexcluir=ninguna -Dgroups=rendimiento}.
 * Los tiempos se miden por HTTP, del lado del cliente, y quedan en {@code target/rendimiento.txt}.
 */
@Tag("rendimiento")
@DisplayName("Fase 7 · Rendimiento")
class RendimientoTest extends PruebaServicio {

    /** Unas 200 ventas al mes durante 24 meses son ~4 800. Se cargan 10 veces más. */
    static final int VENTAS = 50_000;
    static final int MOVIMIENTOS = 100_000;
    static final long LIMITE_CRUD = 1_500;
    static final long LIMITE_HISTORIAL = 2_000;
    static final Path REPORTE = Path.of("target", "rendimiento.txt");

    private final Map<String, List<Long>> tiempos = new ConcurrentHashMap<>();

    // --- Datos

    /**
     * Los datos son coherentes por construcción. Las validaciones por fila se suspenden durante la carga (con tablas
     * recién llenadas, sin estadísticas, cada una recorrería la tabla completa) y después se comprueba que cuadren.
     */
    private void cargarHistoria() {
        transaccion.executeWithoutResult(t -> {
            sql.execute("SET LOCAL session_replication_role = replica");
            insertarHistoria();
        });
        sql.execute("ANALYZE");
        assertThat(sql.queryForObject("""
                SELECT count(*) FROM sivant.venta v JOIN sivant.cuenta c USING (id_cuenta)
                LEFT JOIN sivant.pago p USING (id_venta)
                WHERE p.id_pago IS NULL OR p.monto <> v.total OR p.id_mesero <> c.id_mesero OR c.estado <> 'CERRADA'
                   OR NOT EXISTS (SELECT 1 FROM sivant.comanda o JOIN sivant.detalle_comanda d USING (id_comanda)
                                  WHERE o.id_cuenta = c.id_cuenta)
                """, Integer.class)).as("la historia cargada cuadra").isZero();
    }

    private void insertarHistoria() {
        Long mesa = Long.valueOf(mesa1);
        sql.update("""
                WITH c AS (
                    INSERT INTO sivant.cuenta (id_mesa, id_mesero, fecha_hora_apertura, fecha_hora_cierre, estado, version)
                    SELECT ?, CASE WHEN g % 2 = 0 THEN ? ELSE ? END, t - interval '40 minutes', t, 'CERRADA', 1
                    FROM (SELECT g, now() - (g * interval '1 minute') * (730.0 * 24 * 60 / ?) AS t
                          FROM generate_series(1, ?) g) s
                    RETURNING id_cuenta, id_mesero, fecha_hora_cierre),
                o AS (
                    INSERT INTO sivant.comanda (id_cuenta, fecha_hora_envio, estado, version)
                    SELECT id_cuenta, fecha_hora_cierre - interval '30 minutes', 'SERVIDA', 3 FROM c
                    RETURNING id_comanda),
                d AS (
                    INSERT INTO sivant.detalle_comanda (id_comanda, id_producto, cantidad, precio_unitario)
                    SELECT id_comanda, ?, 2, 20.00 FROM o RETURNING id_detalle),
                v AS (
                    INSERT INTO sivant.venta (id_cuenta, fecha_hora, total)
                    SELECT id_cuenta, fecha_hora_cierre, 40.00 FROM c RETURNING id_venta, id_cuenta, fecha_hora)
                INSERT INTO sivant.pago (id_venta, id_mesero, fecha_hora, monto, metodo, referencia_externa)
                SELECT v.id_venta, c.id_mesero, v.fecha_hora, 40.00,
                       (ARRAY['EFECTIVO', 'TARJETA', 'TRANSFERENCIA'])[1 + v.id_venta % 3],
                       CASE WHEN v.id_venta % 3 = 1 THEN 'R' || v.id_venta END
                FROM v JOIN c USING (id_cuenta)
                """, mesa, juan.getId(), otroMeseroId(), VENTAS, VENTAS, Long.valueOf(agua));
        Long existencia = sql.queryForObject(
                "SELECT id_existencia FROM sivant.existencia_producto WHERE id_producto_reventa = ?", Long.class,
                Long.valueOf(agua));
        sql.update("""
                INSERT INTO sivant.movimiento_inventario (id_existencia, id_empleado, tipo, cantidad, saldo_anterior,
                    saldo_posterior, fecha_hora)
                SELECT ?, ?, 'ENTRADA_MANUAL', 1, g - 1, g, now() - (g * interval '7 minutes')
                FROM generate_series(1, ?) g
                """, existencia, administrador.getId(), MOVIMIENTOS);
    }

    private Long otroMeseroId() {
        return sql.queryForObject("SELECT id_empleado FROM sivant.empleado WHERE correo = 'ana@sivant.test'", Long.class);
    }

    // --- Medición

    private ClienteApi.Respuesta medir(String operacion, Supplier<ClienteApi.Respuesta> solicitud) {
        long inicio = System.nanoTime();
        ClienteApi.Respuesta r = solicitud.get();
        long ms = (System.nanoTime() - inicio) / 1_000_000;
        assertThat(r.estado()).as(operacion + ": " + r.texto()).isBetween(200, 299);
        tiempos.computeIfAbsent(operacion, k -> Collections.synchronizedList(new ArrayList<>())).add(ms);
        return r;
    }

    private static long percentil(List<Long> valores, double p) {
        List<Long> orden = new ArrayList<>(valores);
        Collections.sort(orden);
        return orden.get(Math.min(orden.size() - 1, (int) Math.ceil(p * orden.size()) - 1));
    }

    private void reportar(String escenario, long limite) throws IOException {
        StringBuilder texto = new StringBuilder("\n## " + escenario + " (límite p95 " + limite + " ms)\n");
        texto.append(String.format("%-38s %6s %6s %6s %6s%n", "operación", "n", "p50", "p95", "máx"));
        tiempos.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> texto.append(String.format(
                "%-38s %6d %6d %6d %6d%n", e.getKey(), e.getValue().size(), percentil(e.getValue(), 0.5),
                percentil(e.getValue(), 0.95), Collections.max(e.getValue()))));
        Files.createDirectories(REPORTE.getParent());
        Files.writeString(REPORTE, texto, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        System.out.print(texto);
        tiempos.forEach((operacion, valores) -> assertThat(percentil(valores, 0.95)).as(operacion)
                .isLessThan(limite));
        tiempos.clear();
    }

    private static void simultaneos(List<Callable<Void>> usuarios) throws Exception {
        CyclicBarrier salida = new CyclicBarrier(usuarios.size());
        try (var hilos = Executors.newFixedThreadPool(usuarios.size())) {
            List<Future<Void>> resultados = new ArrayList<>();
            for (Callable<Void> usuario : usuarios) {
                resultados.add(hilos.submit(() -> {
                    salida.await();
                    return usuario.call();
                }));
            }
            for (Future<Void> r : resultados) {
                r.get();
            }
        }
    }

    // --- Escenarios

    @Test
    @DisplayName("CP-168 Historial de ventas e inventario con 50 000 ventas y 100 000 movimientos: p95 menor a 2 s")
    void historial() throws Exception {
        cargarHistoria();
        String hoy = java.time.LocalDate.now(java.time.ZoneId.of("America/Mexico_City")).toString();
        String haceUnMes = java.time.LocalDate.now(java.time.ZoneId.of("America/Mexico_City")).minusMonths(1).toString();
        String venta = sql.queryForObject("SELECT max(id_venta) FROM sivant.venta", Long.class).toString();
        for (int i = 0; i < 20; i++) {
            medir("ventas sin filtros", () -> admin.get("/ventas"));
            medir("ventas por mesero y método", () -> cajero.get("/ventas?meseroId=" + juan.getId()
                    + "&metodo=TARJETA"));
            medir("ventas del último mes", () -> cajero.get("/ventas?desde=" + haceUnMes + "&hasta=" + hoy));
            medir("ventas última página", () -> admin.get("/ventas?page=1999&size=25"));
            medir("detalle de venta", () -> cajero.get("/ventas/" + venta));
            medir("opciones de filtro", () -> cajero.get("/ventas/opciones"));
            medir("movimientos de inventario", () -> admin.get("/inventario/movimientos"));
            medir("movimientos por día", () -> admin.get("/inventario/movimientos?desde=" + haceUnMes));
        }
        assertThat(admin.get("/ventas").json().get("totalElementos").asInt()).isEqualTo(VENTAS);
        reportar("Historial con " + VENTAS + " ventas y " + MOVIMIENTOS + " movimientos", LIMITE_HISTORIAL);
    }

    @Test
    @DisplayName("CP-169 Tres usuarios simultáneos en altas, consultas y ediciones: p95 menor a 1.5 s")
    void tresUsuarios() throws Exception {
        String cuenta = cuentaNueva();
        ClienteApi almacen = sesionDe(crear("ALMACENISTA"));
        Long existencia = sql.queryForObject(
                "SELECT id_existencia FROM sivant.existencia_producto WHERE id_producto_reventa = ?", Long.class,
                Long.valueOf(agua));
        int vueltas = 15;
        simultaneos(List.of(
                () -> {
                    for (int i = 0; i < vueltas; i++) {
                        medir("mesero: catálogo", () -> mesero.get("/productos?activo=true"));
                        var comanda = medir("mesero: enviar comanda", () -> enviar(mesero, cuenta, linea(agua, 1, null)));
                        String id = comanda.json().get("id").asString();
                        medir("mesero: editar comanda", () -> editar(mesero, id, 0,
                                detalle(comanda.json().get("detalles").get(0), 2)));
                        medir("mesero: consultar cuenta", () -> mesero.get("/cuentas/" + cuenta));
                    }
                    return null;
                },
                () -> {
                    for (int i = 0; i < vueltas; i++) {
                        int n = i;
                        var alta = medir("administrador: alta de producto", () -> admin.post("/productos", Map.of(
                                "nombre", "Producto " + n, "descripcion", "x", "precioVenta", "10.00", "tipo", "REVENTA",
                                "unidadMedida", "PIEZA", "nivelMinimo", "0")));
                        String id = alta.json().get("id").asString();
                        medir("administrador: editar producto", () -> admin.patch("/productos/" + id,
                                Map.of("precioVenta", "12.00")));
                        medir("administrador: consultar empleados", () -> admin.get("/empleados"));
                        medir("administrador: alta de empleado", () -> admin.post("/empleados", Map.of("nombreCompleto",
                                "Empleado " + n, "correo", "e" + n + "@sivant.test", "contrasena", CONTRASENA, "rol",
                                "MESERO")));
                    }
                    return null;
                },
                () -> {
                    for (int i = 0; i < vueltas; i++) {
                        medir("almacenista: existencias", () -> almacen.get("/inventario/existencias"));
                        medir("almacenista: registrar entrada", () -> almacen.postConClave("/inventario/existencias/"
                                + existencia + "/movimientos", clave(), Map.of("tipo", "ENTRADA_MANUAL", "cantidad", "5")));
                        medir("almacenista: historial de la existencia", () -> almacen.get("/inventario/existencias/"
                                + existencia + "/movimientos"));
                    }
                    return null;
                }));
        reportar("Tres usuarios simultáneos", LIMITE_CRUD);
    }

    @Test
    @DisplayName("CP-170 Cuatro usuarios con cocina y almacén sobre la historia de dos años: p95 menor a 1.5 s, sin errores")
    void cuatroUsuarios() throws Exception {
        cargarHistoria();
        String cuenta = cuentaNueva();
        ClienteApi almacen = sesionDe(crear("ALMACENISTA"));
        Long existencia = sql.queryForObject(
                "SELECT id_existencia FROM sivant.existencia_producto WHERE id_producto_reventa = ?", Long.class,
                Long.valueOf(agua));
        int vueltas = 15;
        List<String> enviadas = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < vueltas; i++) {
            enviadas.add(enviar(mesero, cuenta, linea(galleta, 1, null)).json().get("id").asString());
        }
        simultaneos(List.of(
                () -> {
                    for (int i = 0; i < vueltas; i++) {
                        medir("mesero: mesas", () -> mesero.get("/mesas"));
                        medir("mesero: enviar comanda", () -> enviar(mesero, cuenta, linea(agua, 1, null)));
                        medir("mesero: consultar cuenta", () -> mesero.get("/cuentas/" + cuenta));
                    }
                    return null;
                },
                () -> {
                    for (String id : enviadas) {
                        medir("cocinero: cola", () -> cocinero.get("/cocina/cola"));
                        medir("cocinero: iniciar preparación", () -> preparar(id, 0));
                        medir("cocinero: marcar lista", () -> lista(id, 1));
                    }
                    return null;
                },
                () -> {
                    for (int i = 0; i < vueltas; i++) {
                        medir("almacenista: existencias", () -> almacen.get("/inventario/existencias"));
                        medir("almacenista: registrar entrada", () -> almacen.postConClave("/inventario/existencias/"
                                + existencia + "/movimientos", clave(), Map.of("tipo", "ENTRADA_MANUAL", "cantidad", "5")));
                        medir("almacenista: movimientos", () -> almacen.get("/inventario/movimientos"));
                    }
                    return null;
                },
                () -> {
                    for (int i = 0; i < vueltas; i++) {
                        medir("cajero: ventas", () -> cajero.get("/ventas"));
                        medir("cajero: efectivo por entregar", () -> cajero.get("/caja/pagos-efectivo-pendientes?meseroId="
                                + juan.getId()));
                    }
                    return null;
                }));
        assertThat(sql.queryForObject("SELECT count(*) FROM sivant.comanda WHERE estado = 'LISTA'", Integer.class))
                .isEqualTo(vueltas);
        reportar("Cuatro usuarios: mesero, cocinero, almacenista y cajero", LIMITE_CRUD);
    }
}
