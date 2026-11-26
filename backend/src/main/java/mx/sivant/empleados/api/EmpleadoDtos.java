package mx.sivant.empleados.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import mx.sivant.empleados.aplicacion.CambiosEmpleado;
import mx.sivant.empleados.aplicacion.DatosEmpleado;
import org.springframework.data.domain.Page;

final class EmpleadoDtos {

    private static final String CORREO =
            "^\\s*[A-Za-z0-9._%+'-]{1,64}@(?:[A-Za-z0-9-]{1,63}\\.)+[A-Za-z0-9-]{2,63}\\s*$";
    private static final String CON_CONTENIDO = "(?s).*\\S.*";

    private EmpleadoDtos() {
    }

    record AltaEmpleado(
            @NotBlank(message = "Escribe el nombre completo.")
            @Size(max = 150, message = "Usa 150 caracteres como máximo.") String nombreCompleto,
            @NotBlank(message = "Escribe el correo.")
            @Size(max = 254, message = "Usa 254 caracteres como máximo.")
            @Pattern(regexp = CORREO, message = "Escribe un correo válido.") String correo,
            @NotBlank(message = "Escribe la contraseña inicial.")
            @Size(min = 8, max = 72, message = "Usa entre 8 y 72 caracteres.") String contrasena,
            @NotBlank(message = "Selecciona un cargo.") String rol) {

        @Override
        public String toString() {
            return "AltaEmpleado[...]";
        }
    }

    record EdicionEmpleado(
            @Pattern(regexp = CON_CONTENIDO, message = "Escribe el nombre completo.")
            @Size(max = 150, message = "Usa 150 caracteres como máximo.") String nombreCompleto,
            @Size(max = 254, message = "Usa 254 caracteres como máximo.")
            @Pattern(regexp = CORREO, message = "Escribe un correo válido.") String correo,
            @Pattern(regexp = CON_CONTENIDO, message = "Escribe la contraseña.")
            @Size(min = 8, max = 72, message = "Usa entre 8 y 72 caracteres.") String contrasena,
            @Pattern(regexp = CON_CONTENIDO, message = "Selecciona un cargo.") String rol) {

        boolean sinCambios() {
            return nombreCompleto == null && correo == null && contrasena == null && rol == null;
        }

        CambiosEmpleado cambios() {
            return new CambiosEmpleado(nombreCompleto, correo, contrasena, rol);
        }

        @Override
        public String toString() {
            return "EdicionEmpleado[...]";
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

    record EmpleadoRespuesta(String id, String nombreCompleto, String correo, String rol, String estado) {

        static EmpleadoRespuesta de(DatosEmpleado e) {
            return new EmpleadoRespuesta(e.id().toString(), e.nombreCompleto(), e.correo(), e.rol(), e.estado().name());
        }
    }

    record PaginaEmpleados(List<EmpleadoRespuesta> contenido, int page, int size, long totalElementos,
            int totalPaginas) {

        static PaginaEmpleados de(Page<DatosEmpleado> pagina) {
            return new PaginaEmpleados(pagina.map(EmpleadoRespuesta::de).getContent(), pagina.getNumber(),
                    pagina.getSize(), pagina.getTotalElements(), pagina.getTotalPages());
        }
    }
}
