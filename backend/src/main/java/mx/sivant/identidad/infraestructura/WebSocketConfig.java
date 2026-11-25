package mx.sivant.identidad.infraestructura;

import jakarta.servlet.http.HttpSession;
import java.util.Map;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.server.HandshakeInterceptor;

/**
 * Canal de eventos en `/ws`. La sesión HTTP autentica la conexión y solo se admite el mismo origen. El cliente
 * únicamente se suscribe: los comandos van por HTTP.
 */
@Configuration
@EnableWebSocketMessageBroker
class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final String SESION_HTTP = "sivant.sesionHttp";
    private static final String PRINCIPAL = "sivant.principal";

    /** Destino permitido y rol que exige; vacío admite a cualquier empleado. */
    private static final Map<String, String> SUSCRIPCIONES = Map.of(
            "/user/queue/notificaciones", "",
            "/topic/cocina", "COCINERO",
            "/topic/inventario", "ALMACENISTA");

    private final ValidadorSesion validador;
    private final SesionesWebSocket sesiones;

    WebSocketConfig(ValidadorSesion validador, SesionesWebSocket sesiones) {
        this.validador = validador;
        this.sesiones = sesiones;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registro) {
        registro.addEndpoint("/ws").addInterceptors(new RecordarSesion());
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registro) {
        registro.enableSimpleBroker("/topic", "/queue");
        registro.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration canal) {
        canal.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> mensaje, MessageChannel destino) {
                return entrante(mensaje);
            }
        });
    }

    @Override
    public void configureClientOutboundChannel(ChannelRegistration canal) {
        canal.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> mensaje, MessageChannel destino) {
                return saliente(mensaje);
            }
        });
    }

    private Message<?> entrante(Message<?> mensaje) {
        SimpMessageHeaderAccessor cabeceras = SimpMessageHeaderAccessor.wrap(mensaje);
        SimpMessageType tipo = cabeceras.getMessageType();
        if (tipo == SimpMessageType.MESSAGE) {
            throw new IllegalArgumentException("El canal solo admite suscripciones.");
        }
        if (tipo == SimpMessageType.CONNECT && !vigente(cabeceras)) {
            throw new IllegalArgumentException("Sesión no vigente.");
        }
        if (tipo == SimpMessageType.SUBSCRIBE) {
            String rol = SUSCRIPCIONES.get(String.valueOf(cabeceras.getDestination()));
            EmpleadoPrincipal principal = principal(cabeceras);
            if (rol == null || principal == null || !vigente(cabeceras)
                    || !(rol.isEmpty() || rol.equals(principal.rol()))) {
                throw new IllegalArgumentException("Suscripción no permitida.");
            }
        }
        return mensaje;
    }

    /** Una conexión abierta deja de recibir eventos en cuanto la sesión deja de ser vigente. */
    private Message<?> saliente(Message<?> mensaje) {
        SimpMessageHeaderAccessor cabeceras = SimpMessageHeaderAccessor.wrap(mensaje);
        boolean evento = cabeceras.getMessageType() == SimpMessageType.MESSAGE;
        return !evento || sesiones.vigente(cabeceras.getSessionId(), validador) ? mensaje : null;
    }

    private boolean vigente(SimpMessageHeaderAccessor cabeceras) {
        EmpleadoPrincipal principal = principal(cabeceras);
        HttpSession sesion = atributo(cabeceras, SESION_HTTP, HttpSession.class);
        boolean vigente = principal != null && validador.validar(sesion, principal).vigente();
        if (vigente) {
            sesiones.registrar(cabeceras.getSessionId(), sesion, principal);
        }
        return vigente;
    }

    private static EmpleadoPrincipal principal(SimpMessageHeaderAccessor cabeceras) {
        return atributo(cabeceras, PRINCIPAL, EmpleadoPrincipal.class);
    }

    private static <T> T atributo(SimpMessageHeaderAccessor cabeceras, String nombre, Class<T> tipo) {
        Map<String, Object> atributos = cabeceras.getSessionAttributes();
        return atributos == null ? null : tipo.cast(atributos.get(nombre));
    }

    /** Conserva la sesión HTTP y el actor de la conexión para volver a comprobarlos. */
    private static final class RecordarSesion implements HandshakeInterceptor {

        @Override
        public boolean beforeHandshake(ServerHttpRequest solicitud, ServerHttpResponse respuesta,
                WebSocketHandler manejador, Map<String, Object> atributos) {
            Authentication actual = SecurityContextHolder.getContext().getAuthentication();
            if (!(solicitud instanceof ServletServerHttpRequest servlet) || actual == null
                    || !(actual.getPrincipal() instanceof EmpleadoPrincipal principal)) {
                return false;
            }
            atributos.put(SESION_HTTP, servlet.getServletRequest().getSession(false));
            atributos.put(PRINCIPAL, principal);
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest solicitud, ServerHttpResponse respuesta,
                WebSocketHandler manejador, Exception error) {
            // Sin acciones posteriores.
        }
    }
}
