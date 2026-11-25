package mx.sivant.notificaciones.infraestructura;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import mx.sivant.infraestructura.api.Pagina;
import mx.sivant.notificaciones.aplicacion.DatosNotificacion;
import mx.sivant.notificaciones.dominio.TipoNotificacion;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

@Repository
public class NotificacionesJdbc {

    /** Dirigidas al empleado o a su rol vigente. */
    private static final String BANDEJA = """
            FROM sivant.notificacion n
            JOIN sivant.empleado e ON e.id_empleado = :empleado
            LEFT JOIN sivant.notificacion_lectura l
                ON l.id_notificacion = n.id_notificacion AND l.id_empleado = e.id_empleado
            WHERE (n.id_empleado_destino = e.id_empleado OR n.id_rol_destinatario = e.id_rol)
            """;

    private final JdbcClient sql;
    private final JsonMapper json;
    private final Clock reloj;

    public NotificacionesJdbc(JdbcClient sql, JsonMapper json, Clock reloj) {
        this.sql = sql;
        this.json = json;
        this.reloj = reloj;
    }

    public void insertarPorComanda(Long comanda, Long empleado, String rol, TipoNotificacion tipo, String mensaje,
            Map<String, ?> contexto) {
        sql.sql("""
                INSERT INTO sivant.notificacion (id_comanda, id_empleado_destino, id_rol_destinatario, tipo, mensaje,
                    fecha_hora, contexto)
                VALUES (:comanda, CAST(:empleado AS bigint),
                    (SELECT id_rol FROM sivant.rol WHERE nombre = CAST(:rol AS text)),
                    :tipo, :mensaje, :fecha, CAST(:contexto AS jsonb))
                """).param("comanda", comanda).param("empleado", empleado).param("rol", rol)
                .param("tipo", tipo.name()).param("mensaje", mensaje).param("fecha", OffsetDateTime.now(reloj))
                .param("contexto", json.writeValueAsString(contexto)).update();
    }

    public Pagina<DatosNotificacion> bandeja(Long empleado, Boolean leida, int pagina, int tamano) {
        String filtro = "AND (CAST(:leida AS boolean) IS NULL OR (l.id_empleado IS NOT NULL) = :leida)\n";
        long total = sql.sql("SELECT count(*) " + BANDEJA + filtro).param("empleado", empleado)
                .param("leida", leida).query(Long.class).single();
        List<DatosNotificacion> filas = sql.sql("""
                SELECT n.id_notificacion, n.tipo, n.mensaje, n.fecha_hora, l.id_empleado IS NOT NULL, n.id_comanda,
                       n.contexto::text
                """ + BANDEJA + filtro + "ORDER BY n.fecha_hora DESC, n.id_notificacion DESC LIMIT :limite OFFSET :salto")
                .param("empleado", empleado).param("leida", leida)
                .param("limite", tamano).param("salto", (long) pagina * tamano)
                .query((f, n) -> new DatosNotificacion(f.getLong(1), TipoNotificacion.valueOf(f.getString(2)),
                        f.getString(3), f.getObject(4, OffsetDateTime.class), f.getBoolean(5),
                        f.getObject(6, Long.class), f.getString(7))).list();
        return Pagina.de(filas, pagina, tamano, total);
    }

    /** Falso si el aviso no existe o no está dirigido al empleado. */
    public boolean marcarLeida(Long notificacion, Long empleado) {
        boolean propia = sql.sql("SELECT count(*) " + BANDEJA + "AND n.id_notificacion = :id")
                .param("empleado", empleado).param("id", notificacion).query(Long.class).single() > 0;
        if (propia) {
            sql.sql("""
                    INSERT INTO sivant.notificacion_lectura (id_notificacion, id_empleado, fecha_hora)
                    VALUES (?, ?, ?) ON CONFLICT DO NOTHING
                    """).params(notificacion, empleado, OffsetDateTime.now(reloj)).update();
        }
        return propia;
    }
}
