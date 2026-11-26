package mx.sivant.empleados.aplicacion;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mx.sivant.auditoria.aplicacion.RegistroAuditoria;
import mx.sivant.empleados.dominio.Empleado;
import mx.sivant.empleados.dominio.EstadoEmpleado;
import mx.sivant.empleados.dominio.Rol;
import mx.sivant.empleados.infraestructura.EmpleadoRepository;
import mx.sivant.empleados.infraestructura.FiltrosEmpleado;
import mx.sivant.empleados.infraestructura.RolRepository;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import mx.sivant.servicio.aplicacion.CuentasDeEmpleado;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF11 a RF14. El permiso se comprueba en la ruta y otra vez aquí. */
@Service
@PreAuthorize("hasAuthority('ADMINISTRADOR')")
public class GestionEmpleados {

    private static final String ENTIDAD = "EMPLEADO";
    private static final int BYTES_MAXIMOS_BCRYPT = 72;

    private final EmpleadoRepository empleados;
    private final RolRepository roles;
    private final PasswordEncoder codificador;
    private final RegistroAuditoria auditoria;
    private final CuentasDeEmpleado cuentas;

    public GestionEmpleados(EmpleadoRepository empleados, RolRepository roles, PasswordEncoder codificador,
            RegistroAuditoria auditoria, CuentasDeEmpleado cuentas) {
        this.empleados = empleados;
        this.roles = roles;
        this.codificador = codificador;
        this.auditoria = auditoria;
        this.cuentas = cuentas;
    }

    @Transactional
    public DatosEmpleado registrar(Long actor, String nombreCompleto, String correo, String contrasena, String rol) {
        Rol rolAsignado = resolverRol(rol);
        if (empleados.existsByCorreo(Empleado.normalizarCorreo(correo))) {
            throw correoDuplicado();
        }
        Empleado nuevo = guardar(new Empleado(nombreCompleto, correo, cifrar(contrasena), rolAsignado));
        auditoria.registrar(actor, "EMPLEADO_REGISTRADO", ENTIDAD, nuevo.getId(), Map.of(
                "nombreCompleto", nuevo.getNombreCompleto(), "correo", nuevo.getCorreo(),
                "rol", rolAsignado.getNombre()));
        return DatosEmpleado.de(nuevo);
    }

    @Transactional(readOnly = true)
    public Page<DatosEmpleado> consultar(String q, String nombre, String correo, String rol, EstadoEmpleado estado,
            int pagina, int tamano) {
        if (rol != null && !rol.isBlank()) {
            resolverRol(rol);
        }
        Sort orden = Sort.by("nombreCompleto").and(Sort.by("id"));
        return empleados.findAll(FiltrosEmpleado.con(q, nombre, correo, rol, estado),
                PageRequest.of(pagina, tamano, orden)).map(DatosEmpleado::de);
    }

    @Transactional(readOnly = true)
    public DatosEmpleado obtener(Long id) {
        return empleados.findConRolById(id).map(DatosEmpleado::de).orElseThrow(GestionEmpleados::noExiste);
    }

    @Transactional
    public DatosEmpleado editar(Long actor, Long id, CambiosEmpleado cambios) {
        Rol nuevoRol = cambios.rol() == null ? null : resolverRol(cambios.rol());
        List<Empleado> administradores = nuevoRol == null ? List.of() : bloquearAdministradores();
        Empleado empleado = empleados.bloquearPorId(id).orElseThrow(GestionEmpleados::noExiste);
        Map<String, Object> campos = new LinkedHashMap<>();

        if (cambios.correo() != null) {
            String correo = Empleado.normalizarCorreo(cambios.correo());
            if (!correo.equals(empleado.getCorreo())) {
                if (empleados.existsByCorreoAndIdNot(correo, id)) {
                    throw correoDuplicado();
                }
                campos.put("correo", cambio(empleado.getCorreo(), correo));
                empleado.cambiarCorreo(correo);
            }
        }
        if (cambios.nombreCompleto() != null
                && !cambios.nombreCompleto().strip().equals(empleado.getNombreCompleto())) {
            String anterior = empleado.getNombreCompleto();
            empleado.cambiarNombre(cambios.nombreCompleto());
            campos.put("nombreCompleto", cambio(anterior, empleado.getNombreCompleto()));
        }
        if (nuevoRol != null && !nuevoRol.getNombre().equals(empleado.getRol().getNombre())) {
            conservarAdministrador(empleado, administradores);
            exigirSinCuentas(id);
            campos.put("rol", cambio(empleado.getRol().getNombre(), nuevoRol.getNombre()));
            empleado.cambiarRol(nuevoRol);
        }
        if (cambios.contrasena() != null) {
            empleado.cambiarContrasena(cifrar(cambios.contrasena()));
            // Se audita el hecho, nunca el valor.
            campos.put("contrasena", "MODIFICADA");
        }
        guardar(empleado);
        if (!campos.isEmpty()) {
            auditoria.registrar(actor, "EMPLEADO_EDITADO", ENTIDAD, id, campos);
        }
        return DatosEmpleado.de(empleado);
    }

    @Transactional
    public DatosEmpleado desactivar(Long actor, Long id) {
        List<Empleado> administradores = bloquearAdministradores();
        Empleado empleado = empleados.bloquearPorId(id).orElseThrow(GestionEmpleados::noExiste);
        if (!empleado.estaActivo()) {
            throw ReglaNegocioException.conflicto("EMPLEADO_INACTIVO", "El empleado ya está desactivado.");
        }
        conservarAdministrador(empleado, administradores);
        exigirSinCuentas(id);
        empleado.desactivar();
        auditoria.registrar(actor, "EMPLEADO_DESACTIVADO", ENTIDAD, id, Map.of(
                "estado", cambio(EstadoEmpleado.ACTIVO.name(), EstadoEmpleado.INACTIVO.name())));
        return DatosEmpleado.de(empleado);
    }

    @Transactional
    public DatosEmpleado reactivar(Long actor, Long id) {
        Empleado empleado = empleados.bloquearPorId(id).orElseThrow(GestionEmpleados::noExiste);
        if (empleado.estaActivo()) {
            throw ReglaNegocioException.conflicto("EMPLEADO_ACTIVO", "El empleado ya está activo.");
        }
        empleado.reactivar();
        auditoria.registrar(actor, "EMPLEADO_REACTIVADO", ENTIDAD, id, Map.of(
                "estado", cambio(EstadoEmpleado.INACTIVO.name(), EstadoEmpleado.ACTIVO.name())));
        return DatosEmpleado.de(empleado);
    }

    private List<Empleado> bloquearAdministradores() {
        return empleados.bloquearAdministradoresActivos(resolverRol(Rol.ADMINISTRADOR).getId());
    }

    /** Evita que el sistema quede sin nadie que pueda gestionar empleados. */
    private static void conservarAdministrador(Empleado empleado, List<Empleado> administradores) {
        if (empleado.esAdministradorActivo() && administradores.size() == 1) {
            throw ReglaNegocioException.conflicto("ULTIMO_ADMINISTRADOR",
                    "Debe permanecer al menos un administrador activo.");
        }
    }

    /** Quien abrió una cuenta es su responsable histórico: primero debe cerrarla. */
    private void exigirSinCuentas(Long id) {
        int abiertas = cuentas.abiertas(id);
        if (abiertas > 0) {
            throw ReglaNegocioException.conflicto("CUENTAS_ABIERTAS", abiertas == 1
                    ? "El empleado tiene una cuenta abierta. Debe cerrarse antes."
                    : "El empleado tiene " + abiertas + " cuentas abiertas. Deben cerrarse antes.");
        }
    }

    private Empleado guardar(Empleado empleado) {
        try {
            return empleados.saveAndFlush(empleado);
        } catch (DataIntegrityViolationException e) {
            // Dos altas simultáneas pasan la consulta previa; decide el índice único.
            if (String.valueOf(e.getMostSpecificCause().getMessage()).contains("uq_empleado_correo")) {
                throw correoDuplicado();
            }
            throw e;
        }
    }

    private static Map<String, String> cambio(String anterior, String nuevo) {
        return Map.of("anterior", anterior, "nuevo", nuevo);
    }

    private Rol resolverRol(String nombre) {
        return roles.findByNombre(nombre)
                .orElseThrow(() -> ReglaNegocioException.invalido("rol", "El cargo no existe."));
    }

    private String cifrar(String contrasena) {
        if (contrasena.getBytes(StandardCharsets.UTF_8).length > BYTES_MAXIMOS_BCRYPT) {
            throw ReglaNegocioException.invalido("contrasena", "La contraseña es demasiado larga.");
        }
        return codificador.encode(contrasena);
    }

    private static ReglaNegocioException correoDuplicado() {
        return ReglaNegocioException.conflicto("CORREO_DUPLICADO", "El correo ya pertenece a otro empleado.", "correo");
    }

    private static ReglaNegocioException noExiste() {
        return ReglaNegocioException.noEncontrado("El empleado no existe.");
    }
}
