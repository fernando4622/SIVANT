package mx.sivant;

import java.net.CookieManager;
import java.net.HttpCookie;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Se comporta como el navegador: conserva cookies y reenvía XSRF-TOKEN en el encabezado. */
class ClienteApi {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final CookieManager cookies = new CookieManager();
    private final HttpClient http = HttpClient.newBuilder().cookieHandler(cookies).build();
    private final String base;

    ClienteApi(int puerto) {
        base = "http://localhost:" + puerto + "/api/v1";
    }

    record Respuesta(int estado, JsonNode json, String texto, Map<String, List<String>> encabezados) {

        String codigo() {
            return json.path("codigo").asString("");
        }

        String campo(String nombre) {
            return json.path("erroresCampo").path(nombre).asString("");
        }
    }

    Respuesta login(String correo, String contrasena) {
        get("/auth/csrf");
        return post("/auth/login", Map.of("correo", correo, "contrasena", contrasena));
    }

    Respuesta get(String ruta) {
        return enviar("GET", ruta, null, false, true, null);
    }

    Respuesta getPasivo(String ruta) {
        return enviar("GET", ruta, null, true, true, null);
    }

    Respuesta post(String ruta, Object cuerpo) {
        return enviar("POST", ruta, cuerpo, false, true, null);
    }

    Respuesta postConClave(String ruta, String clave, Object cuerpo) {
        return enviar("POST", ruta, cuerpo, false, true, clave);
    }

    Respuesta patch(String ruta, Object cuerpo) {
        return enviar("PATCH", ruta, cuerpo, false, true, null);
    }

    Respuesta put(String ruta, Object cuerpo) {
        return enviar("PUT", ruta, cuerpo, false, true, null);
    }

    Respuesta delete(String ruta) {
        return enviar("DELETE", ruta, null, false, true, null);
    }

    /** Envía un archivo en multipart/form-data, como el formulario del navegador. */
    Respuesta subir(String metodo, String ruta, String parte, String nombre, byte[] contenido) {
        try {
            String limite = "----sivant" + System.nanoTime();
            var cuerpo = new java.io.ByteArrayOutputStream();
            String fin = "\r\n";
            cuerpo.write(("--" + limite + fin + "Content-Disposition: form-data; name=\"" + parte + "\"; filename=\""
                    + nombre + "\"" + fin + "Content-Type: application/octet-stream" + fin + fin)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            cuerpo.write(contenido);
            cuerpo.write((fin + "--" + limite + "--" + fin).getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var solicitud = HttpRequest.newBuilder(URI.create(base + ruta))
                    .header("Content-Type", "multipart/form-data; boundary=" + limite)
                    .method(metodo, HttpRequest.BodyPublishers.ofByteArray(cuerpo.toByteArray()));
            if (cookie("XSRF-TOKEN") != null) {
                solicitud.header("X-XSRF-TOKEN", cookie("XSRF-TOKEN"));
            }
            HttpResponse<String> r = http.send(solicitud.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = r.body().startsWith("{") ? JSON.readTree(r.body()) : JSON.createObjectNode();
            return new Respuesta(r.statusCode(), json, r.body(), r.headers().map());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    HttpResponse<byte[]> getBytes(String ruta) {
        try {
            return http.send(HttpRequest.newBuilder(URI.create(base + ruta)).GET().build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    Respuesta sinCsrf(String metodo, String ruta, Object cuerpo) {
        return enviar(metodo, ruta, cuerpo, false, false, null);
    }

    String cookie(String nombre) {
        return cookies.getCookieStore().getCookies().stream().filter(c -> c.getName().equals(nombre))
                .map(HttpCookie::getValue).findFirst().orElse(null);
    }

    void ponerCookieSesion(String valor) {
        HttpCookie cookie = new HttpCookie("SIVANT_SESION", valor);
        cookie.setPath("/");
        cookies.getCookieStore().add(URI.create(base), cookie);
    }

    private Respuesta enviar(String metodo, String ruta, Object cuerpo, boolean pasiva, boolean conCsrf,
            String clave) {
        try {
            var solicitud = HttpRequest.newBuilder(URI.create(base + ruta));
            if (pasiva) {
                solicitud.header("X-Solicitud-Pasiva", "true");
            }
            if (clave != null) {
                solicitud.header("Idempotency-Key", clave);
            }
            if (conCsrf && cookie("XSRF-TOKEN") != null) {
                solicitud.header("X-XSRF-TOKEN", cookie("XSRF-TOKEN"));
            }
            if (cuerpo == null) {
                solicitud.method(metodo, HttpRequest.BodyPublishers.noBody());
            } else {
                String texto = cuerpo instanceof String s ? s : JSON.writeValueAsString(cuerpo);
                solicitud.header("Content-Type", "application/json")
                        .method(metodo, HttpRequest.BodyPublishers.ofString(texto));
            }
            HttpResponse<String> r = http.send(solicitud.build(), HttpResponse.BodyHandlers.ofString());
            JsonNode json = r.body().startsWith("{") ? JSON.readTree(r.body()) : JSON.createObjectNode();
            return new Respuesta(r.statusCode(), json, r.body(), r.headers().map());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
