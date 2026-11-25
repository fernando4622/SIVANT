package mx.sivant.infraestructura.errores;

import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ManejadorErrores extends ResponseEntityExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorErrores.class);

    @ExceptionHandler(ReglaNegocioException.class)
    ResponseEntity<ProblemDetail> reglaNegocio(ReglaNegocioException e, HttpServletRequest solicitud) {
        return ResponseEntity.status(e.estado()).body(Problemas.crear(e.estado(), e.codigo(), e.getMessage(),
                solicitud.getRequestURI(), e.erroresCampo()));
    }

    /** Los resuelve Spring Security con sus respuestas 401 y 403. */
    @ExceptionHandler({AccessDeniedException.class, AuthenticationException.class})
    void seguridad(RuntimeException e) {
        throw e;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> inesperado(Exception e, HttpServletRequest solicitud) {
        LOG.error("Error no controlado en {} {}", solicitud.getMethod(), solicitud.getRequestURI(), e);
        return ResponseEntity.internalServerError().body(Problemas.crear(HttpStatus.INTERNAL_SERVER_ERROR,
                "ERROR_INTERNO", "Ocurrió un error inesperado. Intenta de nuevo.", solicitud.getRequestURI()));
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception e, Object cuerpo, HttpHeaders encabezados,
            HttpStatusCode estado, WebRequest solicitud) {
        Map<String, String> campos = erroresCampo(e);
        String codigo;
        String detalle;
        if (estado.is5xxServerError()) {
            LOG.error("Error de Spring MVC", e);
            codigo = "ERROR_INTERNO";
            detalle = "Ocurrió un error inesperado. Intenta de nuevo.";
        } else if (estado.value() == 404) {
            codigo = "NO_ENCONTRADO";
            detalle = "El recurso no existe.";
        } else if (estado.value() == 405) {
            codigo = "METODO_NO_PERMITIDO";
            detalle = "La operación no está disponible en esta ruta.";
        } else {
            codigo = "DATOS_INVALIDOS";
            detalle = campos.isEmpty() ? "La solicitud no tiene el formato esperado." : "Revisa los datos enviados.";
        }
        String ruta = ((ServletWebRequest) solicitud).getRequest().getRequestURI();
        return ResponseEntity.status(estado).headers(encabezados)
                .body(Problemas.crear(estado, codigo, detalle, ruta, campos));
    }

    private static Map<String, String> erroresCampo(Exception e) {
        Map<String, String> campos = new LinkedHashMap<>();
        if (e instanceof MethodArgumentNotValidException invalido) {
            for (FieldError error : invalido.getBindingResult().getFieldErrors()) {
                campos.putIfAbsent(error.getField(), error.getDefaultMessage());
            }
        } else if (e instanceof HandlerMethodValidationException invalido) {
            invalido.getParameterValidationResults().forEach(resultado -> resultado.getResolvableErrors()
                    .forEach(error -> campos.putIfAbsent(resultado.getMethodParameter().getParameterName(),
                            error.getDefaultMessage())));
        } else if (e instanceof MethodArgumentTypeMismatchException tipo) {
            campos.put(tipo.getName(), "El valor no es válido.");
        }
        return campos;
    }
}
