package mx.sivant.inventario.aplicacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.TreeMap;
import mx.sivant.auditoria.aplicacion.RegistroAuditoria;
import mx.sivant.infraestructura.api.Pagina;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import mx.sivant.infraestructura.idempotencia.Idempotencia;
import mx.sivant.inventario.aplicacion.DatosInventario.Existencia;
import mx.sivant.inventario.aplicacion.DatosInventario.Historial;
import mx.sivant.inventario.aplicacion.DatosInventario.MovimientoRegistrado;
import mx.sivant.inventario.aplicacion.DatosInventario.Solicitud;
import mx.sivant.inventario.aplicacion.DatosInventario.TipoArticulo;
import mx.sivant.inventario.dominio.EstadoSolicitud;
import mx.sivant.inventario.dominio.TipoHistorial;
import mx.sivant.inventario.dominio.TipoMovimientoManual;
import mx.sivant.inventario.infraestructura.InventarioJdbc;
import mx.sivant.inventario.infraestructura.InventarioJdbc.SolicitudBloqueada;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF15 a RF18. */
@Service
public class GestionInventario {

    private static final String CONSULTA = "hasAnyAuthority('ADMINISTRADOR', 'ALMACENISTA')";
    private static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Mexico_City");
    /** numeric(18,6) admite 12 enteros. */
    private static final BigDecimal SALDO_MAXIMO = new BigDecimal("1000000000000");

    private final InventarioJdbc inventario;
    private final Idempotencia idempotencia;
    private final RegistroAuditoria auditoria;

    public GestionInventario(InventarioJdbc inventario, Idempotencia idempotencia, RegistroAuditoria auditoria) {
        this.inventario = inventario;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    @PreAuthorize(CONSULTA)
    public Pagina<Existencia> existencias(String q, TipoArticulo tipo, Boolean nivelBajo, int pagina, int tamano) {
        return inventario.existencias(q, tipo, nivelBajo, pagina, tamano);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(CONSULTA)
    public Existencia existencia(Long id) {
        return inventario.existencia(id).orElseThrow(GestionInventario::sinExistencia);
    }

    /** Los días se interpretan en la zona del negocio e incluyen `hasta` completo. */
    @Transactional(readOnly = true)
    @PreAuthorize(CONSULTA)
    public Pagina<Historial> historial(Long existencia, String q, TipoHistorial tipo, LocalDate desde, LocalDate hasta,
            int pagina, int tamano) {
        if (existencia != null) {
            existencia(existencia);
        }
        if (desde != null && hasta != null && hasta.isBefore(desde)) {
            throw ReglaNegocioException.invalido("hasta", "La fecha final no puede ser anterior a la inicial.");
        }
        return inventario.historial(existencia, q, tipo, inicio(desde), inicio(hasta == null ? null : hasta.plusDays(1)),
                pagina, tamano);
    }

    @Transactional
    @PreAuthorize("hasAuthority('ALMACENISTA')")
    public MovimientoRegistrado registrarMovimiento(Long actor, String clave, Long idExistencia,
            TipoMovimientoManual tipo, BigDecimal cantidad) {
        Map<String, String> cuerpo = new TreeMap<>(
                Map.of("tipo", tipo.name(), "cantidad", cantidad.stripTrailingZeros().toPlainString()));
        return idempotencia.ejecutar(actor, "REGISTRAR_MOVIMIENTO", idExistencia.toString(), clave, cuerpo,
                MovimientoRegistrado.class, () -> aplicarMovimiento(actor, idExistencia, tipo, cantidad));
    }

    private MovimientoRegistrado aplicarMovimiento(Long actor, Long id, TipoMovimientoManual tipo,
            BigDecimal cantidad) {
        Existencia existencia = inventario.bloquearExistencia(id).orElseThrow(GestionInventario::sinExistencia);
        BigDecimal anterior = existencia.cantidadActual();
        BigDecimal posterior;
        TipoHistorial registro;
        if (tipo == TipoMovimientoManual.ENTRADA_MANUAL) {
            registro = TipoHistorial.ENTRADA_MANUAL;
            posterior = anterior.add(cantidad);
            if (posterior.compareTo(SALDO_MAXIMO) >= 0) {
                throw ReglaNegocioException.invalido("cantidad", "La existencia resultante es demasiado grande.");
            }
        } else {
            registro = TipoHistorial.SALIDA_MANUAL;
            posterior = anterior.subtract(cantidad);
            if (posterior.signum() < 0) {
                throw ReglaNegocioException.conflicto("EXISTENCIA_INSUFICIENTE",
                        "La cantidad supera la existencia disponible.", "cantidad");
            }
        }
        inventario.cambiarSaldo(id, posterior);
        Long movimiento = inventario.insertarMovimiento(id, actor, null, registro, anterior, posterior, null);
        auditoria.registrar(actor, "MOVIMIENTO_REGISTRADO", "EXISTENCIA", id, Map.of("movimiento", movimiento,
                "tipo", registro.name(), "saldoAnterior", anterior.toPlainString(),
                "saldoPosterior", posterior.toPlainString()));
        return new MovimientoRegistrado(inventario.movimiento(movimiento), existencia(id));
    }

    @Transactional
    @PreAuthorize("hasAuthority('ALMACENISTA')")
    public Solicitud solicitarAjuste(Long actor, String clave, Long idExistencia, BigDecimal solicitada,
            String justificacion) {
        Map<String, String> cuerpo = new TreeMap<>(Map.of("cantidadSolicitada",
                solicitada.stripTrailingZeros().toPlainString(), "justificacion", justificacion.strip()));
        return idempotencia.ejecutar(actor, "SOLICITAR_AJUSTE", idExistencia.toString(), clave, cuerpo,
                Solicitud.class, () -> {
                    Existencia existencia = inventario.bloquearExistencia(idExistencia)
                            .orElseThrow(() -> ReglaNegocioException.invalido("existenciaId", "La existencia no existe."));
                    if (existencia.cantidadActual().compareTo(solicitada) == 0) {
                        throw ReglaNegocioException.invalido("cantidadSolicitada",
                                "La cantidad es igual a la existencia actual.");
                    }
                    Long id = inventario.insertarSolicitud(existencia, actor, solicitada, justificacion.strip());
                    auditoria.registrar(actor, "AJUSTE_SOLICITADO", "SOLICITUD_AJUSTE", id, Map.of(
                            "existencia", idExistencia, "cantidadAnterior", existencia.cantidadActual().toPlainString(),
                            "cantidadSolicitada", solicitada.toPlainString()));
                    return solicitud(id);
                });
    }

    @Transactional(readOnly = true)
    @PreAuthorize(CONSULTA)
    public Pagina<Solicitud> solicitudes(EstadoSolicitud estado, Long existencia, int pagina, int tamano) {
        return inventario.solicitudes(estado, existencia, pagina, tamano);
    }

    @Transactional(readOnly = true)
    @PreAuthorize(CONSULTA)
    public Solicitud solicitud(Long id) {
        return inventario.solicitud(id).orElseThrow(GestionInventario::sinSolicitud);
    }

    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public Solicitud resolver(Long actor, String clave, Long id, EstadoSolicitud decision) {
        return idempotencia.ejecutar(actor, "RESOLVER_AJUSTE", id.toString(), clave, Map.of("decision", decision),
                Solicitud.class, () -> aplicarResolucion(actor, id, decision));
    }

    private Solicitud aplicarResolucion(Long actor, Long id, EstadoSolicitud decision) {
        SolicitudBloqueada solicitud = inventario.bloquearSolicitud(id).orElseThrow(GestionInventario::sinSolicitud);
        if (solicitud.estado() != EstadoSolicitud.PENDIENTE) {
            throw ReglaNegocioException.conflicto("AJUSTE_RESUELTO", "La solicitud ya fue resuelta.");
        }
        if (decision == EstadoSolicitud.AUTORIZADA) {
            Existencia existencia = inventario.bloquearExistencia(solicitud.existenciaId())
                    .orElseThrow(GestionInventario::sinExistencia);
            if (existencia.version() != solicitud.versionSolicitada()) {
                throw ReglaNegocioException.conflicto("EXISTENCIA_CAMBIADA",
                        "La existencia cambió desde la solicitud. Recházala y pide una nueva.");
            }
            inventario.cambiarSaldo(existencia.id(), solicitud.cantidadSolicitada());
            inventario.insertarMovimiento(existencia.id(), actor, id, TipoHistorial.AJUSTE,
                    existencia.cantidadActual(), solicitud.cantidadSolicitada(), solicitud.justificacion());
        }
        inventario.resolverSolicitud(id, decision, actor);
        auditoria.registrar(actor, "AJUSTE_" + decision.name(), "SOLICITUD_AJUSTE", id,
                Map.of("existencia", solicitud.existenciaId()));
        return solicitud(id);
    }

    private static OffsetDateTime inicio(LocalDate dia) {
        return dia == null ? null : dia.atStartOfDay(ZONA_NEGOCIO).toOffsetDateTime();
    }

    private static ReglaNegocioException sinExistencia() {
        return ReglaNegocioException.noEncontrado("La existencia no existe.");
    }

    private static ReglaNegocioException sinSolicitud() {
        return ReglaNegocioException.noEncontrado("La solicitud no existe.");
    }
}
