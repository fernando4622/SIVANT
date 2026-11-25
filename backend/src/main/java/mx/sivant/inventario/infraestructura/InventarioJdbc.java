package mx.sivant.inventario.infraestructura;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import mx.sivant.infraestructura.api.Pagina;
import mx.sivant.inventario.aplicacion.DatosInventario.Existencia;
import mx.sivant.inventario.aplicacion.DatosInventario.Historial;
import mx.sivant.inventario.aplicacion.DatosInventario.Solicitud;
import mx.sivant.inventario.aplicacion.DatosInventario.TipoArticulo;
import mx.sivant.inventario.dominio.EstadoSolicitud;
import mx.sivant.inventario.dominio.TipoHistorial;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

/** Consultas de proyección y escrituras de inventario. Las escrituras requieren la fila bloqueada. */
@Repository
public class InventarioJdbc {

    private static final String ARTICULO = """
            FROM sivant.existencia_inventario e
            LEFT JOIN sivant.existencia_insumo ei ON ei.id_existencia = e.id_existencia
            LEFT JOIN sivant.existencia_producto ep ON ep.id_existencia = e.id_existencia
            JOIN sivant.articulo a ON a.id_articulo = coalesce(ei.id_insumo, ep.id_producto_reventa)
            """;

    private static final String EXISTENCIA = """
            SELECT e.id_existencia, a.id_articulo, a.nombre, CASE WHEN ei.id_insumo IS NULL THEN 'PRODUCTO' ELSE 'INSUMO' END,
                   a.unidad_medida, a.activo, e.cantidad_actual, e.nivel_minimo, e.fecha_actualizacion, e.version
            """ + ARTICULO;

    private static final String FILTRO_EXISTENCIA = """
            WHERE (CAST(:q AS text) IS NULL OR lower(a.nombre) LIKE :q ESCAPE '\\')
              AND (CAST(:tipo AS text) IS NULL OR (ei.id_insumo IS NULL) = (:tipo = 'PRODUCTO'))
              AND (CAST(:bajo AS boolean) IS NULL OR (e.cantidad_actual <= e.nivel_minimo) = :bajo)
            """;

    private static final String HISTORIAL = """
            FROM (
                SELECT m.id_movimiento AS id, m.tipo, m.id_existencia, m.saldo_posterior - m.saldo_anterior AS cambio,
                       m.saldo_anterior, m.saldo_posterior, m.fecha_hora, m.id_empleado,
                       m.id_solicitud_ajuste AS id_solicitud, d.id_comanda, m.motivo
                FROM sivant.movimiento_inventario m
                LEFT JOIN sivant.detalle_comanda d ON d.id_detalle = m.id_detalle_comanda
                UNION ALL
                SELECT s.id_solicitud, 'AJUSTE_RECHAZADO', s.id_existencia, s.cantidad_solicitada - s.cantidad_anterior,
                       s.cantidad_anterior, NULL, s.fecha_hora_resolucion, s.id_administrador,
                       s.id_solicitud, NULL, s.justificacion
                FROM sivant.solicitud_ajuste s WHERE s.estado = 'RECHAZADA'
            ) h
            JOIN sivant.empleado p ON p.id_empleado = h.id_empleado
            LEFT JOIN sivant.existencia_insumo ei ON ei.id_existencia = h.id_existencia
            LEFT JOIN sivant.existencia_producto ep ON ep.id_existencia = h.id_existencia
            JOIN sivant.articulo a ON a.id_articulo = coalesce(ei.id_insumo, ep.id_producto_reventa)
            WHERE (CAST(:existencia AS bigint) IS NULL OR h.id_existencia = :existencia)
              AND (CAST(:q AS text) IS NULL OR lower(a.nombre) LIKE :q ESCAPE '\\')
              AND (CAST(:tipo AS text) IS NULL OR h.tipo = :tipo)
              AND (CAST(:desde AS timestamptz) IS NULL OR h.fecha_hora >= :desde)
              AND (CAST(:hasta AS timestamptz) IS NULL OR h.fecha_hora < :hasta)
            """;

    private static final String SOLICITUD = """
            SELECT s.id_solicitud, s.id_existencia, a.nombre, a.unidad_medida, s.cantidad_anterior, s.cantidad_solicitada,
                   e.cantidad_actual, s.justificacion, s.estado, al.nombre_completo, s.fecha_hora_solicitud,
                   ad.nombre_completo, s.fecha_hora_resolucion,
                   s.estado = 'PENDIENTE' AND s.version_existencia_solicitada <> e.version
            """ + ARTICULO + """
            JOIN sivant.solicitud_ajuste s ON s.id_existencia = e.id_existencia
            JOIN sivant.empleado al ON al.id_empleado = s.id_almacenista
            LEFT JOIN sivant.empleado ad ON ad.id_empleado = s.id_administrador
            """;

    private final JdbcClient sql;
    private final Clock reloj;

    public InventarioJdbc(JdbcClient sql, Clock reloj) {
        this.sql = sql;
        this.reloj = reloj;
    }

    /** Estado de la solicitud, bajo bloqueo. */
    public record SolicitudBloqueada(Long existenciaId, BigDecimal cantidadSolicitada, int versionSolicitada,
            String justificacion, EstadoSolicitud estado) {
    }

    public Pagina<Existencia> existencias(String q, TipoArticulo tipo, Boolean nivelBajo, int pagina, int tamano) {
        long total = sql.sql("SELECT count(*) " + ARTICULO + FILTRO_EXISTENCIA).param("q", patron(q))
                .param("tipo", nombre(tipo)).param("bajo", nivelBajo).query(Long.class).single();
        List<Existencia> filas = sql.sql(EXISTENCIA + FILTRO_EXISTENCIA
                        + "ORDER BY lower(a.nombre), e.id_existencia LIMIT :limite OFFSET :salto")
                .param("q", patron(q)).param("tipo", nombre(tipo)).param("bajo", nivelBajo)
                .param("limite", tamano).param("salto", (long) pagina * tamano)
                .query(InventarioJdbc::existencia).list();
        return Pagina.de(filas, pagina, tamano, total);
    }

    public Optional<Existencia> existencia(Long id) {
        return sql.sql(EXISTENCIA + "WHERE e.id_existencia = ?").param(id).query(InventarioJdbc::existencia).optional();
    }

    public Optional<Existencia> bloquearExistencia(Long id) {
        return sql.sql(EXISTENCIA + "WHERE e.id_existencia = ? FOR UPDATE OF e").param(id)
                .query(InventarioJdbc::existencia).optional();
    }

    public void cambiarSaldo(Long id, BigDecimal saldo) {
        sql.sql("""
                UPDATE sivant.existencia_inventario
                SET cantidad_actual = ?, version = version + 1, fecha_actualizacion = ? WHERE id_existencia = ?
                """).params(saldo, OffsetDateTime.now(reloj), id).update();
    }

    public Long insertarMovimiento(Long existencia, Long empleado, Long solicitud, TipoHistorial tipo,
            BigDecimal anterior, BigDecimal posterior, String motivo) {
        return sql.sql("""
                INSERT INTO sivant.movimiento_inventario (id_existencia, id_empleado, id_solicitud_ajuste, tipo, cantidad,
                    saldo_anterior, saldo_posterior, fecha_hora, motivo)
                VALUES (:existencia, :empleado, CAST(:solicitud AS bigint), :tipo, :cantidad, :anterior, :posterior,
                    :fecha, CAST(:motivo AS text)) RETURNING id_movimiento
                """).param("existencia", existencia).param("empleado", empleado).param("solicitud", solicitud)
                .param("tipo", tipo.name()).param("cantidad", posterior.subtract(anterior).abs())
                .param("anterior", anterior).param("posterior", posterior)
                .param("fecha", OffsetDateTime.now(reloj)).param("motivo", motivo).query(Long.class).single();
    }

    public void insertarConsumo(Long existencia, Long empleado, Long detalle, BigDecimal anterior,
            BigDecimal posterior) {
        sql.sql("""
                INSERT INTO sivant.movimiento_inventario (id_existencia, id_empleado, id_detalle_comanda, tipo, cantidad,
                    saldo_anterior, saldo_posterior, fecha_hora)
                VALUES (?, ?, ?, 'CONSUMO_VENTA', ?, ?, ?, ?)
                """).params(existencia, empleado, detalle, anterior.subtract(posterior), anterior, posterior,
                OffsetDateTime.now(reloj)).update();
    }

    public Map<Long, Long> existenciasDeInsumos(Collection<Long> insumos) {
        return existenciasPor("SELECT id_insumo, id_existencia FROM sivant.existencia_insumo WHERE id_insumo IN (:ids)",
                insumos);
    }

    public Map<Long, Long> existenciasDeProductos(Collection<Long> productos) {
        return existenciasPor("SELECT id_producto_reventa, id_existencia FROM sivant.existencia_producto "
                + "WHERE id_producto_reventa IN (:ids)", productos);
    }

    private Map<Long, Long> existenciasPor(String consulta, Collection<Long> ids) {
        Map<Long, Long> existencias = new HashMap<>();
        if (!ids.isEmpty()) {
            sql.sql(consulta).param("ids", ids).query(fila -> {
                existencias.put(fila.getLong(1), fila.getLong(2));
            });
        }
        return existencias;
    }

    public Pagina<Historial> historial(Long existencia, String q, TipoHistorial tipo, OffsetDateTime desde,
            OffsetDateTime hasta, int pagina, int tamano) {
        long total = filtrar("SELECT count(*) " + HISTORIAL, existencia, q, tipo, desde, hasta).query(Long.class).single();
        List<Historial> filas = filtrar(SELECT_HISTORIAL + HISTORIAL
                + "ORDER BY h.fecha_hora DESC, h.tipo, h.id DESC LIMIT :limite OFFSET :salto",
                existencia, q, tipo, desde, hasta)
                .param("limite", tamano).param("salto", (long) pagina * tamano)
                .query(InventarioJdbc::historial).list();
        return Pagina.de(filas, pagina, tamano, total);
    }

    private static final String SELECT_HISTORIAL = """
            SELECT h.id, h.tipo, h.id_existencia, a.nombre, a.unidad_medida, h.cambio, h.saldo_anterior, h.saldo_posterior,
                   h.fecha_hora, p.nombre_completo, h.id_solicitud, h.id_comanda, h.motivo
            """;

    public Historial movimiento(Long id) {
        return filtrar(SELECT_HISTORIAL + HISTORIAL + "AND h.id = :id AND h.tipo <> 'AJUSTE_RECHAZADO'",
                null, null, null, null, null).param("id", id).query(InventarioJdbc::historial).single();
    }

    public Long insertarSolicitud(Existencia existencia, Long almacenista, BigDecimal solicitada, String justificacion) {
        return sql.sql("""
                INSERT INTO sivant.solicitud_ajuste (id_existencia, id_almacenista, cantidad_anterior, cantidad_solicitada,
                    version_existencia_solicitada, justificacion, fecha_hora_solicitud)
                VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id_solicitud
                """).params(existencia.id(), almacenista, existencia.cantidadActual(), solicitada, existencia.version(),
                justificacion, OffsetDateTime.now(reloj)).query(Long.class).single();
    }

    public Optional<SolicitudBloqueada> bloquearSolicitud(Long id) {
        return sql.sql("""
                SELECT id_existencia, cantidad_solicitada, version_existencia_solicitada, justificacion, estado
                FROM sivant.solicitud_ajuste WHERE id_solicitud = ? FOR UPDATE
                """).param(id).query((f, n) -> new SolicitudBloqueada(f.getLong(1), f.getBigDecimal(2), f.getInt(3),
                f.getString(4), EstadoSolicitud.valueOf(f.getString(5)))).optional();
    }

    public void resolverSolicitud(Long id, EstadoSolicitud estado, Long administrador) {
        sql.sql("""
                UPDATE sivant.solicitud_ajuste SET estado = ?, id_administrador = ?, fecha_hora_resolucion = ?
                WHERE id_solicitud = ?
                """).params(estado.name(), administrador, OffsetDateTime.now(reloj), id).update();
    }

    public Optional<Solicitud> solicitud(Long id) {
        return sql.sql(SOLICITUD + "WHERE s.id_solicitud = ?").param(id).query(InventarioJdbc::solicitud).optional();
    }

    public Pagina<Solicitud> solicitudes(EstadoSolicitud estado, Long existencia, int pagina, int tamano) {
        String filtro = """
                WHERE (CAST(:estado AS text) IS NULL OR s.estado = :estado)
                  AND (CAST(:existencia AS bigint) IS NULL OR s.id_existencia = :existencia)
                """;
        long total = sql.sql("SELECT count(*) FROM sivant.solicitud_ajuste s " + filtro).param("estado", nombre(estado))
                .param("existencia", existencia).query(Long.class).single();
        List<Solicitud> filas = sql.sql(SOLICITUD + filtro
                        + "ORDER BY s.fecha_hora_solicitud DESC, s.id_solicitud DESC LIMIT :limite OFFSET :salto")
                .param("estado", nombre(estado)).param("existencia", existencia)
                .param("limite", tamano).param("salto", (long) pagina * tamano)
                .query(InventarioJdbc::solicitud).list();
        return Pagina.de(filas, pagina, tamano, total);
    }

    private JdbcClient.StatementSpec filtrar(String consulta, Long existencia, String q, TipoHistorial tipo,
            OffsetDateTime desde, OffsetDateTime hasta) {
        return sql.sql(consulta).param("existencia", existencia).param("q", patron(q)).param("tipo", nombre(tipo))
                .param("desde", desde).param("hasta", hasta);
    }

    private static Existencia existencia(ResultSet f, int n) throws SQLException {
        return new Existencia(f.getLong(1), f.getLong(2), f.getString(3), TipoArticulo.valueOf(f.getString(4)),
                f.getString(5), f.getBoolean(6), f.getBigDecimal(7), f.getBigDecimal(8),
                f.getObject(9, OffsetDateTime.class), f.getInt(10));
    }

    private static Historial historial(ResultSet f, int n) throws SQLException {
        return new Historial(f.getLong(1), TipoHistorial.valueOf(f.getString(2)), f.getLong(3), f.getString(4),
                f.getString(5), f.getBigDecimal(6), f.getBigDecimal(7), f.getBigDecimal(8),
                f.getObject(9, OffsetDateTime.class), f.getString(10), f.getObject(11, Long.class), f.getObject(12, Long.class),
                f.getString(13));
    }

    private static Solicitud solicitud(ResultSet f, int n) throws SQLException {
        return new Solicitud(f.getLong(1), f.getLong(2), f.getString(3), f.getString(4), f.getBigDecimal(5),
                f.getBigDecimal(6), f.getBigDecimal(7), f.getString(8), EstadoSolicitud.valueOf(f.getString(9)),
                f.getString(10), f.getObject(11, OffsetDateTime.class), f.getString(12),
                f.getObject(13, OffsetDateTime.class), f.getBoolean(14));
    }

    private static String patron(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        return "%" + q.strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
                + "%";
    }

    private static String nombre(Enum<?> valor) {
        return valor == null ? null : valor.name();
    }
}
