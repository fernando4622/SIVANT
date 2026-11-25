package mx.sivant.ventas.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.infraestructura.api.Pagina;
import mx.sivant.infraestructura.idempotencia.Idempotencia;
import mx.sivant.ventas.api.VentasDtos.Cancelacion;
import mx.sivant.ventas.api.VentasDtos.Cobrada;
import mx.sivant.ventas.api.VentasDtos.Cobro;
import mx.sivant.ventas.api.VentasDtos.Opciones;
import mx.sivant.ventas.api.VentasDtos.Resumen;
import mx.sivant.ventas.api.VentasDtos.Venta;
import mx.sivant.ventas.aplicacion.Cobros;
import mx.sivant.ventas.aplicacion.DatosVentas.Filtro;
import mx.sivant.ventas.aplicacion.GestionVentas;
import mx.sivant.ventas.dominio.EstadoVenta;
import mx.sivant.ventas.dominio.MetodoPago;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class VentasController {

    private final Cobros cobros;
    private final GestionVentas gestion;

    VentasController(Cobros cobros, GestionVentas gestion) {
        this.cobros = cobros;
        this.gestion = gestion;
    }

    @PostMapping("/cuentas/{id}/cobro")
    @ResponseStatus(HttpStatus.CREATED)
    Cobrada cobrar(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @RequestHeader(name = Idempotencia.ENCABEZADO, required = false) String clave,
            @Valid @RequestBody Cobro cobro) {
        return Cobrada.de(cobros.cobrar(actor, clave, id, cobro.datos()), actor);
    }

    @GetMapping("/ventas")
    Pagina<Resumen> ventas(@RequestParam(required = false) LocalDate desde,
            @RequestParam(required = false) LocalDate hasta,
            @RequestParam(required = false) EstadoVenta estado,
            @RequestParam(required = false) MetodoPago metodo,
            @RequestParam(required = false) Long meseroId,
            @RequestParam(required = false) Long mesaId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página inicia en 0.") int page,
            @RequestParam(defaultValue = "25") @Min(value = 1, message = "Usa un tamaño de 1 a 100.")
            @Max(value = 100, message = "Usa un tamaño de 1 a 100.") int size) {
        return gestion.ventas(new Filtro(desde, hasta, estado, metodo, meseroId, mesaId), page, size).con(Resumen::de);
    }

    @GetMapping("/ventas/opciones")
    Opciones opciones() {
        return Opciones.de(gestion.opciones());
    }

    @GetMapping("/ventas/{id}")
    Venta venta(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id) {
        return Venta.de(gestion.venta(id), actor);
    }

    @PostMapping("/ventas/{id}/cancelacion")
    Venta cancelar(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @RequestHeader(name = Idempotencia.ENCABEZADO, required = false) String clave,
            @Valid @RequestBody Cancelacion cancelacion) {
        return Venta.de(gestion.cancelar(actor, clave, id, cancelacion.justificacion(),
                cancelacion.referenciaReversion()), actor);
    }
}
