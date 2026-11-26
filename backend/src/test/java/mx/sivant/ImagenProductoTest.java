package mx.sivant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import mx.sivant.catalogo.infraestructura.AlmacenImagenes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;

@DisplayName("Imagen del producto")
class ImagenProductoTest extends PruebaIntegracion {

    static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
    static final byte[] JPG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0, 0x10, 'J', 'F', 'I', 'F'};
    static final byte[] WEBP = {'R', 'I', 'F', 'F', 4, 0, 0, 0, 'W', 'E', 'B', 'P', 'V', 'P', '8', ' '};

    @Autowired
    AlmacenImagenes almacen;

    ClienteApi admin;
    String producto;

    @BeforeEach
    void preparar() {
        admin = sesionDe(administrador);
        var alta = new HashMap<String, Object>(Map.of("nombre", "Agua embotellada", "descripcion", "500 ml",
                "precioVenta", "20", "tipo", "REVENTA", "unidadMedida", "PIEZA", "nivelMinimo", "5"));
        producto = admin.post("/productos", alta).json().get("id").asString();
    }

    private String ruta() {
        return sql.queryForObject("SELECT imagen FROM sivant.producto WHERE id_producto = ?", String.class,
                Long.valueOf(producto));
    }

    private Path archivo(String ruta) {
        return almacen.archivo(ruta);
    }

    private long archivos() throws Exception {
        Path carpeta = almacen.archivo("productos");
        if (!Files.isDirectory(carpeta)) {
            return 0;
        }
        try (var lista = Files.list(carpeta)) {
            return lista.count();
        }
    }

    @Test
    @DisplayName("CP-174 El administrador sube una imagen; se guarda la ruta, se sirve con su tipo y reemplazarla borra la anterior")
    void subirYReemplazar() throws Exception {
        var r = admin.subir("PUT", "/productos/" + producto + "/imagen", "archivo", "foto.png", PNG);
        assertThat(r.estado()).as(r.texto()).isEqualTo(200);
        String primera = ruta();
        assertThat(primera).matches("productos/[0-9a-f-]{36}\\.png");
        assertThat(r.json().get("imagen").asString())
                .isEqualTo("/api/v1/productos/" + producto + "/imagen?v=" + primera.substring(10, 46));
        assertThat(Files.readAllBytes(archivo(primera))).isEqualTo(PNG);

        var imagen = admin.getBytes("/productos/" + producto + "/imagen");
        assertThat(imagen.statusCode()).isEqualTo(200);
        assertThat(imagen.body()).isEqualTo(PNG);
        assertThat(imagen.headers().firstValue("content-type")).hasValue("image/png");
        assertThat(imagen.headers().firstValue("cache-control").orElse("")).contains("private").contains("max-age");
        assertThat(imagen.headers().firstValue("x-content-type-options")).hasValue("nosniff");

        var otra = admin.subir("PUT", "/productos/" + producto + "/imagen", "archivo", "otra.png", JPG);
        assertThat(otra.estado()).isEqualTo(200);
        assertThat(ruta()).endsWith(".jpg").isNotEqualTo(primera);
        assertThat(otra.json().get("imagen").asString()).isNotEqualTo(r.json().get("imagen").asString());
        assertThat(Files.exists(archivo(primera))).isFalse();
        assertThat(admin.getBytes("/productos/" + producto + "/imagen").headers().firstValue("content-type"))
                .hasValue("image/jpeg");
        assertThat(admin.subir("PUT", "/productos/" + producto + "/imagen", "archivo", "a.webp", WEBP).estado())
                .isEqualTo(200);
        assertThat(ruta()).endsWith(".webp");
        assertThat(sql.queryForObject("SELECT count(*) FROM sivant.auditoria WHERE accion = 'PRODUCTO_IMAGEN_CAMBIADA'",
                Integer.class)).isEqualTo(3);

        ClienteApi mesero = sesionDe(crear("MESERO"));
        assertThat(mesero.get("/productos?q=agua").json().get("contenido").get(0).get("imagen").asString())
                .startsWith("/api/v1/productos/" + producto + "/imagen?v=");
        assertThat(mesero.getBytes("/productos/" + producto + "/imagen").body()).isEqualTo(WEBP);
    }

    @Test
    @DisplayName("CP-175 Rechaza archivos que no son JPG, PNG o WebP, vacíos, de más de 2 MB o sin archivo, y no guarda nada")
    void rechazos() throws Exception {
        String url = "/productos/" + producto + "/imagen";
        long antes = archivos();
        var texto = admin.subir("PUT", url, "archivo", "foto.png", "<svg onload=alert(1)>".getBytes());
        assertThat(texto.estado()).isEqualTo(400);
        assertThat(texto.campo("archivo")).isEqualTo("Usa una imagen JPG, PNG o WebP.");
        assertThat(admin.subir("PUT", url, "archivo", "vacia.png", new byte[0]).campo("archivo"))
                .isEqualTo("Selecciona una imagen.");
        byte[] grande = Arrays.copyOf(PNG, 2 * 1024 * 1024 + 1);
        var pesada = admin.subir("PUT", url, "archivo", "grande.png", grande);
        assertThat(pesada.estado()).isEqualTo(413);
        assertThat(pesada.campo("archivo")).isEqualTo("La imagen pesa más de 2 MB.");
        assertThat(admin.subir("PUT", url, "otro", "foto.png", PNG).estado()).isEqualTo(400);
        assertThat(admin.subir("PUT", "/productos/999999/imagen", "archivo", "foto.png", PNG).estado()).isEqualTo(404);
        assertThat(ruta()).isNull();
        assertThat(archivos()).isEqualTo(antes);
        assertThat(admin.getBytes(url).statusCode()).isEqualTo(404);
    }

    @ParameterizedTest(name = "CP-176 {0} no puede subir ni quitar la imagen")
    @ValueSource(strings = {"MESERO", "CAJERO", "COCINERO", "ALMACENISTA"})
    void permisos(String rol) {
        ClienteApi cliente = sesionDe(crear(rol));
        String url = "/productos/" + producto + "/imagen";
        assertThat(cliente.subir("PUT", url, "archivo", "foto.png", PNG).estado()).isEqualTo(403);
        assertThat(admin.subir("PUT", url, "archivo", "foto.png", PNG).estado()).isEqualTo(200);
        assertThat(cliente.sinCsrf("DELETE", url, null).estado()).isEqualTo(403);
        assertThat(cliente.delete(url).estado()).isEqualTo(403);
        assertThat(cliente.getBytes(url).statusCode()).isEqualTo(rol.equals("MESERO") ? 200 : 403);
        assertThat(ruta()).isNotNull();
    }

    @Test
    @DisplayName("CP-177 Quitar la imagen borra su ruta y su archivo; un producto inactivo no muestra su imagen al mesero")
    void quitar() {
        String url = "/productos/" + producto + "/imagen";
        assertThat(admin.delete(url).codigo()).isEqualTo("SIN_IMAGEN");
        admin.subir("PUT", url, "archivo", "foto.png", PNG);
        String guardada = ruta();
        ClienteApi mesero = sesionDe(crear("MESERO"));
        admin.post("/productos/" + producto + "/desactivacion", Map.of("confirmado", true));
        assertThat(mesero.getBytes(url).statusCode()).isEqualTo(404);
        assertThat(admin.getBytes(url).statusCode()).isEqualTo(200);

        var r = admin.delete(url);
        assertThat(r.estado()).as(r.texto()).isEqualTo(200);
        assertThat(r.json().has("imagen")).isFalse();
        assertThat(ruta()).isNull();
        assertThat(Files.exists(archivo(guardada))).isFalse();
        assertThat(admin.getBytes(url).statusCode()).isEqualTo(404);
        assertThat(sql.queryForObject("SELECT count(*) FROM sivant.auditoria WHERE accion = 'PRODUCTO_IMAGEN_QUITADA'",
                Integer.class)).isEqualTo(1);
    }

    @ParameterizedTest(name = "CP-178 PostgreSQL rechaza la ruta {0}")
    @ValueSource(strings = {"../secreto.png", "productos/foto.png", "https://otro.sitio/a.png",
            "productos/0a1b2c3d-0000-0000-0000-000000000000.svg", "/productos/0a1b2c3d-0000-0000-0000-000000000000.png"})
    void integridad(String ruta) {
        assertThatThrownBy(() -> sql.update("UPDATE sivant.producto SET imagen = ? WHERE id_producto = ?", ruta,
                Long.valueOf(producto))).isInstanceOf(DataAccessException.class);
    }
}
