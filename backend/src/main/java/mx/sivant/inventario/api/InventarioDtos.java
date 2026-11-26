package mx.sivant.inventario.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import mx.sivant.infraestructura.api.Decimales;
import mx.sivant.inventario.aplicacion.DatosInventario;
import mx.sivant.inventario.aplicacion.DatosInventario.TipoArticulo;
import mx.sivant.inventario.dominio.CausaSalida;
import mx.sivant.inventario.dominio.EstadoSolicitud;
import mx.sivant.inventario.dominio.TipoHistorial;
import mx.sivant.inventario.dominio.TipoMovimientoManual;

final class InventarioDtos {

    private InventarioDtos() {
    }

    enum Decision {
        AUTORIZADA, RECHAZADA
    }

    record Movimiento(
            @NotNull(message = "Selecciona entrada o salida.") TipoMovimientoManual tipo,
            @NotNull(message = "Escribe la cantidad.")
            @Pattern(regexp = "^(?=.*[1-9])\\d{1,12}(\\.\\d{1,6})?$", message = "Escribe una cantidad mayor que cero.")
            String cantidad,
            CausaSalida causa,
            @Size(max = 300, message = "Usa 300 caracteres como máximo.") String nota) {
    }

    record NivelMinimo(
            @NotNull(message = "Escribe el nivel mínimo.")
            @Pattern(regexp = Decimales.CANTIDAD, message = "Escribe una cantidad válida, desde 0.") String nivelMinimo) {
    }

    record Ajuste(
            @NotNull(message = "Selecciona una existencia.")
            @Pattern(regexp = "^\\d{1,18}$", message = "Selecciona una existencia.") String existenciaId,
            @NotNull(message = "Escribe la cantidad.")
            @Pattern(regexp = Decimales.CANTIDAD, message = "Escribe una cantidad válida.") String cantidadSolicitada,
            @NotBlank(message = "Escribe la justificación.")
            @Size(max = 500, message = "Usa 500 caracteres como máximo.") String justificacion) {
    }

    record Resolucion(@NotNull(message = "Indica la decisión.") Decision decision) {
    }

    record Existencia(String id, String articuloId, String nombre, TipoArticulo tipo, String unidadMedida,
            boolean activo, String imagen, String cantidadActual, String nivelMinimo, boolean nivelBajo,
            OffsetDateTime fechaActualizacion, int version) {

        static Existencia de(DatosInventario.Existencia e) {
            return new Existencia(e.id().toString(), e.articuloId().toString(), e.nombre(), e.tipo(), e.unidadMedida(),
                    e.activo(), urlImagen(e), Decimales.cantidad(e.cantidadActual()), Decimales.cantidad(e.nivelMinimo()),
                    e.nivelBajo(), e.fechaActualizacion(), e.version());
        }

        private static String urlImagen(DatosInventario.Existencia e) {
            if (e.imagen() == null) {
                return null;
            }
            String archivo = e.imagen().substring(e.imagen().lastIndexOf('/') + 1);
            String recurso = e.tipo() == TipoArticulo.INSUMO ? "insumos" : "productos";
            return "/api/v1/" + recurso + "/" + e.articuloId() + "/imagen?v="
                    + archivo.substring(0, archivo.indexOf('.'));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Historial(String id, TipoHistorial tipo, String existenciaId, String articulo, String unidadMedida,
            String cambio, String saldoAnterior, String saldoPosterior, OffsetDateTime fechaHora, String empleado,
            String solicitudId, String comandaId, String motivo, String causa) {

        static Historial de(DatosInventario.Historial h) {
            return new Historial(h.id().toString(), h.tipo(), h.existenciaId().toString(), h.articulo(),
                    h.unidadMedida(), Decimales.cantidad(h.cambio()), Decimales.cantidad(h.saldoAnterior()),
                    opcional(h.saldoPosterior()), h.fechaHora(), h.empleado(),
                    h.solicitudId() == null ? null : h.solicitudId().toString(),
                    h.comandaId() == null ? null : h.comandaId().toString(), h.motivo(), h.causa());
        }
    }

    record MovimientoRegistrado(Historial movimiento, Existencia existencia) {

        static MovimientoRegistrado de(DatosInventario.MovimientoRegistrado m) {
            return new MovimientoRegistrado(Historial.de(m.movimiento()), Existencia.de(m.existencia()));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Solicitud(String id, String existenciaId, String articulo, String unidadMedida, String cantidadAnterior,
            String cantidadSolicitada, String cantidadActual, String justificacion, EstadoSolicitud estado,
            String almacenista, OffsetDateTime fechaHoraSolicitud, String administrador,
            OffsetDateTime fechaHoraResolucion, boolean existenciaCambiada) {

        static Solicitud de(DatosInventario.Solicitud s) {
            return new Solicitud(s.id().toString(), s.existenciaId().toString(), s.articulo(), s.unidadMedida(),
                    Decimales.cantidad(s.cantidadAnterior()), Decimales.cantidad(s.cantidadSolicitada()),
                    Decimales.cantidad(s.cantidadActual()), s.justificacion(), s.estado(), s.almacenista(),
                    s.fechaHoraSolicitud(), s.administrador(), s.fechaHoraResolucion(), s.existenciaCambiada());
        }
    }

    private static String opcional(BigDecimal valor) {
        return valor == null ? null : Decimales.cantidad(valor);
    }
}
