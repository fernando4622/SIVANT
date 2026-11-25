package mx.sivant.notificaciones.aplicacion;

import java.time.OffsetDateTime;
import mx.sivant.notificaciones.dominio.TipoNotificacion;

/** `contexto` es el objeto JSON guardado con el aviso. */
public record DatosNotificacion(Long id, TipoNotificacion tipo, String mensaje, OffsetDateTime fechaHora,
        boolean leida, Long comandaId, String contexto) {
}
