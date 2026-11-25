package mx.sivant.infraestructura.idempotencia;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Repetir una solicitud con la misma clave devuelve el resultado guardado sin aplicarla de nuevo. */
@Service
public class Idempotencia {

    public static final String ENCABEZADO = "Idempotency-Key";
    private static final Pattern CLAVE = Pattern.compile("[A-Za-z0-9_-]{8,100}");

    private final JdbcClient sql;
    private final JsonMapper json;
    private final Clock reloj;

    public Idempotencia(JdbcClient sql, JsonMapper json, Clock reloj) {
        this.sql = sql;
        this.json = json;
        this.reloj = reloj;
    }

    private record Guardada(String huella, String respuesta) {
    }

    /** La fila se confirma junto con la operación: si esta falla, la clave queda libre para reintentar. */
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T ejecutar(Long actor, String operacion, String recurso, String clave, Object cuerpo, Class<T> tipo,
            Supplier<T> accion) {
        if (clave == null || !CLAVE.matcher(clave).matches()) {
            throw ReglaNegocioException.invalido(ENCABEZADO, "Envía una clave de 8 a 100 letras, números o guiones.");
        }
        String huella = huella(json.writeValueAsString(cuerpo));
        // Si otra transacción usa la misma clave, espera a que termine.
        int nueva = sql.sql("""
                INSERT INTO sivant.idempotencia_operacion (id_empleado, operacion, recurso, clave, huella, fecha_hora)
                VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT DO NOTHING
                """).params(actor, operacion, recurso, clave, huella, OffsetDateTime.now(reloj)).update();
        if (nueva == 0) {
            Guardada previa = sql.sql("""
                    SELECT huella, respuesta FROM sivant.idempotencia_operacion
                    WHERE id_empleado = ? AND operacion = ? AND recurso = ? AND clave = ?
                    """).params(actor, operacion, recurso, clave).query(Guardada.class).single();
            if (!previa.huella().equals(huella)) {
                throw ReglaNegocioException.conflicto("IDEMPOTENCIA_CONFLICTO",
                        "La clave ya se usó con otros datos.");
            }
            return json.readValue(previa.respuesta(), tipo);
        }
        T resultado = accion.get();
        sql.sql("""
                UPDATE sivant.idempotencia_operacion SET respuesta = ?
                WHERE id_empleado = ? AND operacion = ? AND recurso = ? AND clave = ?
                """).params(json.writeValueAsString(resultado), actor, operacion, recurso, clave).update();
        return resultado;
    }

    private static String huella(String cuerpo) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(cuerpo.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
