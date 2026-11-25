package mx.sivant.ventas.infraestructura;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import mx.sivant.infraestructura.api.Pagina;
import mx.sivant.ventas.aplicacion.DatosVentas.Cancelacion;
import mx.sivant.ventas.aplicacion.DatosVentas.Filtro;
import mx.sivant.ventas.aplicacion.DatosVentas.Opcion;
import mx.sivant.ventas.aplicacion.DatosVentas.Opciones;
import mx.sivant.ventas.aplicacion.DatosVentas.Pago;
import mx.sivant.ventas.aplicacion.DatosVentas.ResumenVenta;
import mx.sivant.ventas.aplicacion.DatosVentas.Venta;
import mx.sivant.ventas.dominio.EstadoVenta;
import mx.sivant.ventas.dominio.MetodoPago;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class VentasJdbc {

    private static final String DATOS = """
            FROM sivant.venta v
            JOIN sivant.pago p USING (id_venta)
            JOIN sivant.cuenta c USING (id_cuenta)
            JOIN sivant.mesa m USING (id_mesa)
            JOIN sivant.empleado e ON e.id_empleado = p.id_mesero
            """;
    private static final String FILTRO = DATOS + """
            WHERE v.fecha_hora >= :desde
              AND (CAST(:hasta AS timestamptz) IS NULL OR v.fecha_hora < :hasta)
              AND (CAST(:estado AS text) IS NULL OR v.estado = :estado)
              AND (CAST(:metodo AS text) IS NULL OR p.metodo = :metodo)
              AND (CAST(:mesero AS bigint) IS NULL OR p.id_mesero = :mesero)
              AND (CAST(:mesa AS bigint) IS NULL OR c.id_mesa = :mesa)
            """;

    private final JdbcClient sql;

    public VentasJdbc(JdbcClient sql) {
        this.sql = sql;
    }

    public Long insertarVenta(Long cuenta, OffsetDateTime fecha, BigDecimal total) {
        return sql.sql("INSERT INTO sivant.venta (id_cuenta, fecha_hora, total) VALUES (?, ?, ?) RETURNING id_venta")
                .params(cuenta, fecha, total).query(Long.class).single();
    }

    /** Falso si la referencia ya se usó ese día con ese método. */
    public boolean insertarPago(Long venta, Long mesero, OffsetDateTime fecha, BigDecimal monto, MetodoPago metodo,
            String referencia) {
        return sql.sql("""
                INSERT INTO sivant.pago (id_venta, id_mesero, fecha_hora, monto, metodo, referencia_externa)
                VALUES (:venta, :mesero, :fecha, :monto, :metodo, CAST(:referencia AS text)) ON CONFLICT DO NOTHING
                """).param("venta", venta).param("mesero", mesero).param("fecha", fecha).param("monto", monto)
                .param("metodo", metodo.name()).param("referencia", referencia).update() > 0;
    }

    public void bloquear(Long id) {
        sql.sql("SELECT id_venta FROM sivant.venta WHERE id_venta = ? FOR UPDATE").param(id).query(Long.class).optional();
    }

    public void marcarCancelada(Long id) {
        sql.sql("UPDATE sivant.venta SET estado = 'CANCELADA' WHERE id_venta = ?").param(id).update();
    }

    /** Falso si la referencia ya se usó ese día en otra devolución. */
    public boolean insertarCancelacion(Long venta, Long administrador, OffsetDateTime fecha, String justificacion,
            BigDecimal monto, String referencia) {
        return sql.sql("""
                INSERT INTO sivant.cancelacion_venta (id_venta, id_administrador, fecha_hora, justificacion,
                    monto_devuelto, referencia_reversion)
                VALUES (:venta, :administrador, :fecha, :justificacion, :monto, CAST(:referencia AS text))
                ON CONFLICT DO NOTHING
                """).param("venta", venta).param("administrador", administrador).param("fecha", fecha)
                .param("justificacion", justificacion).param("monto", monto).param("referencia", referencia)
                .update() > 0;
    }

    public Optional<Venta> venta(Long id) {
        return sql.sql("""
                SELECT v.id_venta, v.id_cuenta, m.numero, v.fecha_hora, v.total, v.estado,
                       p.id_pago, p.metodo, p.monto, p.referencia_externa, p.fecha_hora, e.nombre_completo,
                       a.nombre_completo, x.fecha_hora, x.justificacion, x.monto_devuelto, x.referencia_reversion
                """ + DATOS + """
                LEFT JOIN sivant.cancelacion_venta x USING (id_venta)
                LEFT JOIN sivant.empleado a ON a.id_empleado = x.id_administrador
                WHERE v.id_venta = ?
                """).param(id).query((f, n) -> venta(f)).optional();
    }

    private static Venta venta(ResultSet f) throws SQLException {
        Pago pago = new Pago(f.getLong(7), MetodoPago.valueOf(f.getString(8)), f.getBigDecimal(9), f.getString(10),
                f.getObject(11, OffsetDateTime.class), f.getString(12));
        Cancelacion cancelacion = f.getString(15) == null ? null : new Cancelacion(f.getString(13),
                f.getObject(14, OffsetDateTime.class), f.getString(15), f.getBigDecimal(16), f.getString(17));
        return new Venta(f.getLong(1), f.getLong(2), f.getInt(3), f.getObject(4, OffsetDateTime.class),
                f.getBigDecimal(5), EstadoVenta.valueOf(f.getString(6)), pago, cancelacion);
    }

    public Pagina<ResumenVenta> ventas(OffsetDateTime desde, OffsetDateTime hasta, Filtro filtro, int pagina,
            int tamano) {
        long total = filtrar("SELECT count(*) " + FILTRO, desde, hasta, filtro).query(Long.class).single();
        List<ResumenVenta> filas = filtrar("""
                SELECT v.id_venta, v.fecha_hora, m.numero, e.nombre_completo, p.metodo, v.total, v.estado
                """ + FILTRO + "ORDER BY v.fecha_hora DESC, v.id_venta DESC LIMIT :limite OFFSET :salto", desde, hasta,
                filtro).param("limite", tamano).param("salto", (long) pagina * tamano)
                .query((f, n) -> new ResumenVenta(f.getLong(1), f.getObject(2, OffsetDateTime.class), f.getInt(3),
                        f.getString(4), MetodoPago.valueOf(f.getString(5)), f.getBigDecimal(6),
                        EstadoVenta.valueOf(f.getString(7)))).list();
        return Pagina.de(filas, pagina, tamano, total);
    }

    private JdbcClient.StatementSpec filtrar(String consulta, OffsetDateTime desde, OffsetDateTime hasta, Filtro f) {
        return sql.sql(consulta).param("desde", desde).param("hasta", hasta)
                .param("estado", f.estado() == null ? null : f.estado().name())
                .param("metodo", f.metodo() == null ? null : f.metodo().name())
                .param("mesero", f.mesero()).param("mesa", f.mesa());
    }

    public Opciones opciones(OffsetDateTime desde) {
        return new Opciones(opciones("e.id_empleado, e.nombre_completo, lower(e.nombre_completo)", desde),
                opciones("m.id_mesa, 'Mesa ' || m.numero, m.numero", desde));
    }

    private List<Opcion> opciones(String columnas, OffsetDateTime desde) {
        return sql.sql("SELECT DISTINCT " + columnas + " AS orden " + DATOS + "WHERE v.fecha_hora >= ? ORDER BY orden, 1")
                .param(desde).query((f, n) -> new Opcion(f.getLong(1), f.getString(2))).list();
    }
}
