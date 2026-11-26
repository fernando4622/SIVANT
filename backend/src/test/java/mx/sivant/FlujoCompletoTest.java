package mx.sivant;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** El guion de demostración de la fase 7, de punta a punta por la API, con los cinco roles. */
@DisplayName("Fase 7 · Aceptación")
class FlujoCompletoTest extends PruebaServicio {

    private static final JsonMapper JSON = new JsonMapper();

    private JsonNode lista(ClienteApi.Respuesta r) {
        assertThat(r.estado()).as(r.texto()).isEqualTo(200);
        return JSON.readTree(r.texto());
    }

    private static void ok(ClienteApi.Respuesta r, int estado) {
        assertThat(r.estado()).as(r.texto()).isEqualTo(estado);
    }

    private String saldo(ClienteApi almacen, Long existencia) {
        return almacen.get("/inventario/existencias/" + existencia).json().get("cantidadActual").asString();
    }

    @Test
    @DisplayName("CP-167 Demostración: catálogo, inventario, cuenta, cocina, servicio, cobro, caja con cambio y venta")
    void demostracion() {
        ClienteApi almacen = sesionDe(crear("Pedro Almacén", "pedro@sivant.test", "ALMACENISTA"));

        // Catálogo e inventario (RF07, RF15 a RF18).
        Long insumo = crearInsumo("Café", "GRAMO");
        Long cafe = sql.queryForObject("SELECT id_existencia FROM sivant.existencia_insumo WHERE id_insumo = ?",
                Long.class, insumo);
        var producto = admin.post("/productos", Map.of("nombre", "Americano", "descripcion", "Café", "precioVenta",
                "35.00", "tipo", "PREPARADO", "unidadMedida", "PIEZA", "receta",
                List.of(Map.of("insumoId", insumo.toString(), "cantidadRequerida", "18"))));
        ok(producto, 201);
        String americano = producto.json().get("id").asString();
        ok(almacen.postConClave("/inventario/existencias/" + cafe + "/movimientos", clave(),
                Map.of("tipo", "ENTRADA_MANUAL", "cantidad", "500")), 201);
        var ajuste = almacen.postConClave("/inventario/ajustes", clave(), Map.of("existenciaId", cafe.toString(),
                "cantidadSolicitada", "480", "justificacion", "Conteo físico"));
        ok(ajuste, 201);
        ok(admin.postConClave("/inventario/ajustes/" + ajuste.json().get("id").asString() + "/resolucion", clave(),
                Map.of("decision", "AUTORIZADA")), 200);
        assertThat(saldo(almacen, cafe)).isEqualTo("480");

        // Cuenta y comandas (RF01 a RF03).
        String cuenta = cuentaNueva();
        JsonNode primera = enviar(mesero, cuenta, linea(americano, 2, "Sin azúcar"), linea(agua, 1, null)).json();
        String idPrimera = primera.get("id").asString();
        ok(editar(mesero, idPrimera, 0, detalle(primera.get("detalles").get(0), 3), detalle(primera.get("detalles")
                .get(1), 1)), 200);
        String segunda = enviar(mesero, cuenta, linea(galleta, 1, null)).json().get("id").asString();
        ok(cancelar(cajero, segunda, 0, "El cliente ya no la quiere"), 200);

        // Cocina y avisos (RF19 a RF21).
        assertThat(lista(cocinero.get("/cocina/cola")).size()).isEqualTo(1);
        ok(preparar(idPrimera, 1), 200);
        ok(lista(idPrimera, 2), 200);
        assertThat(mesero.get("/notificaciones").texto()).contains("COMANDA_LISTA");

        // Servicio y consumo (RF23, RF24).
        ok(mesero.postConClave("/comandas/" + idPrimera + "/servicio", clave(), Map.of("version", 3)), 200);
        assertThat(saldo(almacen, cafe)).as("480 - 3 × 18").isEqualTo("426");

        // Cobro en efectivo (RF04).
        JsonNode antes = mesero.get("/cuentas/" + cuenta).json();
        assertThat(antes.get("total").asString()).isEqualTo("125.00");
        var cobro = mesero.postConClave("/cuentas/" + cuenta + "/cobro", clave(), Map.of("versionCuenta",
                antes.get("version").asInt(), "metodo", "EFECTIVO", "totalEsperado", "125.00"));
        ok(cobro, 201);
        String venta = cobro.json().get("venta").get("id").asString();
        String pago = cobro.json().get("pago").get("id").asString();

        // El mesero lleva a caja los 200 del cliente; caja recibe el pago y le da 75 de cambio (RF25, RF26).
        var entrega = cajero.postConClave("/caja/entregas-efectivo", clave(), Map.of("meseroId",
                juan.getId().toString(), "pagos", List.of(Map.of("pagoId", pago, "montoRecibido", "200"))));
        ok(entrega, 201);
        assertThat(entrega.json().get("monto").asString()).isEqualTo("125.00");
        assertThat(entrega.json().get("cambio").asString()).isEqualTo("75.00");
        assertThat(lista(cajero.get("/caja/pagos-efectivo-pendientes"))).isEmpty();

        // Historial y cancelación de venta (RF06, RF22).
        assertThat(cajero.get("/ventas").json().get("totalElementos").asInt()).isEqualTo(1);
        ok(admin.postConClave("/ventas/" + venta + "/cancelacion", clave(), Map.of("confirmado", true,
                "justificacion", "Cobro de prueba")), 200);
        assertThat(saldo(almacen, cafe)).as("la cancelación no repone inventario").isEqualTo("426");

        // Todo quedó auditado con su responsable.
        List<String> acciones = sql.queryForList("SELECT DISTINCT accion FROM sivant.auditoria", String.class);
        assertThat(acciones).contains("PRODUCTO_REGISTRADO", "MOVIMIENTO_REGISTRADO", "AJUSTE_SOLICITADO", "AJUSTE_AUTORIZADA",
                "CUENTA_ABIERTA", "COMANDA_ENVIADA", "COMANDA_EDITADA", "COMANDA_CANCELADA", "COMANDA_EN_PREPARACION", "COMANDA_LISTA", "COMANDA_SERVIDA",
                "VENTA_COBRADA", "VENTA_CANCELADA", "EFECTIVO_RECIBIDO");
        assertThat(sql.queryForObject("SELECT count(*) FROM sivant.auditoria WHERE id_empleado IS NULL AND accion <> "
                + "'EMPLEADO_REGISTRADO'", Integer.class)).isZero();
    }
}
