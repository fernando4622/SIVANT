package mx.sivant.identidad.infraestructura;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

/** Conexiones abiertas. Los mensajes salientes solo traen el ID de la conexión, no sus atributos. */
@Component
class SesionesWebSocket {

    private record Conexion(HttpSession sesion, EmpleadoPrincipal principal) {
    }

    private final Map<String, Conexion> conexiones = new ConcurrentHashMap<>();

    void registrar(String id, HttpSession sesion, EmpleadoPrincipal principal) {
        conexiones.put(id, new Conexion(sesion, principal));
    }

    boolean vigente(String id, ValidadorSesion validador) {
        Conexion conexion = id == null ? null : conexiones.get(id);
        return conexion != null && validador.validar(conexion.sesion(), conexion.principal()).vigente();
    }

    @EventListener
    void desconectar(SessionDisconnectEvent evento) {
        conexiones.remove(evento.getSessionId());
    }
}
