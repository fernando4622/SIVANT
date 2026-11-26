package mx.sivant.inventario.aplicacion;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import mx.sivant.inventario.dominio.EstadoSolicitud;
import mx.sivant.inventario.dominio.TipoHistorial;

public final class DatosInventario {

    private DatosInventario() {
    }

    public enum TipoArticulo {
        INSUMO, PRODUCTO
    }

    public record Existencia(Long id, Long articuloId, String nombre, TipoArticulo tipo, String unidadMedida,
            boolean activo, BigDecimal cantidadActual, BigDecimal nivelMinimo, OffsetDateTime fechaActualizacion,
            int version) {

        public boolean nivelBajo() {
            return cantidadActual.compareTo(nivelMinimo) <= 0;
        }
    }

    /** `cambio` tiene signo. En un ajuste rechazado es el cambio que no se aplicó y no hay saldo posterior. */
    public record Historial(Long id, TipoHistorial tipo, Long existenciaId, String articulo, String unidadMedida,
            BigDecimal cambio, BigDecimal saldoAnterior, BigDecimal saldoPosterior, OffsetDateTime fechaHora,
            String empleado, Long solicitudId, Long comandaId, String motivo, String causa) {
    }

    /** `existenciaCambiada` indica que la solicitud ya no puede autorizarse. */
    public record Solicitud(Long id, Long existenciaId, String articulo, String unidadMedida,
            BigDecimal cantidadAnterior, BigDecimal cantidadSolicitada, BigDecimal cantidadActual,
            String justificacion, EstadoSolicitud estado, String almacenista, OffsetDateTime fechaHoraSolicitud,
            String administrador, OffsetDateTime fechaHoraResolucion, boolean existenciaCambiada) {
    }

    public record MovimientoRegistrado(Historial movimiento, Existencia existencia) {
    }
}
