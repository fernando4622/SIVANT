package mx.sivant.servicio.aplicacion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import mx.sivant.servicio.dominio.EstadoComanda;
import mx.sivant.servicio.dominio.EstadoCuenta;

public final class DatosServicio {

    private DatosServicio() {
    }

    /** `cuentaId` es nulo si la mesa está libre. */
    public record Mesa(Long id, int numero, Long cuentaId, Long meseroId, String mesero) {
    }

    public record ResumenCuenta(Long id, int mesa, Long meseroId, String mesero, EstadoCuenta estado,
            OffsetDateTime fechaHoraApertura, OffsetDateTime fechaHoraCierre, BigDecimal total) {
    }

    public record Cuenta(Long id, Long mesaId, int mesa, Long meseroId, String mesero, EstadoCuenta estado,
            OffsetDateTime fechaHoraApertura, OffsetDateTime fechaHoraCierre, int version, List<Comanda> comandas) {

        /** Suma de comandas no canceladas, con los precios guardados. */
        public BigDecimal total() {
            return comandas.stream().filter(c -> c.estado() != EstadoComanda.CANCELADA).map(Comanda::subtotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        public boolean abierta() {
            return estado == EstadoCuenta.ABIERTA;
        }

        public boolean tieneComandasVigentes() {
            return comandas.stream().anyMatch(c -> c.estado() != EstadoComanda.CANCELADA);
        }
    }

    public record Comanda(Long id, Long cuentaId, EstadoComanda estado, OffsetDateTime fechaHoraEnvio, int version,
            List<Detalle> detalles, Cancelacion cancelacion) {

        public BigDecimal subtotal() {
            return detalles.stream().map(Detalle::subtotal).reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    public record Detalle(Long id, Long productoId, String producto, int cantidad, BigDecimal precioUnitario,
            String observaciones) {

        public BigDecimal subtotal() {
            return precioUnitario.multiply(BigDecimal.valueOf(cantidad));
        }
    }

    public record Cancelacion(String cajero, OffsetDateTime fechaHora, String justificacion) {
    }

    public record EnCola(Long id, Long cuentaId, int mesa, String mesero, EstadoComanda estado,
            OffsetDateTime fechaHoraEnvio, int version, List<Detalle> detalles) {
    }

    public record LineaNueva(Long productoId, int cantidad, String observaciones) {
    }

    public record LineaEditada(Long detalleId, int cantidad) {
    }
}
