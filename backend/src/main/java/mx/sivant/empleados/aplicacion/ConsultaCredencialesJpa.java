package mx.sivant.empleados.aplicacion;

import java.util.Optional;
import mx.sivant.empleados.dominio.Empleado;
import mx.sivant.empleados.infraestructura.EmpleadoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
class ConsultaCredencialesJpa implements ConsultaCredenciales {

    private final EmpleadoRepository empleados;

    ConsultaCredencialesJpa(EmpleadoRepository empleados) {
        this.empleados = empleados;
    }

    @Override
    public Optional<CredencialEmpleado> porCorreo(String correo) {
        return empleados.findByCorreo(Empleado.normalizarCorreo(correo)).map(ConsultaCredencialesJpa::credencial);
    }

    @Override
    public Optional<CredencialEmpleado> porId(Long id) {
        return empleados.findConRolById(id).map(ConsultaCredencialesJpa::credencial);
    }

    private static CredencialEmpleado credencial(Empleado e) {
        return new CredencialEmpleado(e.getId(), e.getNombreCompleto(), e.getRol().getNombre(), e.getContrasenaHash(),
                e.estaActivo(), e.getSesionVersion());
    }
}
