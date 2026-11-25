package mx.sivant.catalogo.infraestructura;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import mx.sivant.catalogo.aplicacion.CatalogoVenta;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class CatalogoVentaJdbc implements CatalogoVenta {

    private final JdbcClient sql;

    CatalogoVentaJdbc(JdbcClient sql) {
        this.sql = sql;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Map<Long, BigDecimal> preciosVigentes(Collection<Long> productos) {
        Map<Long, BigDecimal> precios = new HashMap<>();
        // Se bloquea por ID ascendente. El filtro se aplica al valor vigente tras esperar el bloqueo.
        sql.sql("""
                SELECT a.id_articulo, p.precio_venta, a.activo
                FROM sivant.articulo a JOIN sivant.producto p ON p.id_producto = a.id_articulo
                WHERE a.id_articulo IN (:ids) ORDER BY a.id_articulo FOR SHARE OF a, p
                """).param("ids", productos).query(fila -> {
            if (fila.getBoolean(3)) {
                precios.put(fila.getLong(1), fila.getBigDecimal(2));
            }
        });
        return precios;
    }
}
