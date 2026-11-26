package mx.sivant.empleados.infraestructura;

import java.util.Optional;
import mx.sivant.empleados.dominio.Rol;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RolRepository extends JpaRepository<Rol, Long> {

    Optional<Rol> findByNombre(String nombre);
}
