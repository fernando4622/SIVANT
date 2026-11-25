package mx.sivant.catalogo.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.Map;
import mx.sivant.catalogo.api.ProductoDtos.Alta;
import mx.sivant.catalogo.api.ProductoDtos.Desactivacion;
import mx.sivant.catalogo.api.ProductoDtos.Edicion;
import mx.sivant.catalogo.api.ProductoDtos.InsumoRespuesta;
import mx.sivant.catalogo.api.ProductoDtos.Respuesta;
import mx.sivant.catalogo.aplicacion.GestionProductos;
import mx.sivant.catalogo.dominio.TipoProducto;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.infraestructura.api.Pagina;
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
@RequestMapping("/api/v1")
class ProductoController {

    private final GestionProductos gestion;

    ProductoController(GestionProductos gestion) {
        this.gestion = gestion;
    }

    @PostMapping("/productos")
    ResponseEntity<Respuesta> registrar(@AuthenticationPrincipal EmpleadoPrincipal actor,
            @Valid @RequestBody Alta alta) {
        var creado = Respuesta.de(gestion.registrar(actor.id(), alta.aplicacion()));
        return ResponseEntity.created(URI.create("/api/v1/productos/" + creado.id())).body(creado);
    }

    @GetMapping("/productos")
    Pagina<Respuesta> consultar(@AuthenticationPrincipal EmpleadoPrincipal actor,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) Boolean activo,
            @RequestParam(required = false) TipoProducto tipo,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "La página inicia en 0.") int page,
            @RequestParam(defaultValue = "25") @Min(value = 1, message = "Usa un tamaño de 1 a 100.")
            @Max(value = 100, message = "Usa un tamaño de 1 a 100.") int size) {
        Boolean filtroActivo = soloOperativo(actor) ? Boolean.TRUE : activo;
        return gestion.consultar(q, filtroActivo, tipo, page, size).con(Respuesta::de);
    }

    @GetMapping("/productos/{id}")
    Respuesta obtener(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id) {
        return Respuesta.de(gestion.obtener(id, soloOperativo(actor)));
    }

    @PatchMapping("/productos/{id}")
    Respuesta editar(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @Valid @RequestBody Edicion edicion) {
        if (edicion.sinCambios()) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "DATOS_INVALIDOS",
                    "Indica al menos un dato para modificar.", Map.of());
        }
        return Respuesta.de(gestion.editar(actor.id(), id, edicion.nombre(), edicion.descripcion(),
                edicion.precio()));
    }

    @PostMapping("/productos/{id}/desactivacion")
    Respuesta desactivar(@AuthenticationPrincipal EmpleadoPrincipal actor, @PathVariable Long id,
            @Valid @RequestBody Desactivacion desactivacion) {
        return Respuesta.de(gestion.desactivar(actor.id(), id));
    }

    @GetMapping("/insumos")
    List<InsumoRespuesta> insumos() {
        return gestion.insumosDisponibles().stream().map(InsumoRespuesta::de).toList();
    }

    private static boolean soloOperativo(EmpleadoPrincipal actor) {
        return "MESERO".equals(actor.rol());
    }
}
