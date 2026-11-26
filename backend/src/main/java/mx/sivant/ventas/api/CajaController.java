package mx.sivant.ventas.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.infraestructura.api.Decimales;
import mx.sivant.infraestructura.idempotencia.Idempotencia;
import mx.sivant.ventas.aplicacion.Caja;
import mx.sivant.ventas.aplicacion.DatosCaja;
import mx.sivant.ventas.dominio.EstadoVenta;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class CajaController {

    private static final String ID = "^\\d{1,18}$";
    private static final String MONTO = "Escribe un importe válido.";

    private final Caja caja;

    CajaController(Caja caja) {
        this.caja = caja;
    }

    // --- Entradas

    record Recibido(
            @NotNull(message = "Selecciona el pago.") @Pattern(regexp = ID, message = "Selecciona el pago.") String pagoId,
            @NotNull(message = MONTO) @Pattern(regexp = Decimales.DINERO_POSITIVO, message = MONTO) String montoRecibido) {
    }

    record Recepcion(
            @NotNull(message = "Selecciona el mesero.") @Pattern(regexp = ID, message = "Selecciona el mesero.")
            String meseroId,
            @NotEmpty(message = "Selecciona al menos un pago.")
            @Size(max = Caja.PAGOS_POR_ENTREGA, message = "Selecciona hasta 100 pagos.")
            List<@NotNull @Valid Recibido> pagos) {

        DatosCaja.Recepcion datos() {
            return new DatosCaja.Recepcion(Long.valueOf(meseroId), pagos.stream()
                    .map(p -> new DatosCaja.Recibido(Long.valueOf(p.pagoId()), new BigDecimal(p.montoRecibido())))
                    .toList());
        }
    }

    // --- Respuestas

    record PagoEfectivo(String id, String ventaId, int mesa, String meseroId, String mesero, OffsetDateTime fechaHora,
            String monto, EstadoVenta estadoVenta) {

        static PagoEfectivo de(DatosCaja.PagoEfectivo p) {
            return new PagoEfectivo(p.id().toString(), p.ventaId().toString(), p.mesa(), p.meseroId().toString(),
                    p.mesero(), p.fechaHora(), Decimales.dinero(p.monto()), p.estadoVenta());
        }
    }

    record PagoRecibido(String id, String ventaId, int mesa, String monto, String montoRecibido, String cambio) {

        static PagoRecibido de(DatosCaja.PagoRecibido r) {
            return new PagoRecibido(r.pago().id().toString(), r.pago().ventaId().toString(), r.pago().mesa(),
                    Decimales.dinero(r.pago().monto()), Decimales.dinero(r.montoRecibido()), Decimales.dinero(r.cambio()));
        }
    }

    record Entrega(String id, OffsetDateTime fechaHora, String mesero, String cajero, String monto, String cambio,
            List<PagoRecibido> pagos) {

        static Entrega de(DatosCaja.Entrega e) {
            return new Entrega(e.id().toString(), e.fechaHora(), e.mesero(), e.cajero(), Decimales.dinero(e.monto()),
                    Decimales.dinero(e.cambio()), e.pagos().stream().map(PagoRecibido::de).toList());
        }
    }

    // --- Operaciones · RF25 y RF26

    @GetMapping("/caja/pagos-efectivo-pendientes")
    List<PagoEfectivo> pendientes(@RequestParam(required = false) Long meseroId) {
        return caja.pendientes(meseroId).stream().map(PagoEfectivo::de).toList();
    }

    @PostMapping("/caja/entregas-efectivo")
    @ResponseStatus(HttpStatus.CREATED)
    Entrega recibir(@AuthenticationPrincipal EmpleadoPrincipal actor,
            @RequestHeader(name = Idempotencia.ENCABEZADO, required = false) String clave,
            @Valid @RequestBody Recepcion recepcion) {
        return Entrega.de(caja.recibir(actor, clave, recepcion.datos()));
    }
}
