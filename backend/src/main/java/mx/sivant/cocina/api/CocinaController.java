package mx.sivant.cocina.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;
import java.util.List;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.servicio.aplicacion.DatosServicio;
import mx.sivant.servicio.aplicacion.PreparacionComandas;
import mx.sivant.servicio.dominio.EstadoComanda;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class CocinaController {

    private final PreparacionComandas preparacion;

    CocinaController(PreparacionComandas preparacion) {
        this.preparacion = preparacion;
    }

    record Version(@NotNull(message = "Indica la versión.") Integer version) {
    }

    /** Cocina no recibe precios. */
    record Linea(String id, String producto, int cantidad, String observaciones) {
    }

    record ComandaCocina(String id, String cuentaId, int mesa, String mesero, EstadoComanda estado,
            OffsetDateTime fechaHoraEnvio, int version, List<Linea> detalles) {

        static ComandaCocina de(DatosServicio.EnCola c) {
            return new ComandaCocina(c.id().toString(), c.cuentaId().toString(), c.mesa(), c.mesero(), c.estado(),
                    c.fechaHoraEnvio(), c.version(), c.detalles().stream().map(d -> new Linea(d.id().toString(),
                    d.producto(), d.cantidad(), d.observaciones())).toList());
        }
    }

    @GetMapping("/cocina/cola")
    List<ComandaCocina> cola() {
        return preparacion.cola().stream().map(ComandaCocina::de).toList();
    }

    @PostMapping("/comandas/{id}/preparacion")
    ComandaCocina iniciar(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @Valid @RequestBody Version cuerpo) {
        return ComandaCocina.de(preparacion.iniciar(actor.id(), id, cuerpo.version()));
    }

    @PostMapping("/comandas/{id}/lista")
    ComandaCocina marcarLista(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @Valid @RequestBody Version cuerpo) {
        return ComandaCocina.de(preparacion.marcarLista(actor.id(), id, cuerpo.version()));
    }
}
