package mx.sivant.empleados.aplicacion;

/** Interfaz que empleados ofrece a servicio. */
public interface EmpleadosActivos {

    /**
     * Comprueba que el empleado siga activo con ese rol y lo mantiene así hasta terminar la transacción: una
     * desactivación o un cambio de rol simultáneo espera.
     */
    boolean mantener(Long idEmpleado, String rol);
}
