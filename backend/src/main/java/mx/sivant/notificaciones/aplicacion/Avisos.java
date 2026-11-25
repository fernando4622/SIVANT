package mx.sivant.notificaciones.aplicacion;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import mx.sivant.notificaciones.aplicacion.EventoOperacion.Dirigido;
import mx.sivant.notificaciones.dominio.TipoNotificacion;
import mx.sivant.notificaciones.infraestructura.NotificacionesJdbc;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Interfaz que notificaciones ofrece a los demás módulos. Se usa dentro de la transacción de negocio. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class Avisos {

    public static final String TEMA_COCINA = "/topic/cocina";
    public static final String TEMA_INVENTARIO = "/topic/inventario";

    private final NotificacionesJdbc notificaciones;
    private final ApplicationEventPublisher eventos;
    private final Clock reloj;

    public Avisos(NotificacionesJdbc notificaciones, ApplicationEventPublisher eventos, Clock reloj) {
        this.notificaciones = notificaciones;
        this.eventos = eventos;
        this.reloj = reloj;
    }

    public void notificarRolPorComanda(String rol, TipoNotificacion tipo, Long comanda, String mensaje,
            Map<String, ?> contexto) {
        notificaciones.insertarPorComanda(comanda, null, rol, tipo, mensaje, contexto);
    }

    public void notificarRolPorExistencia(String rol, TipoNotificacion tipo, Long existencia, String mensaje,
            Map<String, ?> contexto) {
        notificaciones.insertarPorExistencia(existencia, rol, tipo, mensaje, contexto);
    }

    public void notificarEmpleadoPorComanda(Long empleado, TipoNotificacion tipo, Long comanda, String mensaje,
            Map<String, ?> contexto) {
        notificaciones.insertarPorComanda(comanda, empleado, null, tipo, mensaje, contexto);
    }

    public void publicarEnTema(String tema, String tipo, Long recurso, int version) {
        eventos.publishEvent(new Dirigido(evento(tipo, recurso, version), tema, null));
    }

    public void publicarAEmpleado(Long empleado, String tipo, Long recurso, int version) {
        eventos.publishEvent(new Dirigido(evento(tipo, recurso, version), null, empleado));
    }

    private EventoOperacion evento(String tipo, Long recurso, int version) {
        return new EventoOperacion(UUID.randomUUID().toString(), tipo, recurso.toString(), version,
                OffsetDateTime.now(reloj));
    }
}
