package mx.sivant.servicio.infraestructura;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import mx.sivant.infraestructura.api.Pagina;
import mx.sivant.servicio.aplicacion.CuentasDeEmpleado;
import mx.sivant.servicio.aplicacion.DatosServicio.Cancelacion;
import mx.sivant.servicio.aplicacion.DatosServicio.Comanda;
import mx.sivant.servicio.aplicacion.DatosServicio.Cuenta;
import mx.sivant.servicio.aplicacion.DatosServicio.Detalle;
import mx.sivant.servicio.aplicacion.DatosServicio.EnCola;
import mx.sivant.servicio.aplicacion.DatosServicio.Mesa;
import mx.sivant.servicio.aplicacion.DatosServicio.ResumenCuenta;
import mx.sivant.servicio.dominio.EstadoComanda;
import mx.sivant.servicio.dominio.EstadoCuenta;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Consultas y escrituras de servicio. Las escrituras requieren la cuenta bloqueada. */
@Repository
public class ServicioJdbc implements CuentasDeEmpleado {

    private static final String TOTAL = """
            coalesce((SELECT sum(d.cantidad * d.precio_unitario) FROM sivant.comanda o
                JOIN sivant.detalle_comanda d USING (id_comanda)
                WHERE o.id_cuenta = c.id_cuenta AND o.estado <> 'CANCELADA'), 0)
            """;

    private final JdbcClient sql;
    private final Clock reloj;

    public ServicioJdbc(JdbcClient sql, Clock reloj) {
        this.sql = sql;
        this.reloj = reloj;
    }

    public record CuentaBloqueada(Long id, int mesa, Long meseroId, EstadoCuenta estado, int version) {
    }

    public record ComandaBloqueada(Long id, Long cuentaId, EstadoComanda estado, int version) {
    }

    @Override
    public int abiertas(Long idEmpleado) {
        return sql.sql("SELECT count(*) FROM sivant.cuenta WHERE id_mesero = ? AND estado = 'ABIERTA'")
                .param(idEmpleado).query(Integer.class).single();
    }

    // --- Mesas y cuentas

    public List<Mesa> mesas() {
        return sql.sql("""
                SELECT m.id_mesa, m.numero, c.id_cuenta, c.id_mesero, e.nombre_completo
                FROM sivant.mesa m
                LEFT JOIN sivant.cuenta c ON c.id_mesa = m.id_mesa AND c.estado = 'ABIERTA'
                LEFT JOIN sivant.empleado e ON e.id_empleado = c.id_mesero
                ORDER BY m.numero
                """).query((f, n) -> new Mesa(f.getLong(1), f.getInt(2), f.getObject(3, Long.class),
                f.getObject(4, Long.class), f.getString(5))).list();
    }

    public boolean existeMesa(Long mesa) {
        return sql.sql("SELECT count(*) FROM sivant.mesa WHERE id_mesa = ?").param(mesa).query(Long.class).single() > 0;
    }

    /** Vacío si la mesa ya tiene una cuenta abierta. Dos aperturas simultáneas las resuelve el índice parcial. */
    public Optional<Long> abrirCuenta(Long mesa, Long mesero) {
        return sql.sql("""
                INSERT INTO sivant.cuenta (id_mesa, id_mesero, fecha_hora_apertura) VALUES (?, ?, ?)
                ON CONFLICT (id_mesa) WHERE estado = 'ABIERTA' DO NOTHING RETURNING id_cuenta
                """).params(mesa, mesero, OffsetDateTime.now(reloj)).query(Long.class).optional();
    }

    public Pagina<ResumenCuenta> cuentas(Long mesero, EstadoCuenta estado, Long mesa, int pagina, int tamano) {
        String filtro = """
                FROM sivant.cuenta c
                JOIN sivant.mesa m USING (id_mesa)
                JOIN sivant.empleado e ON e.id_empleado = c.id_mesero
                WHERE (CAST(:mesero AS bigint) IS NULL OR c.id_mesero = :mesero)
                  AND (CAST(:estado AS text) IS NULL OR c.estado = :estado)
                  AND (CAST(:mesa AS bigint) IS NULL OR c.id_mesa = :mesa)
                """;
        String nombreEstado = estado == null ? null : estado.name();
        long total = sql.sql("SELECT count(*) " + filtro).param("mesero", mesero).param("estado", nombreEstado)
                .param("mesa", mesa).query(Long.class).single();
        List<ResumenCuenta> filas = sql.sql("""
                SELECT c.id_cuenta, m.numero, c.id_mesero, e.nombre_completo, c.estado, c.fecha_hora_apertura,
                       c.fecha_hora_cierre,
                """ + TOTAL + filtro + "ORDER BY c.fecha_hora_apertura DESC, c.id_cuenta DESC LIMIT :limite OFFSET :salto")
                .param("mesero", mesero).param("estado", nombreEstado).param("mesa", mesa)
                .param("limite", tamano).param("salto", (long) pagina * tamano)
                .query((f, n) -> new ResumenCuenta(f.getLong(1), f.getInt(2), f.getLong(3), f.getString(4),
                        EstadoCuenta.valueOf(f.getString(5)), f.getObject(6, OffsetDateTime.class),
                        f.getObject(7, OffsetDateTime.class), f.getBigDecimal(8))).list();
        return Pagina.de(filas, pagina, tamano, total);
    }

    public Optional<CuentaBloqueada> bloquearCuenta(Long id) {
        return sql.sql("""
                SELECT c.id_cuenta, m.numero, c.id_mesero, c.estado, c.version
                FROM sivant.cuenta c JOIN sivant.mesa m USING (id_mesa) WHERE c.id_cuenta = ? FOR UPDATE OF c
                """).param(id).query((f, n) -> new CuentaBloqueada(f.getLong(1), f.getInt(2), f.getLong(3),
                EstadoCuenta.valueOf(f.getString(4)), f.getInt(5))).optional();
    }

    public void subirVersionCuenta(Long id) {
        sql.sql("UPDATE sivant.cuenta SET version = version + 1 WHERE id_cuenta = ?").param(id).update();
    }

    public void cerrarCuenta(Long id) {
        sql.sql("""
                UPDATE sivant.cuenta SET estado = 'CERRADA', fecha_hora_cierre = ?, version = version + 1
                WHERE id_cuenta = ?
                """).params(OffsetDateTime.now(reloj), id).update();
    }

    public Optional<Cuenta> cuenta(Long id) {
        return sql.sql("""
                SELECT c.id_cuenta, c.id_mesa, m.numero, c.id_mesero, e.nombre_completo, c.estado, c.fecha_hora_apertura,
                       c.fecha_hora_cierre, c.version
                FROM sivant.cuenta c
                JOIN sivant.mesa m USING (id_mesa)
                JOIN sivant.empleado e ON e.id_empleado = c.id_mesero
                WHERE c.id_cuenta = ?
                """).param(id).query((f, n) -> new Cuenta(f.getLong(1), f.getLong(2), f.getInt(3), f.getLong(4),
                f.getString(5), EstadoCuenta.valueOf(f.getString(6)), f.getObject(7, OffsetDateTime.class),
                f.getObject(8, OffsetDateTime.class), f.getInt(9), comandasDe(f.getLong(1)))).optional();
    }

    // --- Comandas

    public Long insertarComanda(Long cuenta) {
        return sql.sql("INSERT INTO sivant.comanda (id_cuenta, fecha_hora_envio) VALUES (?, ?) RETURNING id_comanda")
                .params(cuenta, OffsetDateTime.now(reloj)).query(Long.class).single();
    }

    public void insertarDetalle(Long comanda, Long producto, int cantidad, BigDecimal precio, String observaciones) {
        sql.sql("""
                INSERT INTO sivant.detalle_comanda (id_comanda, id_producto, cantidad, precio_unitario, observaciones)
                VALUES (?, ?, ?, ?, ?)
                """).params(comanda, producto, cantidad, precio, observaciones).update();
    }

    public void cambiarCantidad(Long detalle, int cantidad) {
        sql.sql("UPDATE sivant.detalle_comanda SET cantidad = ? WHERE id_detalle = ?").params(cantidad, detalle).update();
    }

    public void eliminarDetalle(Long detalle) {
        sql.sql("DELETE FROM sivant.detalle_comanda WHERE id_detalle = ?").param(detalle).update();
    }

    public Optional<Long> cuentaDeComanda(Long comanda) {
        return sql.sql("SELECT id_cuenta FROM sivant.comanda WHERE id_comanda = ?").param(comanda).query(Long.class)
                .optional();
    }

    public ComandaBloqueada bloquearComanda(Long id) {
        return sql.sql("""
                SELECT id_comanda, id_cuenta, estado, version FROM sivant.comanda WHERE id_comanda = ? FOR UPDATE
                """).param(id).query((f, n) -> new ComandaBloqueada(f.getLong(1), f.getLong(2),
                EstadoComanda.valueOf(f.getString(3)), f.getInt(4))).single();
    }

    public void subirVersionComanda(Long id) {
        sql.sql("UPDATE sivant.comanda SET version = version + 1 WHERE id_comanda = ?").param(id).update();
    }

    /** Cambia el estado y registra la transición con su responsable. */
    public void transitar(ComandaBloqueada comanda, EstadoComanda nuevo, Long empleado) {
        sql.sql("UPDATE sivant.comanda SET estado = ?, version = version + 1 WHERE id_comanda = ?")
                .params(nuevo.name(), comanda.id()).update();
        registrarTransicion(comanda.id(), comanda.estado(), nuevo, empleado);
    }

    public void registrarTransicion(Long comanda, EstadoComanda anterior, EstadoComanda nuevo, Long empleado) {
        sql.sql("""
                INSERT INTO sivant.historial_estado_comanda (id_comanda, id_empleado, estado_anterior, estado_nuevo,
                    fecha_hora)
                VALUES (:comanda, :empleado, CAST(:anterior AS text), :nuevo, :fecha)
                """).param("comanda", comanda).param("empleado", empleado)
                .param("anterior", anterior == null ? null : anterior.name()).param("nuevo", nuevo.name())
                .param("fecha", OffsetDateTime.now(reloj)).update();
    }

    public void insertarCancelacion(Long comanda, Long cajero, String justificacion) {
        sql.sql("""
                INSERT INTO sivant.cancelacion_comanda (id_comanda, id_cajero, fecha_hora, justificacion)
                VALUES (?, ?, ?, ?)
                """).params(comanda, cajero, OffsetDateTime.now(reloj), justificacion).update();
    }

    public Comanda comanda(Long id) {
        return comandas("WHERE o.id_comanda = :id", id).getFirst();
    }

    private List<Comanda> comandasDe(Long cuenta) {
        return comandas("WHERE o.id_cuenta = :id", cuenta);
    }

    private List<Comanda> comandas(String filtro, Long id) {
        record Fila(Long id, Long cuenta, EstadoComanda estado, OffsetDateTime envio, int version, Cancelacion cancelacion) {
        }
        List<Fila> filas = sql.sql("""
                SELECT o.id_comanda, o.id_cuenta, o.estado, o.fecha_hora_envio, o.version,
                       e.nombre_completo, x.fecha_hora, x.justificacion
                FROM sivant.comanda o
                LEFT JOIN sivant.cancelacion_comanda x USING (id_comanda)
                LEFT JOIN sivant.empleado e ON e.id_empleado = x.id_cajero
                """ + filtro + " ORDER BY o.fecha_hora_envio, o.id_comanda").param("id", id)
                .query((f, n) -> new Fila(f.getLong(1), f.getLong(2), EstadoComanda.valueOf(f.getString(3)),
                        f.getObject(4, OffsetDateTime.class), f.getInt(5), f.getString(8) == null ? null
                        : new Cancelacion(f.getString(6), f.getObject(7, OffsetDateTime.class), f.getString(8))))
                .list();
        Map<Long, List<Detalle>> detalles = detalles(filas.stream().map(Fila::id).toList());
        return filas.stream().map(f -> new Comanda(f.id(), f.cuenta(), f.estado(), f.envio(), f.version(),
                detalles.getOrDefault(f.id(), List.of()), f.cancelacion())).toList();
    }

    /** Comandas vigentes en cocina, en orden de envío. */
    public List<EnCola> cola() {
        List<EnCola> filas = sql.sql("""
                SELECT o.id_comanda, o.id_cuenta, m.numero, e.nombre_completo, o.estado, o.fecha_hora_envio, o.version
                FROM sivant.comanda o
                JOIN sivant.cuenta c USING (id_cuenta)
                JOIN sivant.mesa m USING (id_mesa)
                JOIN sivant.empleado e ON e.id_empleado = c.id_mesero
                WHERE o.estado IN ('ENVIADA', 'EN_PREPARACION', 'LISTA')
                ORDER BY o.fecha_hora_envio, o.id_comanda
                """).query((f, n) -> new EnCola(f.getLong(1), f.getLong(2), f.getInt(3), f.getString(4),
                EstadoComanda.valueOf(f.getString(5)), f.getObject(6, OffsetDateTime.class), f.getInt(7), List.of())).list();
        Map<Long, List<Detalle>> detalles = detalles(filas.stream().map(EnCola::id).toList());
        return filas.stream().map(f -> new EnCola(f.id(), f.cuentaId(), f.mesa(), f.mesero(), f.estado(),
                f.fechaHoraEnvio(), f.version(), detalles.getOrDefault(f.id(), List.of()))).toList();
    }

    private Map<Long, List<Detalle>> detalles(Collection<Long> comandas) {
        if (comandas.isEmpty()) {
            return Map.of();
        }
        record Fila(Long comanda, Detalle detalle) {
        }
        return sql.sql("""
                SELECT d.id_comanda, d.id_detalle, d.id_producto, a.nombre, d.cantidad, d.precio_unitario, d.observaciones
                FROM sivant.detalle_comanda d JOIN sivant.articulo a ON a.id_articulo = d.id_producto
                WHERE d.id_comanda IN (:ids) ORDER BY d.id_detalle
                """).param("ids", comandas).query((f, n) -> new Fila(f.getLong(1), detalle(f))).list().stream()
                .collect(Collectors.groupingBy(Fila::comanda, Collectors.mapping(Fila::detalle, Collectors.toList())));
    }

    private static Detalle detalle(ResultSet f) throws SQLException {
        return new Detalle(f.getLong(2), f.getLong(3), f.getString(4), f.getInt(5), f.getBigDecimal(6), f.getString(7));
    }
}
