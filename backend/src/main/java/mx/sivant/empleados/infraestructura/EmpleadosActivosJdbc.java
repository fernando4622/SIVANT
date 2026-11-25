package mx.sivant.empleados.infraestructura;

import mx.sivant.empleados.aplicacion.EmpleadosActivos;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class EmpleadosActivosJdbc implements EmpleadosActivos {

    private final JdbcClient sql;

    EmpleadosActivosJdbc(JdbcClient sql) {
        this.sql = sql;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean mantener(Long idEmpleado, String rol) {
        return sql.sql("""
                SELECT e.estado = 'ACTIVO' AND r.nombre = :rol
                FROM sivant.empleado e JOIN sivant.rol r USING (id_rol)
                WHERE e.id_empleado = :id FOR SHARE OF e
                """).param("rol", rol).param("id", idEmpleado).query(Boolean.class).optional().orElse(false);
    }
}
