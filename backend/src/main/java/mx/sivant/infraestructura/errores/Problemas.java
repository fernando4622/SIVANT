package mx.sivant.infraestructura.errores;

import java.net.URI;
import java.util.Locale;
import java.util.Map;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

/** Construye los errores con el contrato de la sección 4 de arquitectura. */
public final class Problemas {

    private Problemas() {
    }

    public static ProblemDetail crear(HttpStatusCode estado, String codigo, String detalle, String ruta) {
        return crear(estado, codigo, detalle, ruta, Map.of());
    }

    public static ProblemDetail crear(HttpStatusCode estado, String codigo, String detalle, String ruta,
            Map<String, String> erroresCampo) {
        ProblemDetail problema = ProblemDetail.forStatusAndDetail(estado, detalle);
        problema.setType(URI.create("urn:sivant:error:" + codigo.toLowerCase(Locale.ROOT).replace('_', '-')));
        problema.setTitle(titulo(estado));
        problema.setInstance(URI.create(ruta));
        problema.setProperty("codigo", codigo);
        if (!erroresCampo.isEmpty()) {
            problema.setProperty("erroresCampo", erroresCampo);
        }
        return problema;
    }

    private static String titulo(HttpStatusCode estado) {
        return switch (estado.value()) {
            case 401 -> "Sesión requerida";
            case 403 -> "Sin permiso";
            case 404 -> "No encontrado";
            case 405 -> "Método no permitido";
            case 409 -> "Conflicto";
            case 415 -> "Formato no admitido";
            default -> estado.is5xxServerError() ? "Error interno" : "Solicitud inválida";
        };
    }
}
