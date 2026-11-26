package mx.sivant.ventas.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.infraestructura.api.Decimales;
import mx.sivant.servicio.api.ServicioDtos;
import mx.sivant.ventas.aplicacion.DatosVentas;
import mx.sivant.ventas.dominio.EstadoVenta;
import mx.sivant.ventas.dominio.MetodoPago;
import mx.sivant.ventas.dominio.Referencias;

final class VentasDtos {

    private static final String REFERENCIA = "Usa hasta 40 letras, números o guiones.";

    private VentasDtos() {
    }

    // --- Entradas

    record Cobro(
            @NotNull(message = "Indica la versión.") Integer versionCuenta,
            @NotNull(message = "Selecciona el método de pago.") MetodoPago metodo,
            @Pattern(regexp = Referencias.FORMATO, message = REFERENCIA) String referenciaExterna,
            @NotNull(message = "Indica el total.")
            @Pattern(regexp = Decimales.DINERO, message = "Indica el total.") String totalEsperado) {

        DatosVentas.DatosCobro datos() {
            return new DatosVentas.DatosCobro(versionCuenta, metodo, referenciaExterna, new BigDecimal(totalEsperado));
        }
    }

    record Cancelacion(
            @NotNull(message = "Confirma la cancelación.")
            @AssertTrue(message = "Confirma la cancelación.") Boolean confirmado,
            @NotBlank(message = "Escribe la justificación.")
            @Size(max = 500, message = "Usa 500 caracteres como máximo.") String justificacion,
            @Pattern(regexp = Referencias.FORMATO, message = REFERENCIA) String referenciaReversion) {
    }

    // --- Respuestas

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Pago(String id, MetodoPago metodo, String monto, String referenciaExterna, OffsetDateTime fechaHora,
            String mesero) {

        static Pago de(DatosVentas.Pago p) {
            return new Pago(p.id().toString(), p.metodo(), Decimales.dinero(p.monto()), p.referenciaExterna(),
                    p.fechaHora(), p.mesero());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record CancelacionRespuesta(String administrador, OffsetDateTime fechaHora, String justificacion,
            String montoDevuelto, String referenciaReversion) {
    }

    record Resumen(String id, OffsetDateTime fechaHora, int mesa, String mesero, MetodoPago metodo, String total,
            EstadoVenta estado) {

        static Resumen de(DatosVentas.ResumenVenta v) {
            return new Resumen(v.id().toString(), v.fechaHora(), v.mesa(), v.mesero(), v.metodo(),
                    Decimales.dinero(v.total()), v.estado());
        }
    }

    record Opcion(String id, String nombre) {

        static Opcion de(DatosVentas.Opcion o) {
            return new Opcion(o.id().toString(), o.nombre());
        }
    }

    record Opciones(List<Opcion> meseros, List<Opcion> mesas) {

        static Opciones de(DatosVentas.Opciones o) {
            return new Opciones(o.meseros().stream().map(Opcion::de).toList(),
                    o.mesas().stream().map(Opcion::de).toList());
        }
    }

    record VentaCobrada(String id, OffsetDateTime fechaHora, String total, EstadoVenta estado) {
    }

    record Cobrada(VentaCobrada venta, Pago pago, ServicioDtos.Cuenta cuenta) {

        static Cobrada de(DatosVentas.VentaConCuenta r, EmpleadoPrincipal actor) {
            DatosVentas.Venta v = r.venta();
            return new Cobrada(new VentaCobrada(v.id().toString(), v.fechaHora(), Decimales.dinero(v.total()),
                    v.estado()), Pago.de(v.pago()), ServicioDtos.Cuenta.de(r.cuenta(), actor));
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Venta(String id, String cuentaId, int mesa, String mesero, OffsetDateTime fechaHora, EstadoVenta estado,
            String total, Pago pago, CancelacionRespuesta cancelacion, List<ServicioDtos.Comanda> comandas,
            List<String> acciones) {

        static Venta de(DatosVentas.VentaConCuenta r, EmpleadoPrincipal actor) {
            DatosVentas.Venta v = r.venta();
            DatosVentas.Cancelacion c = v.cancelacion();
            boolean cancelable = actor.rol().equals("ADMINISTRADOR") && v.estado() == EstadoVenta.COMPLETADA;
            return new Venta(v.id().toString(), v.cuentaId().toString(), v.mesa(), v.pago().mesero(), v.fechaHora(),
                    v.estado(), Decimales.dinero(v.total()), Pago.de(v.pago()),
                    c == null ? null : new CancelacionRespuesta(c.administrador(), c.fechaHora(), c.justificacion(),
                            Decimales.dinero(c.montoDevuelto()), c.referenciaReversion()),
                    r.cuenta().comandas().stream().map(o -> ServicioDtos.Comanda.de(o, false, actor)).toList(),
                    cancelable ? List.of("CANCELAR") : List.of());
        }
    }
}
