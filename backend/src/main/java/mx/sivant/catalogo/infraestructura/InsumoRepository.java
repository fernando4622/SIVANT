package mx.sivant.catalogo.infraestructura;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import mx.sivant.catalogo.dominio.Insumo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InsumoRepository extends JpaRepository<Insumo, Long> {

    List<Insumo> findByActivoTrueOrderByNombreAscIdAsc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from Insumo i where i.id = :id")
    Optional<Insumo> bloquearPorId(@Param("id") Long id);
}
