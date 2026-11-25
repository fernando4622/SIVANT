package mx.sivant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import mx.sivant.empleados.dominio.Empleado;
import org.junit.jupiter.api.BeforeEach;
import tools.jackson.databind.JsonNode;

/** Mesas, productos y personas para los casos de servicio y cocina. */
abstract class PruebaServicio extends PruebaIntegracion {

    Empleado juan;
    ClienteApi admin;
    ClienteApi mesero;
    ClienteApi otroMesero;
    ClienteApi cajero;
    ClienteApi cocinero;
    String mesa1;
    String mesa2;
    String agua;
    String galleta;

    @BeforeEach
    void prepararServicio() {
        juan = crear("Juan Pérez", "juan@sivant.test", "MESERO");
        admin = sesionDe(administrador);
        mesero = sesionDe(juan);
        otroMesero = sesionDe(crear("Ana Mesera", "ana@sivant.test", "MESERO"));
        cajero = sesionDe(crear("María Cajera", "maria@sivant.test", "CAJERO"));
        cocinero = sesionDe(crear("Carla Cocina", "carla@sivant.test", "COCINERO"));
        mesa1 = mesa(1);
        mesa2 = mesa(2);
        agua = producto("Agua", "20.00");
        galleta = producto("Galleta", "15.50");
    }

    private String mesa(int numero) {
        return sql.queryForObject("INSERT INTO sivant.mesa (numero) VALUES (?) RETURNING id_mesa", Long.class, numero)
                .toString();
    }

    private String producto(String nombre, String precio) {
        return admin.post("/productos", Map.of("nombre", nombre, "descripcion", "x", "precioVenta", precio,
                "tipo", "REVENTA", "unidadMedida", "PIEZA", "nivelMinimo", "0")).json().get("id").asString();
    }

    static String clave() {
        return UUID.randomUUID().toString();
    }

    ClienteApi.Respuesta abrir(ClienteApi cliente, String mesa) {
        return cliente.postConClave("/cuentas", clave(), Map.of("mesaId", mesa));
    }

    String cuentaNueva() {
        var r = abrir(mesero, mesa1);
        assertThat(r.estado()).as(r.texto()).isEqualTo(201);
        return r.json().get("id").asString();
    }

    static Map<String, Object> linea(String producto, int cantidad, String observaciones) {
        Map<String, Object> linea = new HashMap<>(Map.of("productoId", producto, "cantidad", cantidad));
        if (observaciones != null) {
            linea.put("observaciones", observaciones);
        }
        return linea;
    }

    ClienteApi.Respuesta enviar(ClienteApi cliente, String cuenta, Object... lineas) {
        return cliente.postConClave("/cuentas/" + cuenta + "/comandas", clave(), Map.of("detalles", List.of(lineas)));
    }

    /** Dos aguas y una galleta. */
    JsonNode comandaNueva(String cuenta) {
        var r = enviar(mesero, cuenta, linea(agua, 2, null), linea(galleta, 1, "Sin azúcar"));
        assertThat(r.estado()).as(r.texto()).isEqualTo(201);
        return r.json();
    }

    ClienteApi.Respuesta cancelar(ClienteApi cliente, String comanda, int version, String justificacion) {
        return cliente.postConClave("/comandas/" + comanda + "/cancelacion", clave(),
                Map.of("version", version, "justificacion", justificacion));
    }

    ClienteApi.Respuesta preparar(String comanda, int version) {
        return cocinero.post("/comandas/" + comanda + "/preparacion", Map.of("version", version));
    }

    ClienteApi.Respuesta lista(String comanda, int version) {
        return cocinero.post("/comandas/" + comanda + "/lista", Map.of("version", version));
    }

    ClienteApi.Respuesta editar(ClienteApi cliente, String comanda, int version, Object... detalles) {
        return cliente.put("/comandas/" + comanda + "/detalles", Map.of("version", version, "detalles", List.of(detalles)));
    }

    static Map<String, Object> detalle(JsonNode detalle, int cantidad) {
        return Map.of("detalleId", detalle.get("id").asString(), "cantidad", cantidad);
    }

    int avisos(ClienteApi cliente, String filtro) {
        return cliente.get("/notificaciones?" + filtro).json().get("totalElementos").asInt();
    }
}
