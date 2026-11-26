package mx.sivant.catalogo.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;
import mx.sivant.catalogo.aplicacion.AltaProducto;
import mx.sivant.catalogo.aplicacion.DatosProducto;
import mx.sivant.catalogo.dominio.Insumo;
import mx.sivant.catalogo.dominio.TipoProducto;
import mx.sivant.catalogo.dominio.UnidadMedida;
import mx.sivant.infraestructura.api.Decimales;

final class ProductoDtos {

    private static final String CON_CONTENIDO = "(?s).*\\S.*";
    private static final String ID = "^\\d{1,18}$";
    /** Igual que CANTIDAD, pero con algún dígito distinto de cero. */
    private static final String CANTIDAD_POSITIVA = "^(?=.*[1-9])\\d{1,12}(\\.\\d{1,6})?$";

    private ProductoDtos() {
    }

    record ComponenteAlta(
            @NotNull(message = "Selecciona un insumo.")
            @Pattern(regexp = ID, message = "Selecciona un insumo.") String insumoId,
            @NotNull(message = "Escribe la cantidad.")
            @Pattern(regexp = CANTIDAD_POSITIVA, message = "Escribe una cantidad mayor que cero.")
            String cantidadRequerida) {
    }

    record Alta(
            @NotBlank(message = "Escribe el nombre.")
            @Size(max = 120, message = "Usa 120 caracteres como máximo.") String nombre,
            @NotBlank(message = "Escribe la descripción.")
            @Size(max = 500, message = "Usa 500 caracteres como máximo.") String descripcion,
            @NotNull(message = "Escribe el precio.")
            @Pattern(regexp = Decimales.DINERO_POSITIVO, message = "Escribe un precio mayor que 0, con dos decimales como máximo.") String precioVenta,
            @NotNull(message = "Selecciona el tipo.") TipoProducto tipo,
            @NotNull(message = "Selecciona la unidad.") UnidadMedida unidadMedida,
            @Size(max = 100, message = "Usa 100 insumos como máximo.") List<@NotNull @Valid ComponenteAlta> receta,
            @Pattern(regexp = Decimales.CANTIDAD, message = "Escribe un nivel mínimo válido.") String nivelMinimo) {

        AltaProducto aplicacion() {
            return new AltaProducto(nombre, descripcion, new BigDecimal(precioVenta), tipo, unidadMedida,
                    receta == null ? null : receta.stream().map(c -> new AltaProducto.Componente(
                            Long.valueOf(c.insumoId()), new BigDecimal(c.cantidadRequerida()))).toList(),
                    nivelMinimo == null ? null : new BigDecimal(nivelMinimo));
        }
    }

    record Edicion(
            @Pattern(regexp = CON_CONTENIDO, message = "Escribe el nombre.")
            @Size(max = 120, message = "Usa 120 caracteres como máximo.") String nombre,
            @Pattern(regexp = CON_CONTENIDO, message = "Escribe la descripción.")
            @Size(max = 500, message = "Usa 500 caracteres como máximo.") String descripcion,
            @Pattern(regexp = Decimales.DINERO_POSITIVO, message = "Escribe un precio mayor que 0, con dos decimales como máximo.") String precioVenta) {

        boolean sinCambios() {
            return nombre == null && descripcion == null && precioVenta == null;
        }

        BigDecimal precio() {
            return precioVenta == null ? null : new BigDecimal(precioVenta);
        }
    }

    record Desactivacion(
            @NotNull(message = "Confirma la desactivación.")
            @AssertTrue(message = "Confirma la desactivación.") Boolean confirmado) {
    }

    record Reactivacion(
            @NotNull(message = "Confirma la reactivación.")
            @AssertTrue(message = "Confirma la reactivación.") Boolean confirmado) {
    }

    record Componente(String insumoId, String nombre, UnidadMedida unidadMedida, String cantidadRequerida) {
    }

    record Saldo(String id, String cantidadActual, String nivelMinimo) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Respuesta(String id, String nombre, String descripcion, String precioVenta, TipoProducto tipo,
            UnidadMedida unidadMedida, boolean activo, String imagen, List<Componente> receta, Saldo existencia) {

        static Respuesta de(DatosProducto p) {
            return new Respuesta(p.id().toString(), p.nombre(), p.descripcion(), Decimales.dinero(p.precioVenta()),
                    p.tipo(), p.unidadMedida(), p.activo(), urlImagen(p),
                    p.receta() == null ? null : p.receta().stream().map(c -> new Componente(c.insumoId().toString(),
                            c.nombre(), c.unidadMedida(), Decimales.cantidad(c.cantidadRequerida()))).toList(),
                    p.existencia() == null ? null : new Saldo(p.existencia().id().toString(),
                            Decimales.cantidad(p.existencia().cantidadActual()),
                            Decimales.cantidad(p.existencia().nivelMinimo())));
        }
    }

    /** La versión cambia con cada imagen nueva, así el navegador puede guardarla en caché sin mostrar una vieja. */
    private static String urlImagen(DatosProducto p) {
        if (p.imagen() == null) {
            return null;
        }
        String archivo = p.imagen().substring(p.imagen().lastIndexOf('/') + 1);
        return "/api/v1/productos/" + p.id() + "/imagen?v=" + archivo.substring(0, archivo.indexOf('.'));
    }

    record InsumoRespuesta(String id, String nombre, UnidadMedida unidadMedida) {

        static InsumoRespuesta de(Insumo i) {
            return new InsumoRespuesta(i.getId().toString(), i.getNombre(), i.getUnidadMedida());
        }
    }
}
