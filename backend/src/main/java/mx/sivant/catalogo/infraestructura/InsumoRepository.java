package mx.sivant.catalogo.infraestructura;

import java.util.List;
import mx.sivant.catalogo.dominio.Insumo;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InsumoRepository extends JpaRepository<Insumo, Long> {

    List<Insumo> findByActivoTrueOrderByNombreAscIdAsc();
}
