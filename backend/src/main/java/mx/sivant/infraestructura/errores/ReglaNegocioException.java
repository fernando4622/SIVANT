package mx.sivant.infraestructura.errores;

import java.util.Map;
import org.springframework.http.HttpStatus;

/** Incumplimiento de una regla que se informa al cliente como Problem Details. */
public class ReglaNegocioException extends RuntimeException {

    private final HttpStatus estado;
    private final String codigo;
    private final transient Map<String, String> erroresCampo;

    public ReglaNegocioException(HttpStatus estado, String codigo, String mensaje, Map<String, String> erroresCampo) {
        super(mensaje);
        this.estado = estado;
        this.codigo = codigo;
        this.erroresCampo = erroresCampo;
    }

    public static ReglaNegocioException conflicto(String codigo, String mensaje) {
        return new ReglaNegocioException(HttpStatus.CONFLICT, codigo, mensaje, Map.of());
    }

    public static ReglaNegocioException conflicto(String codigo, String mensaje, String campo) {
        return new ReglaNegocioException(HttpStatus.CONFLICT, codigo, mensaje, Map.of(campo, mensaje));
    }

    public static ReglaNegocioException invalido(String campo, String mensaje) {
        return new ReglaNegocioException(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS", "Revisa los datos enviados.",
                Map.of(campo, mensaje));
    }

    public static ReglaNegocioException noEncontrado(String mensaje) {
        return new ReglaNegocioException(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", mensaje, Map.of());
    }

    public static ReglaNegocioException noAutenticado(String codigo, String mensaje) {
        return new ReglaNegocioException(HttpStatus.UNAUTHORIZED, codigo, mensaje, Map.of());
    }

    public HttpStatus estado() {
        return estado;
    }

    public String codigo() {
        return codigo;
    }

    public Map<String, String> erroresCampo() {
        return erroresCampo;
    }
}
