package mx.sivant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessException;

@DisplayName("Gestionar productos")
class CatalogoTest extends PruebaIntegracion {

    ClienteApi admin;
    String cafe;
    String leche;

    @BeforeEach
    void preparar() {
        admin = sesionDe(administrador);
        cafe = crearInsumo("Café en grano", "GRAMO").toString();
        leche = crearInsumo("Leche", "MILILITRO").toString();
    }

    private Map<String, Object> preparado(String nombre) {
        return new HashMap<>(Map.of("nombre", nombre, "descripcion", "Descripción", "precioVenta", "55.00",
                "tipo", "PREPARADO", "unidadMedida", "PIEZA",
                "receta", List.of(Map.of("insumoId", cafe, "cantidadRequerida", "18.5"),
                        Map.of("insumoId", leche, "cantidadRequerida", "200"))));
    }

    private static Map<String, Object> reventa(String nombre) {
        return new HashMap<>(Map.of("nombre", nombre, "descripcion", "Descripción", "precioVenta", "25",
                "tipo", "REVENTA", "unidadMedida", "PIEZA", "nivelMinimo", "5"));
    }

    private String alta(Map<String, Object> datos) {
        var r = admin.post("/productos", datos);
        assertThat(r.estado()).as(r.texto()).isEqualTo(201);
        return r.json().get("id").asString();
    }

    private long articulos() {
        return sql.queryForObject("SELECT count(*) FROM sivant.articulo", Long.class);
    }

    @Nested
    @DisplayName("RF07 · Registrar producto")
    class Registrar {

        @Test
        @DisplayName("CP-42 El alta de un preparado guarda su receta en la misma operación y no crea existencia")
        void altaPreparado() {
            var r = admin.post("/productos", preparado(" Capuchino "));
            assertThat(r.estado()).isEqualTo(201);
            assertThat(r.encabezados().get("location").getFirst()).endsWith("/" + r.json().get("id").asString());
            assertThat(r.json().get("nombre").asString()).isEqualTo("Capuchino");
            assertThat(r.json().get("precioVenta").isString()).isTrue();
            assertThat(r.json().get("precioVenta").asString()).isEqualTo("55.00");
            assertThat(r.json().get("receta").size()).isEqualTo(2);
            assertThat(r.json().get("receta").get(0).get("cantidadRequerida").asString()).isEqualTo("18.5");
            assertThat(r.json().has("existencia")).isFalse();
            assertThat(sql.queryForObject("SELECT count(*) FROM sivant.existencia_producto", Integer.class)).isZero();
        }

        @Test
        @DisplayName("CP-43 El alta de reventa crea su existencia en cero con el nivel mínimo")
        void altaReventa() {
            var r = admin.post("/productos", reventa("Agua embotellada"));
            assertThat(r.estado()).isEqualTo(201);
            assertThat(r.json().get("precioVenta").asString()).isEqualTo("25.00");
            assertThat(r.json().get("existencia").get("cantidadActual").asString()).isEqualTo("0");
            assertThat(r.json().get("existencia").get("nivelMinimo").asString()).isEqualTo("5");
            assertThat(r.json().has("receta")).isFalse();
        }

        @Test
        @DisplayName("CP-44 Datos inválidos: 400 por campo y no se guarda nada")
        void invalidos() {
            long antes = articulos();
            var vacio = admin.post("/productos", Map.of());
            for (String campo : List.of("nombre", "descripcion", "precioVenta", "tipo", "unidadMedida")) {
                assertThat(vacio.campo(campo)).as(campo).isNotBlank();
            }
            for (String precio : List.of("0", "0.00", "-1", "1.234", "abc", "", "1e5", "1234567890123")) {
                var datos = reventa("Precio");
                datos.put("precioVenta", precio);
                assertThat(admin.post("/productos", datos).campo("precioVenta")).as(precio).isNotBlank();
            }
            var sinReceta = preparado("X");
            sinReceta.put("receta", List.of());
            assertThat(admin.post("/productos", sinReceta).campo("receta")).isNotBlank();
            var repetido = preparado("X");
            repetido.put("receta", List.of(Map.of("insumoId", cafe, "cantidadRequerida", "1"),
                    Map.of("insumoId", cafe, "cantidadRequerida", "2")));
            assertThat(admin.post("/productos", repetido).campo("receta")).contains("repitas");
            var ajeno = preparado("X");
            ajeno.put("receta", List.of(Map.of("insumoId", "999999", "cantidadRequerida", "1")));
            assertThat(admin.post("/productos", ajeno).campo("receta")).isNotBlank();
            var cero = preparado("X");
            cero.put("receta", List.of(Map.of("insumoId", cafe, "cantidadRequerida", "0.000")));
            assertThat(admin.post("/productos", cero).estado()).isEqualTo(400);
            var conMinimo = preparado("X");
            conMinimo.put("nivelMinimo", "1");
            assertThat(admin.post("/productos", conMinimo).campo("nivelMinimo")).isNotBlank();
            var sinMinimo = reventa("X");
            sinMinimo.remove("nivelMinimo");
            assertThat(admin.post("/productos", sinMinimo).campo("nivelMinimo")).isNotBlank();
            var reventaConReceta = reventa("X");
            reventaConReceta.put("receta", preparado("X").get("receta"));
            assertThat(admin.post("/productos", reventaConReceta).campo("receta")).isNotBlank();
            assertThat(articulos()).isEqualTo(antes);
        }

        @Test
        @DisplayName("CP-45 La base rechaza preparados sin receta, reventa sin existencia y subtipos ambiguos")
        void invariantes() {
            assertThatThrownBy(() -> transaccion.executeWithoutResult(t -> {
                Long id = articulo();
                sql.update("INSERT INTO sivant.producto VALUES (?, 'd', 1)", id);
                sql.update("INSERT INTO sivant.producto_preparado VALUES (?)", id);
            })).hasStackTraceContaining("requiere receta");
            assertThatThrownBy(() -> transaccion.executeWithoutResult(t -> {
                Long id = articulo();
                sql.update("INSERT INTO sivant.producto VALUES (?, 'd', 1)", id);
                sql.update("INSERT INTO sivant.producto_reventa VALUES (?)", id);
            })).hasStackTraceContaining("requiere existencia");
            assertThatThrownBy(() -> transaccion.executeWithoutResult(t -> articulo()))
                    .hasStackTraceContaining("producto o insumo");
            assertThatThrownBy(() -> transaccion.executeWithoutResult(t ->
                    sql.update("INSERT INTO sivant.existencia_inventario (fecha_actualizacion) VALUES (now())")))
                    .hasStackTraceContaining("insumo o de producto");
            String id = alta(preparado("Latte"));
            assertThatThrownBy(() -> sql.update("DELETE FROM sivant.receta_producto WHERE id_producto_preparado = ?",
                    Long.valueOf(id))).hasStackTraceContaining("requiere receta");
            assertThatThrownBy(() -> sql.update("DELETE FROM sivant.existencia_insumo"))
                    .isInstanceOf(DataAccessException.class);
        }

        private Long articulo() {
            return sql.queryForObject("INSERT INTO sivant.articulo (nombre, unidad_medida) VALUES ('X', 'PIEZA') "
                    + "RETURNING id_articulo", Long.class);
        }

        @Test
        @DisplayName("CP-171 PostgreSQL rechaza un precio de 0 en productos y en comandas")
        void precioPositivo() {
            String id = alta(reventa("Agua"));
            assertThatThrownBy(() -> sql.update("UPDATE sivant.producto SET precio_venta = 0 WHERE id_producto = ?",
                    Long.valueOf(id))).isInstanceOf(DataIntegrityViolationException.class);
            assertThat(admin.get("/productos/" + id).json().get("precioVenta").asString()).isNotEqualTo("0.00");
            assertThat(sql.queryForObject("""
                    SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conname = 'ck_detalle_precio'
                    """, String.class)).contains("> (0)");
        }

        @Test
        @DisplayName("CP-46 Ofrece los insumos disponibles para la receta")
        void insumos() {
            var r = admin.get("/insumos");
            assertThat(r.estado()).isEqualTo(200);
            assertThat(r.texto()).contains("Café en grano", "Leche").startsWith("[");
        }
    }

    @Nested
    @DisplayName("RF08 · Consultar producto")
    class Consultar {

        String agua;

        @BeforeEach
        void datos() {
            alta(preparado("Capuchino"));
            alta(preparado("Té 100%"));
            agua = alta(reventa("Agua"));
            admin.post("/productos/" + agua + "/desactivacion", Map.of("confirmado", true));
        }

        private List<String> nombres(ClienteApi cliente, String filtro) {
            List<String> nombres = new ArrayList<>();
            cliente.get("/productos?" + filtro).json().get("contenido")
                    .forEach(p -> nombres.add(p.get("nombre").asString()));
            return nombres;
        }

        @Test
        @DisplayName("CP-47 El administrador busca y distingue activos e inactivos, preparados y reventa")
        void filtros() {
            assertThat(nombres(admin, "")).containsExactly("Agua", "Capuchino", "Té 100%");
            assertThat(nombres(admin, "activo=true")).containsExactly("Capuchino", "Té 100%");
            assertThat(nombres(admin, "activo=false")).containsExactly("Agua");
            assertThat(nombres(admin, "tipo=REVENTA")).containsExactly("Agua");
            assertThat(nombres(admin, "tipo=PREPARADO&q=CAPU")).containsExactly("Capuchino");
            assertThat(nombres(admin, "q=%25")).containsExactly("Té 100%");
            assertThat(nombres(admin, "page=1&size=2")).containsExactly("Té 100%");
            assertThat(admin.get("/productos?tipo=OTRO").estado()).isEqualTo(400);
            assertThat(admin.get("/productos?size=101").estado()).isEqualTo(400);
        }

        @Test
        @DisplayName("CP-48 El mesero solo consulta el catálogo activo, sin recetas ni existencias")
        void mesero() {
            ClienteApi mesero = sesionDe(crear("MESERO"));
            assertThat(nombres(mesero, "activo=false")).containsExactly("Capuchino", "Té 100%");
            assertThat(mesero.get("/productos/" + agua).estado()).isEqualTo(404);
            String id = mesero.get("/productos").json().get("contenido").get(0).get("id").asString();
            var detalle = mesero.get("/productos/" + id);
            assertThat(detalle.estado()).isEqualTo(200);
            assertThat(detalle.json().has("receta")).isFalse();
        }

        @Test
        @DisplayName("CP-49 Detalle inexistente e ID inválido")
        void detalle() {
            assertThat(admin.get("/productos/999999").estado()).isEqualTo(404);
            assertThat(admin.get("/productos/abc").estado()).isEqualTo(400);
        }
    }

    @Nested
    @DisplayName("RF09 · Modificar producto")
    class Modificar {

        @Test
        @DisplayName("CP-50 Cambia nombre, descripción o precio; conserva lo demás y lo audita")
        void parcial() {
            String id = alta(preparado("Latte"));
            var r = admin.patch("/productos/" + id, Map.of("precioVenta", "60.5"));
            assertThat(r.estado()).isEqualTo(200);
            assertThat(r.json().get("precioVenta").asString()).isEqualTo("60.50");
            assertThat(r.json().get("nombre").asString()).isEqualTo("Latte");
            assertThat(r.json().get("receta").size()).isEqualTo(2);
            assertThat(sql.queryForObject("SELECT detalle #>> '{precioVenta,nuevo}' FROM sivant.auditoria "
                    + "WHERE accion = 'PRODUCTO_EDITADO'", String.class)).isEqualTo("60.5");
        }

        @Test
        @DisplayName("Actualiza la receta completa de un producto preparado")
        void receta() {
            String id = alta(preparado("Latte"));
            var r = admin.patch("/productos/" + id, Map.of("receta",
                    List.of(Map.of("insumoId", leche, "cantidadRequerida", "250"))));
            assertThat(r.estado()).isEqualTo(200);
            assertThat(r.json().get("receta").size()).isEqualTo(1);
            assertThat(r.json().get("receta").get(0).get("insumoId").asString()).isEqualTo(leche);
            assertThat(r.json().get("receta").get(0).get("cantidadRequerida").asString()).isEqualTo("250");
        }

        @Test
        @DisplayName("CP-51 Valores inválidos, vacíos o no permitidos se rechazan")
        void invalido() {
            String id = alta(preparado("Latte"));
            assertThat(admin.patch("/productos/" + id, Map.of()).estado()).isEqualTo(400);
            assertThat(admin.patch("/productos/" + id, Map.of("nombre", " ")).campo("nombre")).isNotBlank();
            assertThat(admin.patch("/productos/" + id, Map.of("precioVenta", "-5")).campo("precioVenta")).isNotBlank();
            assertThat(admin.patch("/productos/" + id, Map.of("precioVenta", "0.00")).campo("precioVenta"))
                    .contains("mayor que 0");
            assertThat(admin.patch("/productos/" + id, Map.of("activo", false)).estado()).isEqualTo(400);
            assertThat(admin.patch("/productos/999999", Map.of("nombre", "X")).estado()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("RF10 · Desactivar producto")
    class Desactivar {

        @Test
        @DisplayName("CP-52 Exige confirmación, conserva el registro y no se repite")
        void desactivar() {
            String id = alta(reventa("Refresco"));
            String ruta = "/productos/" + id + "/desactivacion";
            assertThat(admin.post(ruta, Map.of("confirmado", false)).estado()).isEqualTo(400);
            assertThat(admin.get("/productos/" + id).json().get("activo").asBoolean()).isTrue();

            var r = admin.post(ruta, Map.of("confirmado", true));
            assertThat(r.estado()).isEqualTo(200);
            assertThat(r.json().get("activo").asBoolean()).isFalse();
            assertThat(admin.get("/productos/" + id).json().get("nombre").asString()).isEqualTo("Refresco");
            assertThat(admin.post(ruta, Map.of("confirmado", true)).codigo()).isEqualTo("PRODUCTO_INACTIVO");
        }

        @Test
        void reactivarDevuelveElProductoAlCatalogo() {
            String id = alta(reventa("Refresco"));
            String producto = "/productos/" + id;
            String reactivacion = producto + "/reactivacion";
            var mesero = sesionDe(crear("MESERO"));
            var original = admin.get(producto).json();

            assertThat(admin.post(producto + "/desactivacion", Map.of("confirmado", true)).estado()).isEqualTo(200);
            assertThat(mesero.get(producto).estado()).isEqualTo(404);
            assertThat(mesero.post(reactivacion, Map.of("confirmado", true)).estado()).isEqualTo(403);
            assertThat(admin.post(reactivacion, Map.of("confirmado", false)).estado()).isEqualTo(400);
            assertThat(admin.get(producto).json().get("activo").asBoolean()).isFalse();

            var respuesta = admin.post(reactivacion, Map.of("confirmado", true));
            assertThat(respuesta.estado()).isEqualTo(200);
            assertThat(respuesta.json()).isEqualTo(original);
            assertThat(mesero.get(producto).estado()).isEqualTo(200);
            assertThat(mesero.get("/productos").json().get("contenido").get(0).get("id").asString()).isEqualTo(id);
            assertThat(admin.post(reactivacion, Map.of("confirmado", true)).codigo()).isEqualTo("PRODUCTO_ACTIVO");
        }
    }

    @ParameterizedTest(name = "CP-53 {0} no puede modificar el catálogo")
    @ValueSource(strings = {"MESERO", "CAJERO", "COCINERO", "ALMACENISTA"})
    void permisos(String rol) {
        String id = alta(reventa("Galleta"));
        ClienteApi cliente = sesionDe(crear(rol));
        long antes = articulos();
        assertThat(List.of(
                cliente.post("/productos", reventa("Intento")),
                cliente.patch("/productos/" + id, Map.of("nombre", "Intento")),
                cliente.post("/productos/" + id + "/desactivacion", Map.of("confirmado", true)),
                cliente.get("/insumos"))).allSatisfy(r -> assertThat(r.estado()).isEqualTo(403));
        int lectura = "MESERO".equals(rol) ? 200 : 403;
        assertThat(cliente.get("/productos").estado()).isEqualTo(lectura);
        assertThat(cliente.get("/productos/" + id).estado()).isEqualTo(lectura);
        assertThat(articulos()).isEqualTo(antes);
        assertThat(admin.get("/productos/" + id).json().get("activo").asBoolean()).isTrue();
    }
}
