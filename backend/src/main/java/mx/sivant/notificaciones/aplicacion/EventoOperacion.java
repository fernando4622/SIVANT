package mx.sivant.notificaciones.aplicacion;

import java.time.OffsetDateTime;

/** Aviso de un cambio confirmado. El cliente obtiene el estado por REST. */
public record EventoOperacion(String eventoId, String tipo, String recursoId, int version, OffsetDateTime fechaHora) {

    /** Evento con su destino; se publica únicamente después de confirmar la transacción. */
    public record Dirigido(EventoOperacion evento, String tema, Long empleado) {
    }
}
