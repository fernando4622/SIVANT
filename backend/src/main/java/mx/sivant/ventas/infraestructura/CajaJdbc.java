package mx.sivant.ventas.infraestructura;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import mx.sivant.ventas.aplicacion.DatosCaja.Entrega;
import mx.sivant.ventas.aplicacion.DatosCaja.PagoEfectivo;
import mx.sivant.ventas.aplicacion.DatosCaja.PagoRecibido;
import mx.sivant.ventas.dominio.EstadoVenta;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class CajaJdbc {

    private static final String PAGOS = """
            SELECT p.id_pago, p.id_venta, m.numero, p.id_mesero, e.nombre_completo, p.fecha_hora, p.monto, v.estado
            FROM sivant.pago p
            JOIN sivant.venta v USING (id_venta)
            JOIN sivant.cuenta c USING (id_cuenta)
            JOIN sivant.mesa m USING (id_mesa)
            JOIN sivant.empleado e ON e.id_empleado = p.id_mesero
            """;

    private final JdbcClient sql;

    public CajaJdbc(JdbcClient sql) {
        this.sql = sql;
    }

    public List<PagoEfectivo> pendientes(Long mesero) {
        return sql.sql(PAGOS + """
                WHERE p.metodo = 'EFECTIVO'
                  AND NOT EXISTS (SELECT 1 FROM sivant.entrega_efectivo_pago x WHERE x.id_pago = p.id_pago)
                  AND (CAST(:mesero AS bigint) IS NULL OR p.id_mesero = :mesero)
                ORDER BY p.fecha_hora DESC, p.id_pago DESC
                """).param("mesero", mesero).query((f, n) -> pago(f)).list();
    }

    /** Bloquea los pagos en orden de ID: dos recepciones simultáneas no se cruzan. */
    public List<Long> bloquearPagos(Collection<Long> ids) {
        return sql.sql("SELECT id_pago FROM sivant.pago WHERE id_pago IN (:ids) ORDER BY id_pago FOR UPDATE")
                .param("ids", ids).query(Long.class).list();
    }

    /** Solo los que son en efectivo. */
    public List<PagoEfectivo> pagosEnEfectivo(Collection<Long> ids) {
        return sql.sql(PAGOS + "WHERE p.metodo = 'EFECTIVO' AND p.id_pago IN (:ids) ORDER BY p.fecha_hora, p.id_pago")
                .param("ids", ids).query((f, n) -> pago(f)).list();
    }

    public List<Long> entregados(Collection<Long> ids) {
        return sql.sql("SELECT id_pago FROM sivant.entrega_efectivo_pago WHERE id_pago IN (:ids)").param("ids", ids)
                .query(Long.class).list();
    }

    private static PagoEfectivo pago(ResultSet f) throws SQLException {
        return new PagoEfectivo(f.getLong(1), f.getLong(2), f.getInt(3), f.getLong(4), f.getString(5),
                f.getObject(6, OffsetDateTime.class), f.getBigDecimal(7), EstadoVenta.valueOf(f.getString(8)));
    }

    public Long insertarEntrega(Long mesero, Long cajero, BigDecimal monto, BigDecimal cambio, OffsetDateTime fecha) {
        return sql.sql("""
                INSERT INTO sivant.entrega_efectivo (id_mesero, id_cajero, monto, monto_fondo, fecha_hora)
                VALUES (?, ?, ?, ?, ?) RETURNING id_entrega
                """).params(mesero, cajero, monto, cambio, fecha).query(Long.class).single();
    }

    public void asociar(Long entrega, Long pago) {
        sql.sql("INSERT INTO sivant.entrega_efectivo_pago (id_entrega, id_pago) VALUES (?, ?)").params(entrega, pago)
                .update();
    }

    /** Solo si hubo cambio. */
    public void registrarCambio(Long entrega, Long pago, Long cajero, BigDecimal recibido, BigDecimal cambio,
            OffsetDateTime fecha) {
        sql.sql("""
                INSERT INTO sivant.fondo_cambio (id_entrega, id_pago, id_cajero, monto_recibido, monto, fecha_hora)
                VALUES (?, ?, ?, ?, ?, ?)
                """).params(entrega, pago, cajero, recibido, cambio, fecha).update();
    }

    public Entrega entrega(Long id) {
        record Fila(Long id, OffsetDateTime fecha, String mesero, String cajero, BigDecimal monto, BigDecimal cambio) {
        }
        Fila fila = sql.sql("""
                SELECT e.id_entrega, e.fecha_hora, m.nombre_completo, c.nombre_completo, e.monto, e.monto_fondo
                FROM sivant.entrega_efectivo e
                JOIN sivant.empleado m ON m.id_empleado = e.id_mesero
                JOIN sivant.empleado c ON c.id_empleado = e.id_cajero
                WHERE e.id_entrega = ?
                """).param(id).query((f, n) -> new Fila(f.getLong(1), f.getObject(2, OffsetDateTime.class),
                f.getString(3), f.getString(4), f.getBigDecimal(5), f.getBigDecimal(6))).single();
        List<PagoRecibido> pagos = sql.sql("""
                SELECT p.id_pago, p.id_venta, m.numero, p.id_mesero, e.nombre_completo, p.fecha_hora, p.monto, v.estado,
                       coalesce(fc.monto_recibido, p.monto), coalesce(fc.monto, 0)
                FROM sivant.pago p
                JOIN sivant.venta v USING (id_venta)
                JOIN sivant.cuenta c USING (id_cuenta)
                JOIN sivant.mesa m USING (id_mesa)
                JOIN sivant.empleado e ON e.id_empleado = p.id_mesero
                JOIN sivant.entrega_efectivo_pago x ON x.id_pago = p.id_pago
                LEFT JOIN sivant.fondo_cambio fc ON fc.id_pago = p.id_pago
                WHERE x.id_entrega = ? ORDER BY p.fecha_hora, p.id_pago
                """).param(id).query((f, n) -> new PagoRecibido(pago(f), f.getBigDecimal(9), f.getBigDecimal(10)))
                .list();
        return new Entrega(fila.id(), fila.fecha(), fila.mesero(), fila.cajero(), fila.monto(), fila.cambio(), pagos);
    }
}
