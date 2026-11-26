package mx.sivant.servicio.aplicacion;

import java.math.BigDecimal;
import java.util.List;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import mx.sivant.servicio.aplicacion.DatosServicio.Cuenta;
import mx.sivant.servicio.dominio.EstadoComanda;
import mx.sivant.servicio.dominio.EstadoCuenta;
import mx.sivant.servicio.infraestructura.ServicioJdbc;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Lo que servicio ofrece a ventas, dentro de su transacción. */
@Service
@Transactional(propagation = Propagation.MANDATORY)
public class CobroDeCuentas {

    private static final BigDecimal MAXIMO = new BigDecimal("999999999999.99");

    private final ServicioJdbc servicio;

    public CobroDeCuentas(ServicioJdbc servicio) {
        this.servicio = servicio;
    }

    /** Bloquea la cuenta y comprueba que el mesero pueda cobrarla. */
    public Cuenta bloquear(Long mesero, Long id, int version) {
        var bloqueada = servicio.bloquearCuenta(id).filter(c -> c.meseroId().equals(mesero))
                .orElseThrow(GestionCuentas::sinCuenta);
        if (bloqueada.estado() != EstadoCuenta.ABIERTA) {
            throw ReglaNegocioException.conflicto("CUENTA_CERRADA", "La cuenta ya está cerrada.");
        }
        GestionCuentas.exigirVersion(bloqueada.version(), version);
        Cuenta cuenta = cuenta(id);
        List<EstadoComanda> vigentes = cuenta.comandas().stream().map(DatosServicio.Comanda::estado)
                .filter(e -> e != EstadoComanda.CANCELADA).toList();
        if (vigentes.isEmpty()) {
            throw ReglaNegocioException.conflicto("CUENTA_SIN_COMANDAS", "La cuenta no tiene comandas por cobrar.");
        }
        if (vigentes.stream().anyMatch(e -> e != EstadoComanda.SERVIDA)) {
            throw ReglaNegocioException.conflicto("COMANDAS_PENDIENTES",
                    "Hay comandas por servir. Sírvelas o pide al cajero cancelarlas.");
        }
        if (cuenta.total().signum() <= 0 || cuenta.total().compareTo(MAXIMO) > 0) {
            throw ReglaNegocioException.conflicto("TOTAL_INVALIDO", "El total de la cuenta no puede cobrarse.");
        }
        return cuenta;
    }

    /** Bloquea una cuenta abierta con consumo por cobrar, sin importar el mesero. Para el fondo de cambio. */
    public Cuenta bloquearConConsumo(Long id) {
        var bloqueada = servicio.bloquearCuenta(id).orElseThrow(GestionCuentas::sinCuenta);
        if (bloqueada.estado() != EstadoCuenta.ABIERTA) {
            throw ReglaNegocioException.conflicto("CUENTA_CERRADA", "La cuenta ya está cerrada.");
        }
        Cuenta cuenta = cuenta(id);
        if (!cuenta.tieneComandasVigentes()) {
            throw ReglaNegocioException.conflicto("CUENTA_SIN_COMANDAS", "La cuenta no tiene comandas por cobrar.");
        }
        if (cuenta.total().signum() <= 0) {
            throw ReglaNegocioException.conflicto("TOTAL_INVALIDO", "El total de la cuenta no puede cobrarse.");
        }
        return cuenta;
    }

    /** Bloquea la cuenta para cambiar su fondo de cambio. */
    public Cuenta bloquearCualquiera(Long id) {
        servicio.bloquearCuenta(id).orElseThrow(GestionCuentas::sinCuenta);
        return cuenta(id);
    }

    public Cuenta cerrar(Long id) {
        servicio.cerrarCuenta(id);
        return cuenta(id);
    }

    public Cuenta cuenta(Long id) {
        return servicio.cuenta(id).orElseThrow(GestionCuentas::sinCuenta);
    }
}
