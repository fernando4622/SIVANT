package mx.sivant.ventas.aplicacion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import mx.sivant.ventas.dominio.EstadoFondo;
import mx.sivant.ventas.dominio.EstadoVenta;

public final class DatosCaja {

    private DatosCaja() {
    }

    public record Anulacion(String cajero, OffsetDateTime fechaHora, String justificacion) {
    }

    /** `ventaId` y `entregaId` indican dónde se aplicó y se liquidó. */
    public record Fondo(Long id, Long cuentaId, int mesa, String mesero, String cajero, BigDecimal monto,
            OffsetDateTime fechaHora, EstadoFondo estado, Long ventaId, Long entregaId, Anulacion anulacion) {
    }

    /** Pago en efectivo que el mesero aún no entrega a caja. Incluye el fondo aplicado, si lo hay. */
    public record PagoEfectivo(Long id, Long ventaId, int mesa, Long meseroId, String mesero, OffsetDateTime fechaHora,
            BigDecimal monto, EstadoVenta estadoVenta, Long fondoId, BigDecimal fondo) {
    }

    public record Recepcion(Long meseroId, List<Long> pagoIds, BigDecimal montoRecibido, BigDecimal fondoRecibido) {
    }

    public record Entrega(Long id, OffsetDateTime fechaHora, String mesero, String cajero, BigDecimal monto,
            BigDecimal montoFondo, List<PagoEfectivo> pagos) {
    }
}
