package mx.sivant.notificaciones.infraestructura;

import mx.sivant.notificaciones.aplicacion.EventoOperacion.Dirigido;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.MessagingException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Si la publicación falla, el aviso persistido sigue en la bandeja y el cliente lo recupera por REST. */
@Component
class PublicadorEventos {

    private static final Logger LOG = LoggerFactory.getLogger(PublicadorEventos.class);

    private final SimpMessagingTemplate mensajes;

    PublicadorEventos(SimpMessagingTemplate mensajes) {
        this.mensajes = mensajes;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void publicar(Dirigido dirigido) {
        try {
            if (dirigido.tema() != null) {
                mensajes.convertAndSend(dirigido.tema(), dirigido.evento());
            } else {
                mensajes.convertAndSendToUser(dirigido.empleado().toString(), "/queue/notificaciones",
                        dirigido.evento());
            }
        } catch (MessagingException e) {
            LOG.warn("No se pudo publicar el evento {}", dirigido.evento().eventoId(), e);
        }
    }
}
