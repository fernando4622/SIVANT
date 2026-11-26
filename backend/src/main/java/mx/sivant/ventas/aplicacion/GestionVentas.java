package mx.sivant.ventas.aplicacion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import mx.sivant.auditoria.aplicacion.RegistroAuditoria;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.infraestructura.api.Fechas;
import mx.sivant.infraestructura.api.Pagina;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import mx.sivant.infraestructura.idempotencia.Idempotencia;
import mx.sivant.servicio.aplicacion.CobroDeCuentas;
import mx.sivant.ventas.aplicacion.DatosVentas.Filtro;
import mx.sivant.ventas.aplicacion.DatosVentas.Opciones;
import mx.sivant.ventas.aplicacion.DatosVentas.ResumenVenta;
import mx.sivant.ventas.aplicacion.DatosVentas.Venta;
import mx.sivant.ventas.aplicacion.DatosVentas.VentaConCuenta;
import mx.sivant.ventas.dominio.EstadoVenta;
import mx.sivant.ventas.dominio.MetodoPago;
import mx.sivant.ventas.dominio.Referencias;
import mx.sivant.ventas.infraestructura.VentasJdbc;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF06 y RF22. Las ventas anteriores a la ventana se conservan, pero no se consultan ni se cancelan. */
@Service
public class GestionVentas {

    public static final int MESES_CONSULTABLES = 24;
    private static final String CONSULTA = "hasAnyAuthority('ADMINISTRADOR', 'CAJERO')";

    private final VentasJdbc ventas;
    private final CobroDeCuentas cuentas;
    private final Idempotencia idempotencia;
    private final RegistroAuditoria auditoria;
    private final Clock reloj;

    public GestionVentas(VentasJdbc ventas, CobroDeCuentas cuentas, Idempotencia idempotencia,
            RegistroAuditoria auditoria, Clock reloj) {
        this.ventas = ventas;
        this.cuentas = cuentas;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(CONSULTA)
    public Pagina<ResumenVenta> ventas(Filtro filtro, int pagina, int tamano) {
        if (filtro.desde() != null && filtro.hasta() != null && filtro.hasta().isBefore(filtro.desde())) {
            throw ReglaNegocioException.invalido("hasta", "La fecha final no puede ser anterior a la inicial.");
        }
        OffsetDateTime desde = Fechas.inicio(filtro.desde());
        OffsetDateTime limite = inicioVentana();
        return ventas.ventas(desde == null || desde.isBefore(limite) ? limite : desde, Fechas.fin(filtro.hasta()), filtro,
                pagina, tamano);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(CONSULTA)
    public Opciones opciones() {
        return ventas.opciones(inicioVentana());
    }

    @Transactional(readOnly = true)
    @PreAuthorize(CONSULTA)
    public VentaConCuenta venta(Long id) {
        return completa(ventas.venta(id).filter(this::consultable).orElseThrow(GestionVentas::sinVenta));
    }

    /** La devolución completa ya se hizo en la terminal, el banco o la caja. Conserva pago, cuenta y consumos. */
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public VentaConCuenta cancelar(EmpleadoPrincipal actor, String clave, Long id, String justificacion,
            String referenciaReversion) {
        String motivo = justificacion.strip();
        String referencia = Referencias.normalizar(referenciaReversion);
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("justificacion", motivo);
        cuerpo.put("referenciaReversion", referencia);
        return idempotencia.ejecutar(actor.id(), "CANCELAR_VENTA", id.toString(), clave, cuerpo, VentaConCuenta.class,
                () -> aplicarCancelacion(actor, id, motivo, referencia));
    }

    private VentaConCuenta aplicarCancelacion(EmpleadoPrincipal actor, Long id, String motivo, String referencia) {
        ventas.bloquear(id);
        Venta venta = ventas.venta(id).filter(this::consultable).orElseThrow(GestionVentas::sinVenta);
        if (venta.estado() != EstadoVenta.COMPLETADA) {
            throw ReglaNegocioException.conflicto("VENTA_CANCELADA", "La venta ya está cancelada.");
        }
        validarReversion(venta.pago().metodo(), referencia);
        ventas.marcarCancelada(id);
        if (!ventas.insertarCancelacion(id, actor.id(), OffsetDateTime.now(reloj), motivo, venta.total(), referencia)) {
            throw ReglaNegocioException.conflicto("REFERENCIA_REPETIDA",
                    "Esa referencia ya se registró hoy en otra devolución.", "referenciaReversion");
        }
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("justificacion", motivo);
        detalle.put("metodo", venta.pago().metodo().name());
        detalle.put("montoDevuelto", venta.total().toPlainString());
        detalle.put("referenciaReversion", referencia);
        auditoria.registrar(actor.id(), "VENTA_CANCELADA", "VENTA", id, detalle);
        return completa(ventas.venta(id).orElseThrow());
    }

    private static void validarReversion(MetodoPago metodo, String referencia) {
        String campo = "referenciaReversion";
        if (metodo == MetodoPago.EFECTIVO) {
            if (referencia != null) {
                throw ReglaNegocioException.invalido(campo, "La devolución en efectivo no lleva referencia.");
            }
            return;
        }
        if (referencia == null) {
            throw ReglaNegocioException.invalido(campo, "Escribe la referencia de la reversión.");
        }
        Cobros.exigirSinTarjeta(campo, referencia);
    }

    private VentaConCuenta completa(Venta venta) {
        return new VentaConCuenta(venta, cuentas.cuenta(venta.cuentaId()));
    }

    private boolean consultable(Venta venta) {
        return !venta.fechaHora().isBefore(inicioVentana());
    }

    private OffsetDateTime inicioVentana() {
        return Fechas.inicio(LocalDate.ofInstant(reloj.instant(), Fechas.ZONA_NEGOCIO).minusMonths(MESES_CONSULTABLES));
    }

    private static ReglaNegocioException sinVenta() {
        return ReglaNegocioException.noEncontrado("La venta no existe.");
    }
}
