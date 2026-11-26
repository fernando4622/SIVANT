package mx.sivant.ventas.aplicacion;

import java.util.LinkedHashMap;
import java.util.Map;
import mx.sivant.auditoria.aplicacion.RegistroAuditoria;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import mx.sivant.infraestructura.idempotencia.Idempotencia;
import mx.sivant.servicio.aplicacion.CobroDeCuentas;
import mx.sivant.servicio.aplicacion.DatosServicio.Cuenta;
import mx.sivant.ventas.aplicacion.DatosVentas.DatosCobro;
import mx.sivant.ventas.aplicacion.DatosVentas.VentaConCuenta;
import mx.sivant.ventas.dominio.MetodoPago;
import mx.sivant.ventas.dominio.Referencias;
import mx.sivant.ventas.infraestructura.CajaJdbc;
import mx.sivant.ventas.infraestructura.VentasJdbc;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * RF04 y RF05. La venta, su pago y el cierre de la cuenta se confirman juntos. El cargo con tarjeta ocurre antes y fuera
 * de SIVANT: repetir el registro con la misma clave no crea otra venta.
 */
@Service
public class Cobros {

    private final VentasJdbc ventas;
    private final CobroDeCuentas cuentas;
    private final Idempotencia idempotencia;
    private final RegistroAuditoria auditoria;
    private final CajaJdbc caja;

    public Cobros(VentasJdbc ventas, CobroDeCuentas cuentas, Idempotencia idempotencia, RegistroAuditoria auditoria,
            CajaJdbc caja) {
        this.caja = caja;
        this.ventas = ventas;
        this.cuentas = cuentas;
        this.idempotencia = idempotencia;
        this.auditoria = auditoria;
    }

    @Transactional
    @PreAuthorize("hasAuthority('MESERO')")
    public VentaConCuenta cobrar(EmpleadoPrincipal actor, String clave, Long idCuenta, DatosCobro datos) {
        String referencia = referencia(datos.metodo(), Referencias.normalizar(datos.referenciaExterna()));
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("versionCuenta", datos.versionCuenta());
        cuerpo.put("metodo", datos.metodo().name());
        cuerpo.put("referenciaExterna", referencia);
        cuerpo.put("totalEsperado", datos.totalEsperado().setScale(2).toPlainString());
        return idempotencia.ejecutar(actor.id(), "COBRAR_CUENTA", idCuenta.toString(), clave, cuerpo,
                VentaConCuenta.class, () -> aplicar(actor, idCuenta, datos, referencia));
    }

    private VentaConCuenta aplicar(EmpleadoPrincipal actor, Long idCuenta, DatosCobro datos, String referencia) {
        Cuenta cuenta = cuentas.bloquear(actor.id(), idCuenta, datos.versionCuenta());
        if (cuenta.total().compareTo(datos.totalEsperado()) != 0) {
            throw ReglaNegocioException.conflicto("TOTAL_CAMBIADO", "El total de la cuenta cambió. Revísalo.");
        }
        if (cuenta.fondoPendiente() && datos.metodo() != MetodoPago.EFECTIVO) {
            throw ReglaNegocioException.conflicto("FONDO_PENDIENTE",
                    "La cuenta tiene un fondo de cambio: cóbrala en efectivo.", "metodo");
        }
        Cuenta cerrada = cuentas.cerrar(idCuenta);
        Long id = ventas.insertarVenta(idCuenta, cerrada.fechaHoraCierre(), cuenta.total());
        Long pago = ventas.insertarPago(id, actor.id(), cerrada.fechaHoraCierre(), cuenta.total(), datos.metodo(),
                referencia).orElseThrow(() -> ReglaNegocioException.conflicto("REFERENCIA_REPETIDA",
                        "Esa referencia ya se registró hoy en otro cobro.", "referenciaExterna"));
        if (cuenta.fondoPendiente()) {
            caja.aplicarFondo(cuenta.fondo().id(), pago);
        }
        Map<String, Object> detalle = new LinkedHashMap<>();
        detalle.put("cuenta", idCuenta);
        detalle.put("total", cuenta.total().toPlainString());
        detalle.put("metodo", datos.metodo().name());
        detalle.put("referenciaExterna", referencia);
        detalle.put("fondo", cuenta.fondoPendiente() ? cuenta.fondo().id() : null);
        auditoria.registrar(actor.id(), "VENTA_COBRADA", "VENTA", id, detalle);
        return new VentaConCuenta(ventas.venta(id).orElseThrow(), cuentas.cuenta(idCuenta));
    }

    private static String referencia(MetodoPago metodo, String referencia) {
        String campo = "referenciaExterna";
        if (metodo == MetodoPago.EFECTIVO && referencia != null) {
            throw ReglaNegocioException.invalido(campo, "El efectivo no lleva referencia.");
        }
        if (metodo == MetodoPago.TARJETA && referencia == null) {
            throw ReglaNegocioException.invalido(campo, "Escribe la referencia de autorización.");
        }
        exigirSinTarjeta(campo, referencia);
        return referencia;
    }

    /** Ninguna referencia puede ser el número de una tarjeta, sea cual sea el método. */
    static void exigirSinTarjeta(String campo, String referencia) {
        if (referencia != null && Referencias.pareceTarjeta(referencia)) {
            throw ReglaNegocioException.invalido(campo, "No captures el número de la tarjeta. Usa la referencia.");
        }
    }
}
