package mx.sivant.empleados.infraestructura;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import mx.sivant.empleados.dominio.Empleado;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface EmpleadoRepository extends JpaRepository<Empleado, Long>, JpaSpecificationExecutor<Empleado> {

    @EntityGraph(attributePaths = "rol")
    Optional<Empleado> findByCorreo(String correo);

    @EntityGraph(attributePaths = "rol")
    Optional<Empleado> findConRolById(Long id);

    @Override
    @EntityGraph(attributePaths = "rol")
    Page<Empleado> findAll(Specification<Empleado> filtro, Pageable pagina);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Empleado e where e.id = :id")
    Optional<Empleado> bloquearPorId(Long id);

    /** Bloquea a los administradores activos, siempre por ID ascendente. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Empleado e where e.estado = ACTIVO and e.rol.id = :idRolAdministrador order by e.id")
    List<Empleado> bloquearAdministradoresActivos(Long idRolAdministrador);

    boolean existsByRolNombre(String nombre);

    boolean existsByCorreo(String correo);

    boolean existsByCorreoAndIdNot(String correo, Long id);
}
