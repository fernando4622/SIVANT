package mx.sivant.infraestructura.arranque;

import java.math.BigDecimal;
import java.util.Map;
import mx.sivant.catalogo.dominio.Insumo;
import mx.sivant.catalogo.dominio.Producto;
import mx.sivant.catalogo.dominio.ProductoPreparado;
import mx.sivant.catalogo.dominio.ProductoReventa;
import mx.sivant.catalogo.dominio.UnidadMedida;
import mx.sivant.catalogo.infraestructura.InsumoRepository;
import mx.sivant.catalogo.infraestructura.ProductoRepository;
import mx.sivant.inventario.aplicacion.Existencias;
import mx.sivant.inventario.dominio.TipoHistorial;
import mx.sivant.inventario.infraestructura.InventarioJdbc;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Mesas, insumos y productos ficticios. Existe únicamente en el perfil dev. */
@Component
@Profile("dev")
class CatalogoDesarrollo {

    private final InsumoRepository insumos;
    private final ProductoRepository productos;
    private final Existencias existencias;
    private final InventarioJdbc inventario;
    private final JdbcClient sql;

    CatalogoDesarrollo(InsumoRepository insumos, ProductoRepository productos, Existencias existencias,
            InventarioJdbc inventario, JdbcClient sql) {
        this.insumos = insumos;
        this.productos = productos;
        this.existencias = existencias;
        this.inventario = inventario;
        this.sql = sql;
    }

    @Transactional
    public void crearSiFalta(Long almacenista) {
        sql.sql("INSERT INTO sivant.mesa (numero) SELECT n FROM generate_series(1, 10) AS n ON CONFLICT DO NOTHING").update();
        if (insumos.count() > 0 || productos.count() > 0) {
            return;
        }
        Insumo cafe = insumo("Café en grano", UnidadMedida.GRAMO, "1000", "5000", almacenista);
        Insumo leche = insumo("Leche", UnidadMedida.MILILITRO, "4000", "12000", almacenista);
        Insumo azucar = insumo("Azúcar", UnidadMedida.GRAMO, "1000", "800", almacenista);
        Insumo te = insumo("Té negro", UnidadMedida.GRAMO, "200", "600", almacenista);
        Insumo queso = insumo("Queso crema", UnidadMedida.GRAMO, "1000", "3000", almacenista);

        preparado("Café Americano", "Café de grano recién molido.", "40.00", Map.of(cafe, "18"));
        preparado("Capuchino", "Café espresso con leche espumada.", "55.00", Map.of(cafe, "18", leche, "180"));
        preparado("Té Helado", "Té negro frío con azúcar.", "45.00", Map.of(te, "5", azucar, "15"));
        preparado("Cheesecake", "Delicioso cheesecake con frutos rojos.", "75.00",
                Map.of(queso, "120", azucar, "30"));
        reventa("Agua embotellada", "Botella de 600 ml.", "20.00", "12", "48", almacenista);
        reventa("Brownie", "Brownie de chocolate empacado.", "70.00", "5", "4", almacenista);
    }

    private Insumo insumo(String nombre, UnidadMedida unidad, String minimo, String cantidad, Long almacenista) {
        Insumo insumo = insumos.saveAndFlush(new Insumo(nombre, unidad));
        surtir(existencias.crearParaInsumo(insumo.getId(), new BigDecimal(minimo)), cantidad, almacenista);
        return insumo;
    }

    private void preparado(String nombre, String descripcion, String precio, Map<Insumo, String> receta) {
        var producto = new ProductoPreparado(nombre, descripcion, new BigDecimal(precio), UnidadMedida.PIEZA);
        receta.forEach((insumo, cantidad) -> producto.agregarInsumo(insumo, new BigDecimal(cantidad)));
        productos.saveAndFlush(producto);
    }

    private void reventa(String nombre, String descripcion, String precio, String minimo, String cantidad,
            Long almacenista) {
        Producto producto = productos.saveAndFlush(
                new ProductoReventa(nombre, descripcion, new BigDecimal(precio), UnidadMedida.PIEZA));
        surtir(existencias.crearParaReventa(producto.getId(), new BigDecimal(minimo)), cantidad, almacenista);
    }

    private void surtir(Long existencia, String cantidad, Long almacenista) {
        BigDecimal saldo = new BigDecimal(cantidad);
        inventario.cambiarSaldo(existencia, saldo);
        inventario.insertarMovimiento(existencia, almacenista, null, TipoHistorial.ENTRADA_MANUAL, BigDecimal.ZERO, saldo,
                "Datos ficticios de desarrollo");
    }
}
