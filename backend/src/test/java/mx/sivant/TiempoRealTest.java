package mx.sivant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@DisplayName("Tiempo real · WebSocket")
class TiempoRealTest extends PruebaServicio {

    private final List<StompSession> abiertas = new ArrayList<>();

    @AfterEach
    void cerrar() {
        abiertas.stream().filter(StompSession::isConnected).forEach(StompSession::disconnect);
    }

    /** Suscripción con sus mensajes y el error que cerró la conexión, si lo hubo. */
    private record Canal(BlockingQueue<String> mensajes, CompletableFuture<Throwable> error, StompSession sesion) {

        JsonNode siguiente() throws InterruptedException {
            String texto = mensajes.poll(5, TimeUnit.SECONDS);
            assertThat(texto).as("evento recibido").isNotNull();
            return new JsonMapper().readTree(texto);
        }

        void silencio() throws InterruptedException {
            assertThat(mensajes.poll(700, TimeUnit.MILLISECONDS)).isNull();
        }
    }

    private Canal suscribir(ClienteApi cliente, String destino, String origen) throws Exception {
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        WebSocketHttpHeaders cabeceras = new WebSocketHttpHeaders();
        if (cliente != null) {
            cabeceras.add("Cookie", "SIVANT_SESION=" + cliente.cookie("SIVANT_SESION"));
        }
        cabeceras.setOrigin(origen == null ? "http://localhost:" + puerto : origen);
        BlockingQueue<String> mensajes = new LinkedBlockingQueue<>();
        CompletableFuture<Throwable> error = new CompletableFuture<>();
        StompSession sesion = stomp.connectAsync("ws://localhost:" + puerto + "/ws", cabeceras,
                new StompSessionHandlerAdapter() {
                    @Override
                    public void handleFrame(StompHeaders encabezados, Object cuerpo) {
                        error.complete(new IllegalStateException(encabezados.getFirst("message")));
                    }

                    @Override
                    public void handleTransportError(StompSession s, Throwable t) {
                        error.complete(t);
                    }
                }).get(5, TimeUnit.SECONDS);
        abiertas.add(sesion);
        sesion.subscribe(destino, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders encabezados) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders encabezados, Object cuerpo) {
                mensajes.add(new String((byte[]) cuerpo, StandardCharsets.UTF_8));
            }
        });
        Thread.sleep(400);
        return new Canal(mensajes, error, sesion);
    }

    private Canal suscribir(ClienteApi cliente, String destino) throws Exception {
        return suscribir(cliente, destino, null);
    }

    @Test
    @DisplayName("CP-102 Cocina recibe envío, modificación y cancelación después de confirmarse")
    void cocina() throws Exception {
        Canal canal = suscribir(cocinero, "/topic/cocina");
        JsonNode comanda = comandaNueva(cuentaNueva());
        String id = comanda.get("id").asString();
        JsonNode evento = canal.siguiente();
        assertThat(evento.get("tipo").asString()).isEqualTo("COMANDA_ENVIADA");
        assertThat(evento.get("recursoId").asString()).isEqualTo(id);
        assertThat(evento.propertyNames()).containsExactlyInAnyOrder("eventoId", "tipo", "recursoId", "version",
                "fechaHora");
        editar(mesero, id, 0, detalle(comanda.get("detalles").get(0), 3));
        JsonNode modificacion = canal.siguiente();
        assertThat(modificacion.get("tipo").asString()).isEqualTo("MODIFICACION_COMANDA");
        assertThat(modificacion.get("version").asInt()).isEqualTo(1);
        cancelar(cajero, id, 1, "x");
        assertThat(canal.siguiente().get("tipo").asString()).isEqualTo("CANCELACION_COMANDA");
        assertThat(preparar(id, 2).estado()).isEqualTo(409);
        canal.silencio();
    }

    @Test
    @DisplayName("CP-103 El aviso de comanda lista llega solo al mesero responsable")
    void lista() throws Exception {
        Canal responsable = suscribir(mesero, "/user/queue/notificaciones");
        Canal ajeno = suscribir(otroMesero, "/user/queue/notificaciones");
        String id = comandaNueva(cuentaNueva()).get("id").asString();
        preparar(id, 0);
        assertThat(responsable.siguiente().get("tipo").asString()).isEqualTo("COMANDA_ACTUALIZADA");
        long inicio = System.nanoTime();
        lista(id, 1);
        assertThat(responsable.siguiente().get("tipo").asString()).isEqualTo("COMANDA_LISTA");
        assertThat(Duration.ofNanos(System.nanoTime() - inicio)).as("RNF10").isLessThan(Duration.ofSeconds(2));
        ajeno.silencio();
    }

    @Test
    @DisplayName("CP-121 Almacén recibe cambios y alertas de consumo; cocina, la salida de la cola")
    void consumo() throws Exception {
        Canal almacen = suscribir(sesionDe(crear("ALMACENISTA")), "/topic/inventario");
        Canal cocina = suscribir(cocinero, "/topic/cocina");
        String id = comandaNueva(cuentaNueva()).get("id").asString();
        preparar(id, 0);
        lista(id, 1);
        assertThat(mesero.postConClave("/comandas/" + id + "/servicio", clave(), Map.of("version", 2)).estado())
                .isEqualTo(200);
        List<String> tipos = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            tipos.add(almacen.siguiente().get("tipo").asString());
            if (i < 3) {
                cocina.siguiente();
            }
        }
        assertThat(tipos).containsExactlyInAnyOrder("EXISTENCIA_ACTUALIZADA", "ALERTA_INVENTARIO",
                "EXISTENCIA_ACTUALIZADA", "ALERTA_INVENTARIO");
        assertThat(cocina.siguiente().get("tipo").asString()).isEqualTo("COMANDA_SERVIDA");
    }

    @Test
    @DisplayName("CP-104 Cada rol solo puede suscribirse a sus destinos")
    void destinos() throws Exception {
        for (String destino : List.of("/topic/cocina", "/topic/inventario", "/topic/otro", "/queue/notificaciones",
                "/topic/*", "/topic/**", "/user/1/queue/notificaciones")) {
            Canal canal = suscribir(mesero, destino);
            assertThat(canal.error().get(5, TimeUnit.SECONDS)).as(destino).isNotNull();
        }
        assertThat(suscribir(cocinero, "/topic/cocina").error()).isNotDone();
        assertThat(suscribir(sesionDe(crear("ALMACENISTA")), "/topic/inventario").error()).isNotDone();
    }

    @Test
    @DisplayName("CP-105 Sin sesión o desde otro origen no se abre la conexión")
    void conexion() {
        assertThatThrownBy(() -> suscribir(null, "/topic/cocina")).hasStackTraceContaining("401");
        assertThatThrownBy(() -> suscribir(cocinero, "/topic/cocina", "https://sitio.externo"))
                .hasStackTraceContaining("403");
    }

    @Test
    @DisplayName("CP-106 El cliente no puede enviar mensajes por el canal")
    void sinEnvios() throws Exception {
        Canal emisor = suscribir(cocinero, "/topic/cocina");
        Canal receptor = suscribir(sesionDe(crear("COCINERO")), "/topic/cocina");
        emisor.sesion().send("/topic/cocina", "falso".getBytes(StandardCharsets.UTF_8));
        assertThat(emisor.error().get(5, TimeUnit.SECONDS)).isNotNull();
        receptor.silencio();
        comandaNueva(cuentaNueva());
        assertThat(receptor.siguiente().get("tipo").asString()).isEqualTo("COMANDA_ENVIADA");
    }

    @Test
    @DisplayName("CP-107 Una conexión abierta deja de recibir al desactivar al empleado, cambiar su contraseña o salir")
    void sesionTerminada() throws Exception {
        String cuenta = cuentaNueva();
        var desactivado = crear("COCINERO");
        var conClave = crear("COCINERO");
        ClienteApi sale = sesionDe(crear("COCINERO"));
        List<Canal> canales = List.of(suscribir(sesionDe(desactivado), "/topic/cocina"),
                suscribir(sesionDe(conClave), "/topic/cocina"), suscribir(sale, "/topic/cocina"));
        comandaNueva(cuenta);
        for (Canal canal : canales) {
            canal.siguiente();
        }
        admin.post("/empleados/" + desactivado.getId() + "/desactivacion", Map.of("confirmado", true));
        admin.patch("/empleados/" + conClave.getId(), Map.of("contrasena", "Otra.Clave.2026"));
        sale.post("/auth/logout", null);
        comandaNueva(cuenta);
        for (Canal canal : canales) {
            canal.silencio();
        }
    }

    @Test
    @DisplayName("CP-108 La conexión no cuenta como actividad ni recibe eventos tras expirar la sesión")
    void inactividad() throws Exception {
        String cuenta = cuentaNueva();
        reloj.avanzar(Duration.ofMinutes(40));
        mesero.get("/auth/me");
        Canal canal = suscribir(cocinero, "/topic/cocina");
        comandaNueva(cuenta);
        canal.siguiente();
        reloj.avanzar(Duration.ofMinutes(6));
        assertThat(comandaNueva(cuenta).get("id")).isNotNull();
        canal.silencio();
        assertThat(cocinero.get("/auth/me").codigo()).isEqualTo("SESION_EXPIRADA");
    }
}
