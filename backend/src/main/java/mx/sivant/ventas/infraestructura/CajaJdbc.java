package mx.sivant.ventas.infraestructura;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import mx.sivant.infraestructura.api.Pagina;
import mx.sivant.ventas.aplicacion.DatosCaja.Anulacion;
import mx.sivant.ventas.aplicacion.DatosCaja.Entrega;
import mx.sivant.ventas.aplicacion.DatosCaja.Fondo;
import mx.sivant.ventas.aplicacion.DatosCaja.PagoEfectivo;
import mx.sivant.ventas.dominio.EstadoFondo;
import mx.sivant.ventas.dominio.EstadoVenta;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CajaJdbc {

    private static final String FONDOS = """
            SELECT f.id_fondo_cambio, f.id_cuenta, m.numero, em.nombre_completo, ec.nombre_completo, f.monto, f.fecha_hora,
                   f.estado, p.id_venta, f.id_entrega, ea.nombre_completo, f.fecha_hora_anulacion, f.justificacion_anulacion
            FROM sivant.fondo_cambio f
            JOIN sivant.cuenta c USING (id_cuenta)
            JOIN sivant.mesa m USING (id_mesa)
            JOIN sivant.empleado em ON em.id_empleado = f.id_mesero
            JOIN sivant.empleado ec ON ec.id_empleado = f.id_cajero
            LEFT JOIN sivant.empleado ea ON ea.id_empleado = f.id_cajero_anulacion
            LEFT JOIN sivant.pago p ON p.id_pago = f.id_pago
            """;
    private static final String PAGOS = """
            SELECT p.id_pago, p.id_venta, m.numero, p.id_mesero, e.nombre_completo, p.fecha_hora, p.monto, v.estado,
                   f.id_fondo_cambio, f.monto
            FROM sivant.pago p
            JOIN sivant.venta v USING (id_venta)
            JOIN sivant.cuenta c USING (id_cuenta)
            JOIN sivant.mesa m USING (id_mesa)
            JOIN sivant.empleado e ON e.id_empleado = p.id_mesero
            LEFT JOIN sivant.fondo_cambio f ON f.id_pago = p.id_pago
            """;

    private final JdbcClient sql;

    public CajaJdbc(JdbcClient sql) {
        this.sql = sql;
    }

    // --- Fondo de cambio

    /** Vacío si la cuenta ya tiene un fondo vigente. */
    public Optional<Long> insertarFondo(Long cuenta, Long cajero, Long mesero, BigDecimal monto, OffsetDateTime fecha) {
        return sql.sql("""
                INSERT INTO sivant.fondo_cambio (id_cuenta, id_cajero, id_mesero, monto, fecha_hora)
                VALUES (?, ?, ?, ?, ?) ON CONFLICT DO NOTHING RETURNING id_fondo_cambio
                """).params(cuenta, cajero, mesero, monto, fecha).query(Long.class).optional();
    }

    public Optional<Long> pendienteDe(Long cuenta) {
        return sql.sql("SELECT id_fondo_cambio FROM sivant.fondo_cambio WHERE id_cuenta = ? AND estado = 'PENDIENTE'")
                .param(cuenta).query(Long.class).optional();
    }

    public void anular(Long fondo, Long cajero, OffsetDateTime fecha, String justificacion) {
        sql.sql("""
                UPDATE sivant.fondo_cambio SET estado = 'ANULADO', id_cajero_anulacion = ?, fecha_hora_anulacion = ?,
                    justificacion_anulacion = ?
                WHERE id_fondo_cambio = ?
                """).params(cajero, fecha, justificacion, fondo).update();
    }

    public void aplicarFondo(Long fondo, Long pago) {
        sql.sql("UPDATE sivant.fondo_cambio SET estado = 'APLICADO', id_pago = ? WHERE id_fondo_cambio = ?")
                .params(pago, fondo).update();
    }

    public Fondo fondo(Long id) {
        return sql.sql(FONDOS + "WHERE f.id_fondo_cambio = ?").param(id).query((f, n) -> fondo(f)).single();
    }

    public List<Fondo> fondosDe(Long cuenta) {
        return sql.sql(FONDOS + "WHERE f.id_cuenta = ? ORDER BY f.fecha_hora DESC, f.id_fondo_cambio DESC")
                .param(cuenta).query((f, n) -> fondo(f)).list();
    }

    public Pagina<Fondo> fondos(EstadoFondo estado, int pagina, int tamano) {
        String filtro = "WHERE (CAST(:estado AS text) IS NULL OR f.estado = :estado) ";
        String nombre = estado == null ? null : estado.name();
        long total = sql.sql("SELECT count(*) FROM sivant.fondo_cambio f " + filtro).param("estado", nombre)
                .query(Long.class).single();
        List<Fondo> filas = sql.sql(FONDOS + filtro
                        + "ORDER BY f.fecha_hora DESC, f.id_fondo_cambio DESC LIMIT :limite OFFSET :salto")
                .param("estado", nombre).param("limite", tamano).param("salto", (long) pagina * tamano)
                .query((f, n) -> fondo(f)).list();
        return Pagina.de(filas, pagina, tamano, total);
    }

    private static Fondo fondo(ResultSet f) throws SQLException {
        Anulacion anulacion = f.getString(13) == null ? null
                : new Anulacion(f.getString(11), f.getObject(12, OffsetDateTime.class), f.getString(13));
        return new Fondo(f.getLong(1), f.getLong(2), f.getInt(3), f.getString(4), f.getString(5), f.getBigDecimal(6),
                f.getObject(7, OffsetDateTime.class), EstadoFondo.valueOf(f.getString(8)), f.getObject(9, Long.class),
                f.getObject(10, Long.class), anulacion);
    }

    // --- Entrega de efectivo

    public List<PagoEfectivo> pendientes(Long mesero) {
        return sql.sql(PAGOS + """
                WHERE p.metodo = 'EFECTIVO'
                  AND NOT EXISTS (SELECT 1 FROM sivant.entrega_efectivo_pago x WHERE x.id_pago = p.id_pago)
                  AND (CAST(:mesero AS bigint) IS NULL OR p.id_mesero = :mesero)
                ORDER BY lower(e.nombre_completo), p.id_mesero, p.fecha_hora, p.id_pago
                """).param("mesero", mesero).query((f, n) -> pago(f)).list();
    }

    /** Bloquea los pagos en orden de ID: dos entregas simultáneas no se cruzan. */
    public List<Long> bloquearPagos(Collection<Long> ids) {
        return sql.sql("SELECT id_pago FROM sivant.pago WHERE id_pago IN (:ids) ORDER BY id_pago FOR UPDATE")
                .param("ids", ids).query(Long.class).list();
    }

    /** Solo los que son en efectivo. */
    public List<PagoEfectivo> pagosEnEfectivo(Collection<Long> ids) {
        return sql.sql(PAGOS + "WHERE p.metodo = 'EFECTIVO' AND p.id_pago IN (:ids) ORDER BY p.fecha_hora, p.id_pago").param("ids", ids)
                .query((f, n) -> pago(f)).list();
    }

    public List<Long> entregados(Collection<Long> ids) {
        return sql.sql("SELECT id_pago FROM sivant.entrega_efectivo_pago WHERE id_pago IN (:ids)").param("ids", ids)
                .query(Long.class).list();
    }

    private static PagoEfectivo pago(ResultSet f) throws SQLException {
        return new PagoEfectivo(f.getLong(1), f.getLong(2), f.getInt(3), f.getLong(4), f.getString(5),
                f.getObject(6, OffsetDateTime.class), f.getBigDecimal(7), EstadoVenta.valueOf(f.getString(8)),
                f.getObject(9, Long.class), f.getBigDecimal(10));
    }

    public Long insertarEntrega(Long mesero, Long cajero, BigDecimal monto, BigDecimal montoFondo, OffsetDateTime fecha) {
        return sql.sql("""
                INSERT INTO sivant.entrega_efectivo (id_mesero, id_cajero, monto, monto_fondo, fecha_hora)
                VALUES (?, ?, ?, ?, ?) RETURNING id_entrega
                """).params(mesero, cajero, monto, montoFondo, fecha).query(Long.class).single();
    }

    public void asociar(Long entrega, Collection<Long> pagos) {
        for (Long pago : pagos) {
            sql.sql("INSERT INTO sivant.entrega_efectivo_pago (id_entrega, id_pago) VALUES (?, ?)").params(entrega, pago)
                    .update();
        }
    }

    public void liquidar(Long entrega, Collection<Long> fondos) {
        if (!fondos.isEmpty()) {
            sql.sql("UPDATE sivant.fondo_cambio SET estado = 'LIQUIDADO', id_entrega = :entrega WHERE id_fondo_cambio "
                    + "IN (:fondos)").param("entrega", entrega).param("fondos", fondos).update();
        }
    }

    public Entrega entrega(Long id) {
        record Fila(Long id, OffsetDateTime fecha, String mesero, String cajero, BigDecimal monto, BigDecimal fondo) {
        }
        Fila fila = sql.sql("""
                SELECT e.id_entrega, e.fecha_hora, m.nombre_completo, c.nombre_completo, e.monto, e.monto_fondo
                FROM sivant.entrega_efectivo e
                JOIN sivant.empleado m ON m.id_empleado = e.id_mesero
                JOIN sivant.empleado c ON c.id_empleado = e.id_cajero
                WHERE e.id_entrega = ?
                """).param(id).query((f, n) -> new Fila(f.getLong(1), f.getObject(2, OffsetDateTime.class),
                f.getString(3), f.getString(4), f.getBigDecimal(5), f.getBigDecimal(6))).single();
        List<PagoEfectivo> pagos = sql.sql(PAGOS + """
                JOIN sivant.entrega_efectivo_pago x ON x.id_pago = p.id_pago
                WHERE x.id_entrega = ? ORDER BY p.fecha_hora, p.id_pago
                """).param(id).query((f, n) -> pago(f)).list();
        return new Entrega(fila.id(), fila.fecha(), fila.mesero(), fila.cajero(), fila.monto(), fila.fondo(), pagos);
    }
}
