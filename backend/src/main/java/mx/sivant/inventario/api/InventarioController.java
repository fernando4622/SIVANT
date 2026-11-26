package mx.sivant.inventario.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDate;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.infraestructura.api.Pagina;
import mx.sivant.infraestructura.idempotencia.Idempotencia;
import mx.sivant.inventario.api.InventarioDtos.Ajuste;
import mx.sivant.inventario.api.InventarioDtos.Existencia;
import mx.sivant.inventario.api.InventarioDtos.Historial;
import mx.sivant.inventario.api.InventarioDtos.NivelMinimo;
import mx.sivant.inventario.api.InventarioDtos.Movimiento;
import mx.sivant.inventario.api.InventarioDtos.MovimientoRegistrado;
import mx.sivant.inventario.api.InventarioDtos.Resolucion;
import mx.sivant.inventario.api.InventarioDtos.Solicitud;
import mx.sivant.inventario.aplicacion.DatosInventario.TipoArticulo;
import mx.sivant.inventario.aplicacion.GestionInventario;
import mx.sivant.inventario.dominio.EstadoSolicitud;
import mx.sivant.inventario.dominio.TipoHistorial;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventario")
class InventarioController {

    private static final String PAGINA = "La página inicia en 0.";
    private static final String TAMANO = "Usa un tamaño de 1 a 100.";

    private final GestionInventario gestion;

    InventarioController(GestionInventario gestion) {
        this.gestion = gestion;
    }

    @GetMapping("/existencias")
    Pagina<Existencia> existencias(@RequestParam(required = false) String q,
            @RequestParam(required = false) TipoArticulo tipo,
            @RequestParam(required = false) Boolean nivelBajo,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = PAGINA) int page,
            @RequestParam(defaultValue = "25") @Min(value = 1, message = TAMANO)
            @Max(value = 100, message = TAMANO) int size) {
        return gestion.existencias(q, tipo, nivelBajo, page, size).con(Existencia::de);
    }

    @GetMapping("/existencias/{id}")
    Existencia existencia(@PathVariable Long id) {
        return Existencia.de(gestion.existencia(id));
    }

    @GetMapping("/existencias/{id}/movimientos")
    Pagina<Historial> historialDeExistencia(@PathVariable Long id,
            @RequestParam(required = false) TipoHistorial tipo,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = PAGINA) int page,
            @RequestParam(defaultValue = "25") @Min(value = 1, message = TAMANO)
            @Max(value = 100, message = TAMANO) int size) {
        return gestion.historial(id, null, tipo, desde, hasta, page, size).con(Historial::de);
    }

    @GetMapping("/movimientos")
    Pagina<Historial> historial(@RequestParam(required = false) String q,
            @RequestParam(required = false) TipoHistorial tipo,
            @RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = PAGINA) int page,
            @RequestParam(defaultValue = "25") @Min(value = 1, message = TAMANO)
            @Max(value = 100, message = TAMANO) int size) {
        return gestion.historial(null, q, tipo, desde, hasta, page, size).con(Historial::de);
    }

    @PostMapping("/existencias/{id}/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    MovimientoRegistrado registrarMovimiento(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @RequestHeader(name = Idempotencia.ENCABEZADO, required = false) String clave,
            @Valid @RequestBody Movimiento movimiento) {
        return MovimientoRegistrado.de(gestion.registrarMovimiento(actor.id(), clave, id, movimiento.tipo(),
                new BigDecimal(movimiento.cantidad()), movimiento.causa(), movimiento.nota()));
    }

    @PatchMapping("/existencias/{id}")
    Existencia cambiarNivelMinimo(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @Valid @RequestBody NivelMinimo cambio) {
        return Existencia.de(gestion.cambiarNivelMinimo(actor.id(), id, new BigDecimal(cambio.nivelMinimo())));
    }

    @PostMapping("/ajustes")
    @ResponseStatus(HttpStatus.CREATED)
    Solicitud solicitarAjuste(@AuthenticationPrincipal EmpleadoPrincipal actor,
            @RequestHeader(name = Idempotencia.ENCABEZADO, required = false) String clave,
            @Valid @RequestBody Ajuste ajuste) {
        return Solicitud.de(gestion.solicitarAjuste(actor.id(), clave, Long.valueOf(ajuste.existenciaId()),
                new BigDecimal(ajuste.cantidadSolicitada()), ajuste.justificacion()));
    }

    @GetMapping("/ajustes")
    Pagina<Solicitud> solicitudes(@RequestParam(required = false) EstadoSolicitud estado,
            @RequestParam(required = false) Long existenciaId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = PAGINA) int page,
            @RequestParam(defaultValue = "25") @Min(value = 1, message = TAMANO)
            @Max(value = 100, message = TAMANO) int size) {
        return gestion.solicitudes(estado, existenciaId, page, size).con(Solicitud::de);
    }

    @GetMapping("/ajustes/{id}")
    Solicitud solicitud(@PathVariable Long id) {
        return Solicitud.de(gestion.solicitud(id));
    }

    @PostMapping("/ajustes/{id}/resolucion")
    Solicitud resolver(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @RequestHeader(name = Idempotencia.ENCABEZADO, required = false) String clave,
            @Valid @RequestBody Resolucion resolucion) {
        return Solicitud.de(gestion.resolver(actor.id(), clave, id,
                EstadoSolicitud.valueOf(resolucion.decision().name())));
    }
}
