package mx.sivant.ventas.aplicacion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import mx.sivant.ventas.dominio.EstadoVenta;

public final class DatosCaja {

    private DatosCaja() {
    }

    /** Pago en efectivo que caja todavía no recibe. */
    public record PagoEfectivo(Long id, Long ventaId, int mesa, Long meseroId, String mesero, OffsetDateTime fechaHora,
            BigDecimal monto, EstadoVenta estadoVenta) {
    }

    /** Lo que el cliente entregó por un pago. El cambio es la diferencia. */
    public record Recibido(Long pagoId, BigDecimal montoRecibido) {
    }

    public record Recepcion(Long meseroId, List<Recibido> pagos) {
    }

    public record PagoRecibido(PagoEfectivo pago, BigDecimal montoRecibido, BigDecimal cambio) {
    }

    /** `monto` suma los pagos; `cambio`, lo que caja devolvió. */
    public record Entrega(Long id, OffsetDateTime fechaHora, String mesero, String cajero, BigDecimal monto,
            BigDecimal cambio, List<PagoRecibido> pagos) {
    }
}
