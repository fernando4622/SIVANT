package mx.sivant.ventas.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeSet;
import mx.sivant.auditoria.aplicacion.RegistroAuditoria;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.infraestructura.api.Pagina;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import mx.sivant.infraestructura.idempotencia.Idempotencia;
import mx.sivant.servicio.aplicacion.CobroDeCuentas;
import mx.sivant.servicio.aplicacion.DatosServicio.Cuenta;
import mx.sivant.ventas.aplicacion.DatosCaja.Entrega;
import mx.sivant.ventas.aplicacion.DatosCaja.Fondo;
import mx.sivant.ventas.aplicacion.DatosCaja.PagoEfectivo;
import mx.sivant.ventas.aplicacion.DatosCaja.Recepcion;
import mx.sivant.ventas.dominio.EstadoFondo;
import mx.sivant.ventas.infraestructura.CajaJdbc;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF25 y RF26. El cajero entrega el fondo antes del cobro y lo recibe de regreso con el efectivo de los pagos. La
 * entrega registra por separado el importe de los pagos y el del fondo.
 */
@Service
public class Caja {

    public static final int PAGOS_POR_ENTREGA = 100;

    private final CajaJdbc caja;
    private final CobroDeCuentas cuentas;
    private final Idempotencia idempotencia;
    private final RegistroAuditoria auditoria;
    private final Clock reloj;

    public Caja(CajaJdbc caja, CobroDeCuentas cuentas, Idempotencia idempotencia, RegistroAuditoria auditoria,
            Clock reloj) {
        this.caja = caja;
        this.cuentas = cuentas;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    // --- Fondo de cambio · RF26

    /** El mesero responsable consulta el fondo de su cuenta; el cajero, el de cualquiera. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('MESERO', 'CAJERO')")
    public List<Fondo> fondosDe(EmpleadoPrincipal actor, Long idCuenta) {
        Cuenta cuenta = cuentas.cuenta(idCuenta);
        if (!actor.rol().equals("CAJERO") && !cuenta.meseroId().equals(actor.id())) {
            throw ReglaNegocioException.noEncontrado("La cuenta no existe.");
        }
        return caja.fondosDe(idCuenta);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CAJERO')")
    public Pagina<Fondo> fondos(EstadoFondo estado, int pagina, int tamano) {
        return caja.fondos(estado, pagina, tamano);
    }

    /** El efectivo ya se entregó físicamente al mesero de la cuenta. */
    @Transactional
    @PreAuthorize("hasAuthority('CAJERO')")
    public Fondo entregarFondo(EmpleadoPrincipal actor, String clave, Long idCuenta, BigDecimal monto) {
        return idempotencia.ejecutar(actor.id(), "ENTREGAR_FONDO", idCuenta.toString(), clave,
                Map.of("monto", monto.setScale(2).toPlainString()), Fondo.class, () -> {
                    Cuenta cuenta = cuentas.bloquearConConsumo(idCuenta);
                    Long id = caja.insertarFondo(idCuenta, actor.id(), cuenta.meseroId(), monto,
                            OffsetDateTime.now(reloj)).orElseThrow(() -> ReglaNegocioException.conflicto(
                                    "FONDO_VIGENTE", "La cuenta ya tiene un fondo de cambio."));
                    auditoria.registrar(actor.id(), "FONDO_ENTREGADO", "FONDO_CAMBIO", id,
                            Map.of("cuenta", idCuenta, "mesero", cuenta.meseroId(), "monto", monto.toPlainString()));
                    return caja.fondo(id);
                });
    }

    /** El mesero regresó el fondo antes del cobro. Se conserva y permite entregar otro. */
    @Transactional
    @PreAuthorize("hasAuthority('CAJERO')")
    public Fondo anularFondo(EmpleadoPrincipal actor, String clave, Long idCuenta, String justificacion) {
        String motivo = justificacion.strip();
        return idempotencia.ejecutar(actor.id(), "ANULAR_FONDO", idCuenta.toString(), clave,
                Map.of("justificacion", motivo), Fondo.class, () -> {
                    cuentas.bloquearCualquiera(idCuenta);
                    Long id = caja.pendienteDe(idCuenta).orElseThrow(() -> ReglaNegocioException.conflicto(
                            "SIN_FONDO_PENDIENTE", "La cuenta no tiene un fondo de cambio pendiente."));
                    caja.anular(id, actor.id(), OffsetDateTime.now(reloj), motivo);
                    auditoria.registrar(actor.id(), "FONDO_ANULADO", "FONDO_CAMBIO", id,
                            Map.of("cuenta", idCuenta, "justificacion", motivo));
                    return caja.fondo(id);
                });
    }

    // --- Entrega de efectivo · RF25

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CAJERO')")
    public List<PagoEfectivo> pendientes(Long mesero) {
        return caja.pendientes(mesero);
    }

    @Transactional
    @PreAuthorize("hasAuthority('CAJERO')")
    public Entrega recibir(EmpleadoPrincipal actor, String clave, Recepcion recepcion) {
        TreeSet<Long> ids = new TreeSet<>(recepcion.pagoIds());
        if (ids.size() != recepcion.pagoIds().size()) {
            throw ReglaNegocioException.invalido("pagoIds", "No repitas pagos.");
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("meseroId", recepcion.meseroId());
        cuerpo.put("pagoIds", ids);
        cuerpo.put("montoRecibido", recepcion.montoRecibido().setScale(2).toPlainString());
        cuerpo.put("fondoRecibido", recepcion.fondoRecibido().setScale(2).toPlainString());
        return idempotencia.ejecutar(actor.id(), "RECIBIR_EFECTIVO", recepcion.meseroId().toString(), clave, cuerpo,
                Entrega.class, () -> aplicarRecepcion(actor, recepcion, ids));
    }

    private Entrega aplicarRecepcion(EmpleadoPrincipal actor, Recepcion recepcion, TreeSet<Long> ids) {
        if (caja.bloquearPagos(ids).size() != ids.size()) {
            throw ReglaNegocioException.invalido("pagoIds", "Un pago no existe.");
        }
        List<PagoEfectivo> pagos = caja.pagosEnEfectivo(ids);
        if (pagos.size() != ids.size()) {
            throw ReglaNegocioException.conflicto("PAGO_NO_EFECTIVO",
                    "Solo se entregan pagos en efectivo; tarjeta y transferencia no pasan por caja.", "pagoIds");
        }
        if (pagos.stream().anyMatch(p -> !p.meseroId().equals(recepcion.meseroId()))) {
            throw ReglaNegocioException.conflicto("PAGO_DE_OTRO_MESERO",
                    "Todos los pagos deben ser del mesero que entrega.", "pagoIds");
        }
        if (!caja.entregados(ids).isEmpty()) {
            throw ReglaNegocioException.conflicto("PAGO_ENTREGADO", "Un pago ya se entregó a caja. Actualiza la lista.",
                    "pagoIds");
        }
        BigDecimal suma = pagos.stream().map(PagoEfectivo::monto).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal fondos = pagos.stream().map(PagoEfectivo::fondo).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (suma.compareTo(recepcion.montoRecibido()) != 0) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT, "MONTO_DISTINTO",
                    "El efectivo recibido no coincide con los pagos: deben ser " + suma.toPlainString() + ".",
                    Map.of("montoRecibido", "Deben ser " + suma.toPlainString() + "."));
        }
        if (fondos.compareTo(recepcion.fondoRecibido()) != 0) {
            throw new ReglaNegocioException(HttpStatus.CONFLICT, "FONDO_DISTINTO",
                    "El fondo recibido no coincide con el entregado: deben ser " + fondos.toPlainString() + ".",
                    Map.of("fondoRecibido", "Deben ser " + fondos.toPlainString() + "."));
        }
        Long id = caja.insertarEntrega(recepcion.meseroId(), actor.id(), suma, fondos, OffsetDateTime.now(reloj));
        caja.asociar(id, ids);
        caja.liquidar(id, pagos.stream().map(PagoEfectivo::fondoId).filter(Objects::nonNull).toList());
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("mesero", recepcion.meseroId());
        detalle.put("pagos", ids);
        detalle.put("monto", suma.toPlainString());
        detalle.put("fondo", fondos.toPlainString());
        auditoria.registrar(actor.id(), "EFECTIVO_RECIBIDO", "ENTREGA_EFECTIVO", id, detalle);
        return caja.entrega(id);
    }
}
