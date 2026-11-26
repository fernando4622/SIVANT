package mx.sivant.ventas.aplicacion;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import mx.sivant.auditoria.aplicacion.RegistroAuditoria;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import mx.sivant.infraestructura.idempotencia.Idempotencia;
import mx.sivant.ventas.aplicacion.DatosCaja.Entrega;
import mx.sivant.ventas.aplicacion.DatosCaja.PagoEfectivo;
import mx.sivant.ventas.aplicacion.DatosCaja.Recepcion;
import mx.sivant.ventas.aplicacion.DatosCaja.Recibido;
import mx.sivant.ventas.infraestructura.CajaJdbc;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF25 y RF26. Al cobrar en efectivo el mesero lleva el dinero a caja. El cajero recibe el pago y, si el cliente pagó con
 * más, entrega el cambio: el fondo de cambio de ese cobro. La entrega guarda por separado el importe de los pagos y el
 * cambio.
 */
@Service
public class Caja {

    public static final int PAGOS_POR_ENTREGA = 100;

    private final CajaJdbc caja;
    private final Idempotencia idempotencia;
    private final RegistroAuditoria auditoria;
    private final Clock reloj;

    public Caja(CajaJdbc caja, Idempotencia idempotencia, RegistroAuditoria auditoria, Clock reloj) {
        this.caja = caja;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
        this.reloj = reloj;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('CAJERO')")
    public List<PagoEfectivo> pendientes(Long mesero) {
        return caja.pendientes(mesero);
    }

    @Transactional
    @PreAuthorize("hasAuthority('CAJERO')")
    public Entrega recibir(EmpleadoPrincipal actor, String clave, Recepcion recepcion) {
        TreeMap<Long, BigDecimal> recibidos = new TreeMap<>();
        for (Recibido r : recepcion.pagos()) {
            if (recibidos.put(r.pagoId(), r.montoRecibido()) != null) {
                throw ReglaNegocioException.invalido("pagos", "No repitas pagos.");
            }
        }
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("meseroId", recepcion.meseroId());
        recibidos.forEach((id, monto) -> cuerpo.put(id.toString(), monto.setScale(2).toPlainString()));
        return idempotencia.ejecutar(actor.id(), "RECIBIR_EFECTIVO", recepcion.meseroId().toString(), clave, cuerpo,
                Entrega.class, () -> aplicar(actor, recepcion.meseroId(), recibidos));
    }

    private Entrega aplicar(EmpleadoPrincipal actor, Long mesero, TreeMap<Long, BigDecimal> recibidos) {
        if (caja.bloquearPagos(recibidos.keySet()).size() != recibidos.size()) {
            throw ReglaNegocioException.invalido("pagos", "Un pago no existe.");
        }
        List<PagoEfectivo> pagos = caja.pagosEnEfectivo(recibidos.keySet());
        if (pagos.size() != recibidos.size()) {
            throw ReglaNegocioException.conflicto("PAGO_NO_EFECTIVO",
                    "Solo se reciben pagos en efectivo; tarjeta y transferencia no pasan por caja.", "pagos");
        }
        if (pagos.stream().anyMatch(p -> !p.meseroId().equals(mesero))) {
            throw ReglaNegocioException.conflicto("PAGO_DE_OTRO_MESERO", "Todos los pagos deben ser del mismo mesero.",
                    "pagos");
        }
        if (!caja.entregados(recibidos.keySet()).isEmpty()) {
            throw ReglaNegocioException.conflicto("PAGO_ENTREGADO", "Un pago ya se recibió en caja. Actualiza la lista.",
                    "pagos");
        }
        BigDecimal suma = BigDecimal.ZERO;
        BigDecimal cambio = BigDecimal.ZERO;
        for (PagoEfectivo p : pagos) {
            BigDecimal recibido = recibidos.get(p.id());
            if (recibido.compareTo(p.monto()) < 0) {
                throw new ReglaNegocioException(HttpStatus.CONFLICT, "MONTO_INSUFICIENTE",
                        "Lo recibido no cubre el pago de la venta " + p.ventaId() + ".",
                        Map.of("pagos", "La venta " + p.ventaId() + " es de " + p.monto().toPlainString() + "."));
            }
            suma = suma.add(p.monto());
            cambio = cambio.add(recibido.subtract(p.monto()));
        }
        OffsetDateTime ahora = OffsetDateTime.now(reloj);
        Long id = caja.insertarEntrega(mesero, actor.id(), suma, cambio, ahora);
        for (PagoEfectivo p : pagos) {
            caja.asociar(id, p.id());
            BigDecimal recibido = recibidos.get(p.id());
            if (recibido.compareTo(p.monto()) > 0) {
                caja.registrarCambio(id, p.id(), actor.id(), recibido, recibido.subtract(p.monto()), ahora);
            }
        }
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("mesero", mesero);
        detalle.put("pagos", recibidos.keySet());
        detalle.put("monto", suma.toPlainString());
        detalle.put("cambio", cambio.toPlainString());
        auditoria.registrar(actor.id(), "EFECTIVO_RECIBIDO", "ENTREGA_EFECTIVO", id, detalle);
        return caja.entrega(id);
    }
}
