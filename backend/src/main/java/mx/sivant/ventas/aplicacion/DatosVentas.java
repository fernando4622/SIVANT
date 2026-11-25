package mx.sivant.ventas.aplicacion;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import mx.sivant.servicio.aplicacion.DatosServicio.Cuenta;
import mx.sivant.ventas.dominio.EstadoVenta;
import mx.sivant.ventas.dominio.MetodoPago;

public final class DatosVentas {

    private DatosVentas() {
    }

    public record DatosCobro(int versionCuenta, MetodoPago metodo, String referenciaExterna, BigDecimal totalEsperado) {
    }

    public record Pago(Long id, MetodoPago metodo, BigDecimal monto, String referenciaExterna, OffsetDateTime fechaHora,
            String mesero) {
    }

    public record Cancelacion(String administrador, OffsetDateTime fechaHora, String justificacion,
            BigDecimal montoDevuelto, String referenciaReversion) {
    }

    public record Venta(Long id, Long cuentaId, int mesa, OffsetDateTime fechaHora, BigDecimal total,
            EstadoVenta estado, Pago pago, Cancelacion cancelacion) {
    }

    /** La cuenta conserva las comandas y los precios aplicados. */
    public record VentaConCuenta(Venta venta, Cuenta cuenta) {
    }

    public record ResumenVenta(Long id, OffsetDateTime fechaHora, int mesa, String mesero, MetodoPago metodo,
            BigDecimal total, EstadoVenta estado) {
    }

    public record Filtro(LocalDate desde, LocalDate hasta, EstadoVenta estado, MetodoPago metodo, Long mesero,
            Long mesa) {
    }

    public record Opcion(Long id, String nombre) {
    }

    /** Meseros y mesas con ventas en la ventana consultable. */
    public record Opciones(List<Opcion> meseros, List<Opcion> mesas) {
    }
}
