package mx.sivant.catalogo.infraestructura;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mx.sivant.catalogo.aplicacion.Recetas;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class RecetasJdbc implements Recetas {

    private final JdbcClient sql;

    RecetasJdbc(JdbcClient sql) {
        this.sql = sql;
    }

    @Override
    public Map<Long, List<Componente>> porProducto(Collection<Long> productos) {
        Map<Long, List<Componente>> recetas = new HashMap<>();
        sql.sql("""
                SELECT id_producto_preparado, id_insumo, cantidad_requerida
                FROM sivant.receta_producto WHERE id_producto_preparado IN (:ids) ORDER BY id_receta_detalle
                """).param("ids", productos).query(fila -> {
            recetas.computeIfAbsent(fila.getLong(1), id -> new ArrayList<>())
                    .add(new Componente(fila.getLong(2), fila.getBigDecimal(3)));
        });
        return recetas;
    }
}
