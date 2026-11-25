package mx.sivant.notificaciones.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonRawValue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.OffsetDateTime;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.infraestructura.api.Pagina;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import mx.sivant.notificaciones.aplicacion.DatosNotificacion;
import mx.sivant.notificaciones.dominio.TipoNotificacion;
import mx.sivant.notificaciones.infraestructura.NotificacionesJdbc;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Bandeja del usuario autenticado. La lectura es individual, también en avisos dirigidos a un rol. */
@RestController
@RequestMapping("/api/v1/notificaciones")
class NotificacionController {

    private final NotificacionesJdbc notificaciones;

    NotificacionController(NotificacionesJdbc notificaciones) {
        this.notificaciones = notificaciones;
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Notificacion(String id, TipoNotificacion tipo, String mensaje, OffsetDateTime fechaHora, boolean leida,
            String comandaId, @JsonRawValue String contexto) {

        static Notificacion de(DatosNotificacion n) {
            return new Notificacion(n.id().toString(), n.tipo(), n.mensaje(), n.fechaHora(), n.leida(),
                    n.comandaId() == null ? null : n.comandaId().toString(), n.contexto());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public Pagina<Notificacion> bandeja(@AuthenticationPrincipal EmpleadoPrincipal actor,
            @RequestParam(required = false) Boolean leida,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página inicia en 0.") int page,
            @RequestParam(defaultValue = "25") @Min(value = 1, message = "Usa un tamaño de 1 a 100.")
            @Max(value = 100, message = "Usa un tamaño de 1 a 100.") int size) {
        return notificaciones.bandeja(actor.id(), leida, page, size).con(Notificacion::de);
    }

    @PutMapping("/{id}/lectura")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Transactional
    public void leer(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id) {
        if (!notificaciones.marcarLeida(id, actor.id())) {
            throw ReglaNegocioException.noEncontrado("El aviso no existe.");
        }
    }
}
