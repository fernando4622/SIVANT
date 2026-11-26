package mx.sivant.servicio.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.infraestructura.api.Pagina;
import mx.sivant.infraestructura.idempotencia.Idempotencia;
import mx.sivant.servicio.api.ServicioDtos.Apertura;
import mx.sivant.servicio.api.ServicioDtos.Cancelacion;
import mx.sivant.servicio.api.ServicioDtos.Comanda;
import mx.sivant.servicio.api.ServicioDtos.Cuenta;
import mx.sivant.servicio.api.ServicioDtos.Edicion;
import mx.sivant.servicio.api.ServicioDtos.Envio;
import mx.sivant.servicio.api.ServicioDtos.Mesa;
import mx.sivant.servicio.api.ServicioDtos.ResumenCuenta;
import mx.sivant.servicio.api.ServicioDtos.Servida;
import mx.sivant.servicio.api.ServicioDtos.SoloVersion;
import mx.sivant.servicio.aplicacion.EntregaComandas;
import mx.sivant.servicio.aplicacion.GestionCuentas;
import mx.sivant.servicio.dominio.EstadoCuenta;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class ServicioController {

    private final GestionCuentas gestion;
    private final EntregaComandas entrega;

    ServicioController(GestionCuentas gestion, EntregaComandas entrega) {
        this.gestion = gestion;
        this.entrega = entrega;
    }

    @GetMapping("/mesas")
    List<Mesa> mesas(@AuthenticationPrincipal EmpleadoPrincipal actor) {
        return gestion.mesas().stream().map(m -> Mesa.de(m, actor)).toList();
    }

    @PostMapping("/cuentas")
    ResponseEntity<Cuenta> abrir(@AuthenticationPrincipal EmpleadoPrincipal actor,
            @RequestHeader(name = Idempotencia.ENCABEZADO, required = false) String clave,
            @Valid @RequestBody Apertura apertura) {
        Cuenta cuenta = Cuenta.de(gestion.abrir(actor, clave, Long.valueOf(apertura.mesaId())), actor);
        return ResponseEntity.created(URI.create("/api/v1/cuentas/" + cuenta.id())).body(cuenta);
    }

    @GetMapping("/cuentas")
    Pagina<ResumenCuenta> cuentas(@AuthenticationPrincipal EmpleadoPrincipal actor,
            @RequestParam(required = false) EstadoCuenta estado,
            @RequestParam(required = false) Long mesaId,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página inicia en 0.") int page,
            @RequestParam(defaultValue = "25") @Min(value = 1, message = "Usa un tamaño de 1 a 100.")
            @Max(value = 100, message = "Usa un tamaño de 1 a 100.") int size) {
        return gestion.cuentas(actor, estado, mesaId, page, size).con(ResumenCuenta::de);
    }

    @GetMapping("/cuentas/{id}")
    Cuenta cuenta(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id) {
        return Cuenta.de(gestion.cuenta(actor, id), actor);
    }

    @PostMapping("/cuentas/{id}/cierre")
    Cuenta cerrar(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @RequestHeader(name = Idempotencia.ENCABEZADO, required = false) String clave,
            @Valid @RequestBody SoloVersion cierre) {
        return Cuenta.de(gestion.cerrarSinCobro(actor, clave, id, cierre.version()), actor);
    }

    @PostMapping("/cuentas/{id}/comandas")
    @ResponseStatus(HttpStatus.CREATED)
    Comanda enviar(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @RequestHeader(name = Idempotencia.ENCABEZADO, required = false) String clave,
            @Valid @RequestBody Envio envio) {
        return Comanda.de(gestion.enviar(actor, clave, id, envio.lineas()), true, actor);
    }

    @PutMapping("/comandas/{id}/detalles")
    Cuenta editar(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @Valid @RequestBody Edicion edicion) {
        return Cuenta.de(gestion.editar(actor, id, edicion.version(), edicion.lineas()), actor);
    }

    @PostMapping("/comandas/{id}/servicio")
    Servida servir(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @RequestHeader(name = Idempotencia.ENCABEZADO, required = false) String clave,
            @Valid @RequestBody SoloVersion cuerpo) {
        return Servida.de(entrega.servir(actor, clave, id, cuerpo.version()), actor);
    }

    @PostMapping("/comandas/{id}/cancelacion")
    Comanda cancelar(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @RequestHeader(name = Idempotencia.ENCABEZADO, required = false) String clave,
            @Valid @RequestBody Cancelacion cancelacion) {
        return Comanda.de(gestion.cancelar(actor, clave, id, cancelacion.version(), cancelacion.justificacion()), true,
                actor);
    }
}
