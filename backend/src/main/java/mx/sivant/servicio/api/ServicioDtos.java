package mx.sivant.servicio.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.infraestructura.api.Decimales;
import mx.sivant.servicio.aplicacion.DatosServicio;
import mx.sivant.servicio.aplicacion.EntregaComandas;
import mx.sivant.servicio.aplicacion.GestionCuentas;
import mx.sivant.servicio.dominio.EstadoComanda;
import mx.sivant.servicio.dominio.EstadoCuenta;

public final class ServicioDtos {

    private static final String ID = "^\\d{1,18}$";
    private static final String CANTIDAD = "Usa una cantidad de 1 a 999.";

    private ServicioDtos() {
    }

    // --- Entradas

    record Apertura(
            @NotNull(message = "Selecciona una mesa.")
            @Pattern(regexp = ID, message = "Selecciona una mesa.") String mesaId) {
    }

    record SoloVersion(@NotNull(message = "Indica la versión.") Integer version) {
    }

    record LineaNueva(
            @NotNull(message = "Selecciona un producto.")
            @Pattern(regexp = ID, message = "Selecciona un producto.") String productoId,
            @NotNull(message = CANTIDAD) @Min(value = 1, message = CANTIDAD)
            @Max(value = GestionCuentas.CANTIDAD_MAXIMA, message = CANTIDAD) Integer cantidad,
            @Size(max = 200, message = "Usa 200 caracteres como máximo.") String observaciones) {
    }

    record Envio(
            @NotEmpty(message = "Agrega al menos un producto.")
            @Size(max = 100, message = "Usa 100 productos como máximo.") List<@NotNull @Valid LineaNueva> detalles) {

        List<DatosServicio.LineaNueva> lineas() {
            return detalles.stream().map(d -> new DatosServicio.LineaNueva(Long.valueOf(d.productoId()), d.cantidad(),
                    d.observaciones())).toList();
        }
    }

    record LineaEditada(
            @NotNull(message = "Indica el detalle.")
            @Pattern(regexp = ID, message = "Indica el detalle.") String detalleId,
            @NotNull(message = CANTIDAD) @Min(value = 1, message = CANTIDAD)
            @Max(value = GestionCuentas.CANTIDAD_MAXIMA, message = CANTIDAD) Integer cantidad) {
    }

    record Edicion(
            @NotNull(message = "Indica la versión.") Integer version,
            @NotNull(message = "Indica los productos.")
            @Size(max = 100, message = "Usa 100 productos como máximo.") List<@NotNull @Valid LineaEditada> detalles) {

        List<DatosServicio.LineaEditada> lineas() {
            return detalles.stream().map(d -> new DatosServicio.LineaEditada(Long.valueOf(d.detalleId()),
                    d.cantidad())).toList();
        }
    }

    record Cancelacion(
            @NotNull(message = "Indica la versión.") Integer version,
            @NotBlank(message = "Escribe la justificación.")
            @Size(max = 500, message = "Usa 500 caracteres como máximo.") String justificacion) {
    }

    // --- Respuestas

    /** `cuentaId` solo se incluye si el actor puede consultar la cuenta. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Mesa(String id, int numero, boolean disponible, String cuentaId, String mesero) {

        static Mesa de(DatosServicio.Mesa m, EmpleadoPrincipal actor) {
            boolean consultable = m.cuentaId() != null
                    && (actor.rol().equals("CAJERO") || m.meseroId().equals(actor.id()));
            return new Mesa(m.id().toString(), m.numero(), m.cuentaId() == null,
                    consultable ? m.cuentaId().toString() : null, m.mesero());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ResumenCuenta(String id, int mesa, String mesero, EstadoCuenta estado, OffsetDateTime fechaHoraApertura,
            OffsetDateTime fechaHoraCierre, String total) {

        static ResumenCuenta de(DatosServicio.ResumenCuenta c) {
            return new ResumenCuenta(c.id().toString(), c.mesa(), c.mesero(), c.estado(), c.fechaHoraApertura(),
                    c.fechaHoraCierre(), Decimales.dinero(c.total()));
        }
    }

    public record Detalle(String id, String productoId, String producto, int cantidad, String precioUnitario,
            String subtotal, String observaciones) {

        static Detalle de(DatosServicio.Detalle d) {
            return new Detalle(d.id().toString(), d.productoId().toString(), d.producto(), d.cantidad(),
                    Decimales.dinero(d.precioUnitario()), Decimales.dinero(d.subtotal()), d.observaciones());
        }
    }

    public record CancelacionRespuesta(String cajero, OffsetDateTime fechaHora, String justificacion) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Comanda(String id, String cuentaId, EstadoComanda estado, OffsetDateTime fechaHoraEnvio, int version,
            String subtotal, List<Detalle> detalles, CancelacionRespuesta cancelacion, List<String> acciones) {

        public static Comanda de(DatosServicio.Comanda c, boolean cuentaAbierta, EmpleadoPrincipal actor) {
            List<String> acciones = new ArrayList<>();
            if (cuentaAbierta && actor.rol().equals("MESERO") && c.estado().esEditable()) {
                acciones.add("EDITAR");
            }
            if (actor.rol().equals("MESERO") && c.estado().puedePasarA(EstadoComanda.SERVIDA)) {
                acciones.add("SERVIR");
            }
            if (cuentaAbierta && actor.rol().equals("CAJERO") && c.estado().esCancelable()) {
                acciones.add("CANCELAR");
            }
            return new Comanda(c.id().toString(), c.cuentaId().toString(), c.estado(), c.fechaHoraEnvio(), c.version(),
                    Decimales.dinero(c.subtotal()), c.detalles().stream().map(Detalle::de).toList(),
                    c.cancelacion() == null ? null : new CancelacionRespuesta(c.cancelacion().cajero(),
                            c.cancelacion().fechaHora(), c.cancelacion().justificacion()), acciones);
        }
    }

    /** El mesero ve qué se descontó, sin los saldos del inventario. */
    record Consumo(String articulo, String unidadMedida, String cantidad, boolean insuficiente) {
    }

    record Servida(Comanda comanda, List<Consumo> consumo) {

        static Servida de(EntregaComandas.Servida s, EmpleadoPrincipal actor) {
            return new Servida(Comanda.de(s.comanda(), s.cuentaAbierta(), actor), s.consumo().stream()
                    .map(c -> new Consumo(c.articulo(), c.unidadMedida(), Decimales.cantidad(c.cantidad()),
                            c.insuficiente())).toList());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Cobro(OffsetDateTime fechaHora, String metodo, String total, String referenciaExterna,
            String estado) {

        static Cobro de(DatosServicio.Cobro c) {
            return new Cobro(c.fechaHora(), c.metodo(), Decimales.dinero(c.total()), c.referenciaExterna(), c.estado());
        }
    }

    public record Fondo(String id, String monto, String estado, String cajero, OffsetDateTime fechaHora) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Cuenta(String id, String mesaId, int mesa, String mesero, EstadoCuenta estado,
            OffsetDateTime fechaHoraApertura, OffsetDateTime fechaHoraCierre, int version, String total,
            List<Comanda> comandas, Cobro cobro, Fondo fondo, List<String> acciones) {

        public static Cuenta de(DatosServicio.Cuenta c, EmpleadoPrincipal actor) {
            List<String> acciones = new ArrayList<>();
            if (c.abierta() && c.meseroId().equals(actor.id())) {
                acciones.add("ENVIAR_COMANDA");
                if (c.cobrable()) {
                    acciones.add("COBRAR");
                }
                if (!c.tieneComandasVigentes() && !c.fondoPendiente()) {
                    acciones.add("CERRAR_SIN_COBRO");
                }
            }
            if (c.abierta() && actor.rol().equals("CAJERO")) {
                if (c.fondo() == null && c.tieneComandasVigentes() && c.total().signum() > 0) {
                    acciones.add("ENTREGAR_FONDO");
                } else if (c.fondoPendiente()) {
                    acciones.add("ANULAR_FONDO");
                }
            }
            return new Cuenta(c.id().toString(), c.mesaId().toString(), c.mesa(), c.mesero(), c.estado(),
                    c.fechaHoraApertura(), c.fechaHoraCierre(), c.version(), Decimales.dinero(c.total()),
                    c.comandas().stream().map(o -> Comanda.de(o, c.abierta(), actor)).toList(),
                    c.cobro() == null ? null : Cobro.de(c.cobro()),
                    c.fondo() == null ? null : new Fondo(c.fondo().id().toString(), Decimales.dinero(c.fondo().monto()),
                            c.fondo().estado(), c.fondo().cajero(), c.fondo().fechaHora()), acciones);
        }
    }
}
