package mx.sivant.empleados.aplicacion;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import mx.sivant.empleados.dominio.Empleado;
import mx.sivant.empleados.dominio.EstadoEmpleado;
import mx.sivant.empleados.dominio.Rol;
import mx.sivant.empleados.infraestructura.EmpleadoRepository;
import mx.sivant.empleados.infraestructura.FiltrosEmpleado;
import mx.sivant.empleados.infraestructura.RolRepository;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /** Registra responsable, operación y campos; nunca valores ni secretos. */
    private static final Logger AUDITORIA = LoggerFactory.getLogger("sivant.auditoria");
    private static final int BYTES_MAXIMOS_BCRYPT = 72;

    private final EmpleadoRepository empleados;
    private final RolRepository roles;
    private final PasswordEncoder codificador;

    public GestionEmpleados(EmpleadoRepository empleados, RolRepository roles, PasswordEncoder codificador) {
        this.empleados = empleados;
        this.roles = roles;
        this.codificador = codificador;
    }

    @Transactional
    public DatosEmpleado registrar(Long actor, String nombreCompleto, String correo, String contrasena, String rol) {
        Rol rolAsignado = resolverRol(rol);
        if (empleados.existsByCorreo(Empleado.normalizarCorreo(correo))) {
            throw correoDuplicado();
        }
        Empleado nuevo = guardar(new Empleado(nombreCompleto, correo, cifrar(contrasena), rolAsignado));
        AUDITORIA.info("accion=EMPLEADO_REGISTRADO actor={} empleado={}", actor, nuevo.getId());
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
        List<String> campos = new ArrayList<>();

        if (cambios.correo() != null) {
            String correo = Empleado.normalizarCorreo(cambios.correo());
            if (!correo.equals(empleado.getCorreo())) {
                if (empleados.existsByCorreoAndIdNot(correo, id)) {
                    throw correoDuplicado();
                }
                empleado.cambiarCorreo(correo);
                campos.add("correo");
            }
        }
        if (cambios.nombreCompleto() != null
                && !cambios.nombreCompleto().strip().equals(empleado.getNombreCompleto())) {
            empleado.cambiarNombre(cambios.nombreCompleto());
            campos.add("nombreCompleto");
        }
        if (nuevoRol != null && !nuevoRol.getNombre().equals(empleado.getRol().getNombre())) {
            conservarAdministrador(empleado, administradores);
            empleado.cambiarRol(nuevoRol);
            campos.add("rol");
        }
        if (cambios.contrasena() != null) {
            empleado.cambiarContrasena(cifrar(cambios.contrasena()));
            campos.add("contrasena");
        }
        guardar(empleado);
        if (!campos.isEmpty()) {
            AUDITORIA.info("accion=EMPLEADO_EDITADO actor={} empleado={} campos={}", actor, id, campos);
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
        empleado.desactivar();
        AUDITORIA.info("accion=EMPLEADO_DESACTIVADO actor={} empleado={}", actor, id);
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

    private Rol resolverRol(String nombre) {
        return roles.findByNombre(nombre)
                .orElseThrow(() -> ReglaNegocioException.invalido("rol", "El rol no existe."));
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
