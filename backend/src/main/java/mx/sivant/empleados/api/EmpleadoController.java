package mx.sivant.empleados.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.Map;
import mx.sivant.empleados.api.EmpleadoDtos.AltaEmpleado;
import mx.sivant.empleados.api.EmpleadoDtos.Desactivacion;
import mx.sivant.empleados.api.EmpleadoDtos.EdicionEmpleado;
import mx.sivant.empleados.api.EmpleadoDtos.EmpleadoRespuesta;
import mx.sivant.empleados.api.EmpleadoDtos.PaginaEmpleados;
import mx.sivant.empleados.aplicacion.GestionEmpleados;
import mx.sivant.empleados.dominio.EstadoEmpleado;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/empleados")
class EmpleadoController {

    private final GestionEmpleados gestion;

    EmpleadoController(GestionEmpleados gestion) {
        this.gestion = gestion;
    }

    @PostMapping
    ResponseEntity<EmpleadoRespuesta> registrar(@AuthenticationPrincipal EmpleadoPrincipal actor,
            @Valid @RequestBody AltaEmpleado alta) {
        var creado = EmpleadoRespuesta.de(gestion.registrar(actor.id(), alta.nombreCompleto(), alta.correo(),
                alta.contrasena(), alta.rol()));
        return ResponseEntity.created(URI.create("/api/v1/empleados/" + creado.id())).body(creado);
    }

    @GetMapping
    PaginaEmpleados consultar(@RequestParam(required = false) String q,
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String correo,
            @RequestParam(required = false) String rol,
            @RequestParam(required = false) EstadoEmpleado estado,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página inicia en 0.") int page,
            @RequestParam(defaultValue = "25") @Min(value = 1, message = "Usa un tamaño de 1 a 100.")
            @Max(value = 100, message = "Usa un tamaño de 1 a 100.") int size) {
        return PaginaEmpleados.de(gestion.consultar(q, nombre, correo, rol, estado, page, size));
    }

    @GetMapping("/{id}")
    EmpleadoRespuesta obtener(@PathVariable Long id) {
        return EmpleadoRespuesta.de(gestion.obtener(id));
    }

    @PatchMapping("/{id}")
    EmpleadoRespuesta editar(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @Valid @RequestBody EdicionEmpleado edicion) {
        if (edicion.sinCambios()) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS",
                    "Indica al menos un dato para modificar.", Map.of());
        }
        return EmpleadoRespuesta.de(gestion.editar(actor.id(), id, edicion.cambios()));
    }

    @PostMapping("/{id}/desactivacion")
    EmpleadoRespuesta desactivar(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @Valid @RequestBody Desactivacion desactivacion) {
        return EmpleadoRespuesta.de(gestion.desactivar(actor.id(), id));
    }
}
