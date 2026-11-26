package mx.sivant.auditoria.aplicacion;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Guarda quién cambió qué y cuándo. El detalle nunca debe incluir contraseñas ni hashes. */
@Service
public class RegistroAuditoria {

    private final JdbcTemplate sql;
    private final JsonMapper json;
    private final Clock reloj;

    public RegistroAuditoria(JdbcTemplate sql, JsonMapper json, Clock reloj) {
        this.sql = sql;
        this.json = json;
        this.reloj = reloj;
    }

    /**
     * Exige una transacción abierta: el cambio y su auditoría se confirman o se revierten juntos.
     *
     * @param actor empleado responsable, o nulo si el cambio lo hace el sistema al arrancar
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void registrar(Long actor, String accion, String entidad, Long idRegistro, Map<String, ?> detalle) {
        sql.update("""
                INSERT INTO sivant.auditoria (fecha_hora, id_empleado, accion, entidad, id_registro, detalle)
                VALUES (?, ?, ?, ?, ?, ?::jsonb)
                """, OffsetDateTime.now(reloj), actor, accion, entidad, idRegistro, json.writeValueAsString(detalle));
    }
}
