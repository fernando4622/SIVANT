package mx.sivant.ventas.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.infraestructura.api.Decimales;
import mx.sivant.infraestructura.api.Pagina;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import mx.sivant.infraestructura.idempotencia.Idempotencia;
import mx.sivant.ventas.aplicacion.Caja;
import mx.sivant.ventas.aplicacion.DatosCaja;
import mx.sivant.ventas.dominio.EstadoFondo;
import mx.sivant.ventas.dominio.EstadoVenta;
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
class CajaController {

    private static final String ID = "^\\d{1,18}$";
    private static final String MONTO = "Escribe un importe válido.";

    private final Caja caja;

    CajaController(Caja caja) {
        this.caja = caja;
    }

    // --- Entradas

    record AltaFondo(
            @NotNull(message = MONTO) @Pattern(regexp = Decimales.DINERO, message = MONTO) String monto) {
    }

    record AnulacionFondo(
            @NotBlank(message = "Escribe la justificación.")
            @Size(max = 500, message = "Usa 500 caracteres como máximo.") String justificacion) {
    }

    record Recepcion(
            @NotNull(message = "Selecciona el mesero.") @Pattern(regexp = ID, message = "Selecciona el mesero.")
            String meseroId,
            @NotEmpty(message = "Selecciona al menos un pago.")
            @Size(max = Caja.PAGOS_POR_ENTREGA, message = "Selecciona hasta 100 pagos.")
            List<@NotNull @Pattern(regexp = ID, message = "Selecciona pagos válidos.") String> pagoIds,
            @NotNull(message = MONTO) @Pattern(regexp = Decimales.DINERO, message = MONTO) String montoRecibido,
            @Pattern(regexp = Decimales.DINERO, message = MONTO) String fondoRecibido) {

        DatosCaja.Recepcion datos() {
            return new DatosCaja.Recepcion(Long.valueOf(meseroId), pagoIds.stream().map(Long::valueOf).toList(),
                    new BigDecimal(montoRecibido), fondoRecibido == null ? BigDecimal.ZERO : new BigDecimal(fondoRecibido));
        }
    }

    // --- Respuestas

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Anulacion(String cajero, OffsetDateTime fechaHora, String justificacion) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Fondo(String id, String cuentaId, int mesa, String mesero, String cajero, String monto,
            OffsetDateTime fechaHora, EstadoFondo estado, String ventaId, String entregaId, Anulacion anulacion) {

        static Fondo de(DatosCaja.Fondo f) {
            return new Fondo(f.id().toString(), f.cuentaId().toString(), f.mesa(), f.mesero(), f.cajero(),
                    Decimales.dinero(f.monto()), f.fechaHora(), f.estado(), texto(f.ventaId()), texto(f.entregaId()),
                    f.anulacion() == null ? null : new Anulacion(f.anulacion().cajero(), f.anulacion().fechaHora(),
                            f.anulacion().justificacion()));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PagoEfectivo(String id, String ventaId, int mesa, String meseroId, String mesero, OffsetDateTime fechaHora,
            String monto, EstadoVenta estadoVenta, String fondo) {

        static PagoEfectivo de(DatosCaja.PagoEfectivo p) {
            return new PagoEfectivo(p.id().toString(), p.ventaId().toString(), p.mesa(), p.meseroId().toString(),
                    p.mesero(), p.fechaHora(), Decimales.dinero(p.monto()), p.estadoVenta(),
                    p.fondo() == null ? null : Decimales.dinero(p.fondo()));
        }
    }

    record Entrega(String id, OffsetDateTime fechaHora, String mesero, String cajero, String monto, String montoFondo,
            List<PagoEfectivo> pagos) {

        static Entrega de(DatosCaja.Entrega e) {
            return new Entrega(e.id().toString(), e.fechaHora(), e.mesero(), e.cajero(), Decimales.dinero(e.monto()),
                    Decimales.dinero(e.montoFondo()), e.pagos().stream().map(PagoEfectivo::de).toList());
        }
    }

    private static String texto(Long id) {
        return id == null ? null : id.toString();
    }

    // --- Fondo de cambio · RF26

    @GetMapping("/cuentas/{id}/fondo-cambio")
    List<Fondo> fondosDeCuenta(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id) {
        return caja.fondosDe(actor, id).stream().map(Fondo::de).toList();
    }

    @PostMapping("/cuentas/{id}/fondo-cambio")
    @ResponseStatus(HttpStatus.CREATED)
    Fondo entregarFondo(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @RequestHeader(name = Idempotencia.ENCABEZADO, required = false) String clave,
            @Valid @RequestBody AltaFondo alta) {
        return Fondo.de(caja.entregarFondo(actor, clave, id, positivo(alta.monto(), "monto")));
    }

    @PostMapping("/cuentas/{id}/fondo-cambio/anulacion")
    Fondo anularFondo(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @RequestHeader(name = Idempotencia.ENCABEZADO, required = false) String clave,
            @Valid @RequestBody AnulacionFondo anulacion) {
        return Fondo.de(caja.anularFondo(actor, clave, id, anulacion.justificacion()));
    }

    @GetMapping("/caja/fondos")
    Pagina<Fondo> fondos(@RequestParam(required = false) EstadoFondo estado,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página inicia en 0.") int page,
            @RequestParam(defaultValue = "25") @Min(value = 1, message = "Usa un tamaño de 1 a 100.")
            @Max(value = 100, message = "Usa un tamaño de 1 a 100.") int size) {
        return caja.fondos(estado, page, size).con(Fondo::de);
    }

    // --- Entrega de efectivo · RF25

    @GetMapping("/caja/pagos-efectivo-pendientes")
    List<PagoEfectivo> pendientes(@RequestParam(required = false) Long meseroId) {
        return caja.pendientes(meseroId).stream().map(PagoEfectivo::de).toList();
    }

    @PostMapping("/caja/entregas-efectivo")
    @ResponseStatus(HttpStatus.CREATED)
    Entrega recibir(@AuthenticationPrincipal EmpleadoPrincipal actor,
            @RequestHeader(name = Idempotencia.ENCABEZADO, required = false) String clave,
            @Valid @RequestBody Recepcion recepcion) {
        positivo(recepcion.montoRecibido(), "montoRecibido");
        return Entrega.de(caja.recibir(actor, clave, recepcion.datos()));
    }

    private static BigDecimal positivo(String importe, String campo) {
        BigDecimal valor = new BigDecimal(importe);
        if (valor.signum() <= 0) {
            throw ReglaNegocioException.invalido(campo, "El importe debe ser mayor que 0.");
        }
        return valor;
    }
}
