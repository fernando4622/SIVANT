package mx.sivant.empleados.aplicacion;

import mx.sivant.empleados.dominio.Empleado;
import mx.sivant.empleados.dominio.EstadoEmpleado;

public record DatosEmpleado(Long id, String nombreCompleto, String correo, String rol, EstadoEmpleado estado) {

    static DatosEmpleado de(Empleado e) {
        return new DatosEmpleado(e.getId(), e.getNombreCompleto(), e.getCorreo(), e.getRol().getNombre(),
                e.getEstado());
    }
}
