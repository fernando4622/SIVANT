package mx.sivant.catalogo.aplicacion;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import mx.sivant.auditoria.aplicacion.RegistroAuditoria;
import mx.sivant.catalogo.dominio.Insumo;
import mx.sivant.catalogo.dominio.Producto;
import mx.sivant.catalogo.dominio.ProductoPreparado;
import mx.sivant.catalogo.dominio.ProductoReventa;
import mx.sivant.catalogo.dominio.TipoProducto;
import mx.sivant.catalogo.infraestructura.AlmacenImagenes;
import mx.sivant.catalogo.infraestructura.InsumoRepository;
import mx.sivant.catalogo.infraestructura.ProductoRepository;
import mx.sivant.infraestructura.api.Pagina;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import mx.sivant.inventario.aplicacion.Existencias;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** RF07 a RF10. */
@Service
public class GestionProductos {

    private static final String ENTIDAD = "PRODUCTO";
    private static final char ESCAPE = '\\';

    private final ProductoRepository productos;
    private final InsumoRepository insumos;
    private final Existencias existencias;
    private final RegistroAuditoria auditoria;
    private final AlmacenImagenes almacen;

    public GestionProductos(ProductoRepository productos, InsumoRepository insumos, Existencias existencias,
            RegistroAuditoria auditoria, AlmacenImagenes almacen) {
        this.productos = productos;
        this.insumos = insumos;
        this.existencias = existencias;
        this.auditoria = auditoria;
        this.almacen = almacen;
    }

    /** Imagen guardada en el almacén, con su tipo de contenido. */
    public record ArchivoImagen(Path archivo, String tipoContenido) {
    }

    public record InsumoRegistrado(Insumo insumo, Long existenciaId) {
    }

    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public InsumoRegistrado registrarInsumo(Long actor, String nombre, mx.sivant.catalogo.dominio.UnidadMedida unidad,
            BigDecimal nivelMinimo) {
        Insumo insumo = insumos.saveAndFlush(new Insumo(nombre, unidad));
        Long existencia = existencias.crearParaInsumo(insumo.getId(), nivelMinimo);
        auditoria.registrar(actor, "INSUMO_REGISTRADO", "INSUMO", insumo.getId(),
                Map.of("nombre", insumo.getNombre(), "unidadMedida", unidad.name(),
                        "nivelMinimo", nivelMinimo.toPlainString()));
        return new InsumoRegistrado(insumo, existencia);
    }

    /**
     * Sustituye la imagen. El archivo nuevo se escribe antes de guardar su ruta: si la transacción falla se borra, y
     * el anterior se borra solo después de confirmar.
     */
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public DatosProducto cambiarImagen(Long actor, Long id, byte[] contenido) {
        String extension = FormatoImagen.extension(contenido);
        Producto producto = productos.bloquearPorId(id).orElseThrow(GestionProductos::noExiste);
        String anterior = producto.getImagen();
        String nueva = almacen.guardar(contenido, extension);
        alTerminar(nueva, anterior);
        producto.cambiarImagen(nueva);
        auditoria.registrar(actor, "PRODUCTO_IMAGEN_CAMBIADA", ENTIDAD, id,
                Map.of("imagen", cambio(anterior == null ? "" : anterior, nueva)));
        return detalle(producto);
    }

    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public DatosProducto quitarImagen(Long actor, Long id) {
        Producto producto = productos.bloquearPorId(id).orElseThrow(GestionProductos::noExiste);
        String anterior = producto.getImagen();
        if (anterior == null) {
            throw ReglaNegocioException.conflicto("SIN_IMAGEN", "El producto no tiene imagen.");
        }
        alTerminar(null, anterior);
        producto.cambiarImagen(null);
        auditoria.registrar(actor, "PRODUCTO_IMAGEN_QUITADA", ENTIDAD, id, Map.of("imagen", cambio(anterior, "")));
        return detalle(producto);
    }

    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public Insumo cambiarImagenInsumo(Long actor, Long id, byte[] contenido) {
        String extension = FormatoImagen.extension(contenido);
        Insumo insumo = insumos.bloquearPorId(id).orElseThrow(GestionProductos::noExisteInsumo);
        String anterior = insumo.getImagen();
        String nueva = almacen.guardarEn("insumos", contenido, extension);
        alTerminar(nueva, anterior);
        insumo.cambiarImagen(nueva);
        auditoria.registrar(actor, "INSUMO_IMAGEN_CAMBIADA", "INSUMO", id,
                Map.of("imagen", cambio(anterior == null ? "" : anterior, nueva)));
        return insumo;
    }

    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public Insumo quitarImagenInsumo(Long actor, Long id) {
        Insumo insumo = insumos.bloquearPorId(id).orElseThrow(GestionProductos::noExisteInsumo);
        String anterior = insumo.getImagen();
        if (anterior == null) {
            throw ReglaNegocioException.conflicto("SIN_IMAGEN", "El insumo no tiene imagen.");
        }
        alTerminar(null, anterior);
        insumo.cambiarImagen(null);
        auditoria.registrar(actor, "INSUMO_IMAGEN_QUITADA", "INSUMO", id,
                Map.of("imagen", cambio(anterior, "")));
        return insumo;
    }

    /** El mesero solo ve imágenes de productos activos. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR', 'MESERO', 'ALMACENISTA')")
    public ArchivoImagen imagen(Long id, boolean soloOperativo) {
        Producto producto = productos.findById(id).filter(p -> !soloOperativo || p.isActivo())
                .filter(p -> p.getImagen() != null).orElseThrow(GestionProductos::sinImagen);
        Path archivo = almacen.archivo(producto.getImagen());
        if (!Files.isRegularFile(archivo)) {
            throw sinImagen();
        }
        return new ArchivoImagen(archivo, FormatoImagen.tipoContenido(producto.getImagen()));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR', 'ALMACENISTA')")
    public ArchivoImagen imagenInsumo(Long id) {
        Insumo insumo = insumos.findById(id).filter(i -> i.getImagen() != null)
                .orElseThrow(GestionProductos::sinImagenInsumo);
        Path archivo = almacen.archivo(insumo.getImagen());
        if (!Files.isRegularFile(archivo)) {
            throw sinImagenInsumo();
        }
        return new ArchivoImagen(archivo, FormatoImagen.tipoContenido(insumo.getImagen()));
    }

    private void alTerminar(String nueva, String anterior) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int estado) {
                if (estado == STATUS_COMMITTED) {
                    if (anterior != null) {
                        almacen.borrar(anterior);
                    }
                } else if (nueva != null) {
                    almacen.borrar(nueva);
                }
            }
        });
    }

    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public DatosProducto registrar(Long actor, AltaProducto alta) {
        validarTipo(alta);
        Producto nuevo = alta.tipo() == TipoProducto.PREPARADO ? preparado(alta) : reventa(alta);
        auditoria.registrar(actor, "PRODUCTO_REGISTRADO", ENTIDAD, nuevo.getId(), Map.of("nombre", nuevo.getNombre(),
                "tipo", nuevo.getTipo().name(), "precioVenta", nuevo.getPrecioVenta().toPlainString()));
        return detalle(nuevo);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR', 'MESERO')")
    public Pagina<DatosProducto> consultar(String q, Boolean activo, TipoProducto tipo, int pagina, int tamano) {
        Specification<Producto> filtro = Specification.unrestricted();
        if (q != null && !q.isBlank()) {
            String patron = "%" + q.strip().toLowerCase(Locale.ROOT)
                    .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            filtro = filtro.and((raiz, consulta, cb) -> cb.like(cb.lower(raiz.get("nombre")), patron, ESCAPE));
        }
        if (activo != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("activo"), activo));
        }
        if (tipo != null) {
            Class<? extends Producto> clase =
                    tipo == TipoProducto.PREPARADO ? ProductoPreparado.class : ProductoReventa.class;
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.type(), clase));
        }
        Page<Producto> resultado = productos.findAll(filtro,
                PageRequest.of(pagina, tamano, Sort.by("nombre").and(Sort.by("id"))));
        return Pagina.de(resultado.map(DatosProducto::resumen).getContent(), pagina, tamano,
                resultado.getTotalElements());
    }

    /** El mesero consulta solo el catálogo activo, sin receta ni existencia. */
    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('ADMINISTRADOR', 'MESERO')")
    public DatosProducto obtener(Long id, boolean soloOperativo) {
        Producto producto = productos.findById(id).filter(p -> !soloOperativo || p.isActivo())
                .orElseThrow(GestionProductos::noExiste);
        return soloOperativo ? DatosProducto.resumen(producto) : detalle(producto);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public List<Insumo> insumosDisponibles() {
        return insumos.findByActivoTrueOrderByNombreAscIdAsc();
    }

    /** El precio aplicado se guarda en cada detalle de comanda, así que este cambio no afecta lo ya registrado. */
    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public DatosProducto editar(Long actor, Long id, String nombre, String descripcion, BigDecimal precio) {
        return editar(actor, id, nombre, descripcion, precio, null);
    }

    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public DatosProducto editar(Long actor, Long id, String nombre, String descripcion, BigDecimal precio,
            List<AltaProducto.Componente> receta) {
        Producto producto = productos.bloquearPorId(id).orElseThrow(GestionProductos::noExiste);
        Map<String, Object> cambios = new LinkedHashMap<>();
        if (nombre != null && !nombre.strip().equals(producto.getNombre())) {
            cambios.put("nombre", cambio(producto.getNombre(), nombre.strip()));
            producto.cambiarNombre(nombre);
        }
        if (descripcion != null && !descripcion.strip().equals(producto.getDescripcion())) {
            cambios.put("descripcion", cambio(producto.getDescripcion(), descripcion.strip()));
            producto.cambiarDescripcion(descripcion);
        }
        if (precio != null && precio.compareTo(producto.getPrecioVenta()) != 0) {
            cambios.put("precioVenta", cambio(producto.getPrecioVenta().toPlainString(), precio.toPlainString()));
            producto.cambiarPrecio(precio);
        }
        if (receta != null) {
            if (!(producto instanceof ProductoPreparado preparado)) {
                throw ReglaNegocioException.invalido("receta", "Un producto de reventa no lleva receta.");
            }
            List<ProductoPreparado.ComponenteReceta> componentes = componentes(receta);
            preparado.reemplazarReceta(List.of());
            productos.flush();
            preparado.reemplazarReceta(componentes);
            cambios.put("receta", Map.of("anterior", "receta anterior", "nuevo", receta.size() + " insumo(s)"));
        }
        if (!cambios.isEmpty()) {
            auditoria.registrar(actor, "PRODUCTO_EDITADO", ENTIDAD, id, cambios);
        }
        return detalle(producto);
    }

    private List<ProductoPreparado.ComponenteReceta> componentes(List<AltaProducto.Componente> receta) {
        Set<Long> usados = new HashSet<>();
        return receta.stream().map(componente -> {
            if (!usados.add(componente.insumoId())) {
                throw ReglaNegocioException.invalido("receta", "No repitas insumos en la receta.");
            }
            Insumo insumo = insumos.findById(componente.insumoId()).filter(Insumo::isActivo)
                    .orElseThrow(() -> ReglaNegocioException.invalido("receta", "Un insumo no está disponible."));
            return new ProductoPreparado.ComponenteReceta(insumo, componente.cantidadRequerida());
        }).toList();
    }

    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public DatosProducto desactivar(Long actor, Long id) {
        Producto producto = productos.bloquearPorId(id).orElseThrow(GestionProductos::noExiste);
        if (!producto.isActivo()) {
            throw ReglaNegocioException.conflicto("PRODUCTO_INACTIVO", "El producto ya está desactivado.");
        }
        producto.desactivar();
        auditoria.registrar(actor, "PRODUCTO_DESACTIVADO", ENTIDAD, id, Map.of("activo", cambio("true", "false")));
        return detalle(producto);
    }

    @Transactional
    @PreAuthorize("hasAuthority('ADMINISTRADOR')")
    public DatosProducto reactivar(Long actor, Long id) {
        Producto producto = productos.bloquearPorId(id).orElseThrow(GestionProductos::noExiste);
        if (producto.isActivo()) {
            throw ReglaNegocioException.conflicto("PRODUCTO_ACTIVO", "El producto ya está activo.");
        }
        producto.reactivar();
        auditoria.registrar(actor, "PRODUCTO_REACTIVADO", ENTIDAD, id, Map.of("activo", cambio("false", "true")));
        return detalle(producto);
    }

    private static void validarTipo(AltaProducto alta) {
        boolean conReceta = alta.receta() != null && !alta.receta().isEmpty();
        if (alta.tipo() == TipoProducto.PREPARADO) {
            if (!conReceta) {
                throw ReglaNegocioException.invalido("receta", "Agrega al menos un insumo.");
            }
            if (alta.nivelMinimo() != null) {
                throw ReglaNegocioException.invalido("nivelMinimo", "Un preparado no tiene existencia propia.");
            }
        } else {
            if (conReceta) {
                throw ReglaNegocioException.invalido("receta", "Un producto de reventa no lleva receta.");
            }
            if (alta.nivelMinimo() == null) {
                throw ReglaNegocioException.invalido("nivelMinimo", "Escribe el nivel mínimo.");
            }
        }
    }

    private Producto preparado(AltaProducto alta) {
        var producto = new ProductoPreparado(alta.nombre(), alta.descripcion(), alta.precioVenta(),
                alta.unidadMedida());
        Set<Long> usados = new HashSet<>();
        for (AltaProducto.Componente componente : alta.receta()) {
            if (!usados.add(componente.insumoId())) {
                throw ReglaNegocioException.invalido("receta", "No repitas insumos en la receta.");
            }
            Insumo insumo = insumos.findById(componente.insumoId()).filter(Insumo::isActivo)
                    .orElseThrow(() -> ReglaNegocioException.invalido("receta", "Un insumo no está disponible."));
            producto.agregarInsumo(insumo, componente.cantidadRequerida());
        }
        return productos.saveAndFlush(producto);
    }

    private Producto reventa(AltaProducto alta) {
        Producto producto = productos.saveAndFlush(new ProductoReventa(alta.nombre(), alta.descripcion(),
                alta.precioVenta(), alta.unidadMedida()));
        existencias.crearParaReventa(producto.getId(), alta.nivelMinimo());
        return producto;
    }

    private DatosProducto detalle(Producto producto) {
        if (producto instanceof ProductoPreparado preparado) {
            return DatosProducto.detalle(producto, preparado.getReceta().stream()
                    .map(r -> new DatosProducto.Componente(r.getInsumo().getId(), r.getInsumo().getNombre(),
                            r.getInsumo().getUnidadMedida(), r.getCantidadRequerida())).toList(), null);
        }
        return DatosProducto.detalle(producto, null, existencias.deProducto(producto.getId()).orElse(null));
    }

    private static Map<String, String> cambio(String anterior, String nuevo) {
        return Map.of("anterior", anterior, "nuevo", nuevo);
    }

    private static ReglaNegocioException sinImagen() {
        return ReglaNegocioException.noEncontrado("El producto no tiene imagen.");
    }

    private static ReglaNegocioException noExisteInsumo() {
        return ReglaNegocioException.noEncontrado("El insumo no existe.");
    }

    private static ReglaNegocioException sinImagenInsumo() {
        return ReglaNegocioException.noEncontrado("El insumo no tiene imagen.");
    }

    private static ReglaNegocioException noExiste() {
        return ReglaNegocioException.noEncontrado("El producto no existe.");
    }
}
