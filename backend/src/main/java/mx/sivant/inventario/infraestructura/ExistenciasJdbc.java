package mx.sivant.inventario.infraestructura;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.Optional;
import mx.sivant.inventario.aplicacion.Existencias;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
class ExistenciasJdbc implements Existencias {

    private final JdbcClient sql;
    private final Clock reloj;

    ExistenciasJdbc(JdbcClient sql, Clock reloj) {
        this.sql = sql;
        this.reloj = reloj;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Long crearParaReventa(Long idProducto, BigDecimal nivelMinimo) {
        Long id = crear(nivelMinimo);
        sql.sql("INSERT INTO sivant.existencia_producto (id_existencia, id_producto_reventa) VALUES (?, ?)")
                .param(id).param(idProducto).update();
        return id;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Long crearParaInsumo(Long idInsumo, BigDecimal nivelMinimo) {
        Long id = crear(nivelMinimo);
        sql.sql("INSERT INTO sivant.existencia_insumo (id_existencia, id_insumo) VALUES (?, ?)")
                .param(id).param(idInsumo).update();
        return id;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Saldo> deProducto(Long idProducto) {
        return sql.sql("""
                SELECT e.id_existencia, e.cantidad_actual, e.nivel_minimo
                FROM sivant.existencia_inventario e
                JOIN sivant.existencia_producto p USING (id_existencia)
                WHERE p.id_producto_reventa = ?
                """).param(idProducto)
                .query((f, n) -> new Saldo(f.getLong(1), f.getBigDecimal(2), f.getBigDecimal(3))).optional();
    }

    private Long crear(BigDecimal nivelMinimo) {
        return sql.sql("""
                INSERT INTO sivant.existencia_inventario (nivel_minimo, fecha_actualizacion)
                VALUES (?, ?) RETURNING id_existencia
                """).param(nivelMinimo).param(OffsetDateTime.now(reloj)).query(Long.class).single();
    }
}
