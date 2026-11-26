package mx.sivant.inventario.aplicacion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import mx.sivant.catalogo.aplicacion.Recetas;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import mx.sivant.inventario.aplicacion.DatosInventario.Existencia;
import mx.sivant.inventario.dominio.TipoHistorial;
import mx.sivant.inventario.infraestructura.InventarioJdbc;
import mx.sivant.notificaciones.aplicacion.Avisos;
import mx.sivant.notificaciones.dominio.TipoNotificacion;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** RF24. Interfaz que inventario ofrece a servicio; forma parte de la transacción que marca la comanda servida. */
@Service
public class ConsumoInventario {

    private final InventarioJdbc inventario;
    private final Recetas recetas;
    private final Avisos avisos;

    public ConsumoInventario(InventarioJdbc inventario, Recetas recetas, Avisos avisos) {
        this.inventario = inventario;
        this.recetas = recetas;
        this.avisos = avisos;
    }

    public record Pedido(Long detalleId, Long productoId, int cantidad) {
    }

    /** `insuficiente` indica que lo registrado no alcanzaba y se generó una alerta. */
    public record Consumido(Long existenciaId, String articulo, String unidadMedida, BigDecimal cantidad,
            boolean insuficiente) {
    }

    private record Linea(Long detalle, BigDecimal cantidad) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public List<Consumido> consumir(Long actor, Long comanda, int mesa, List<Pedido> pedidos) {
        // Las existencias se bloquean por ID ascendente para no cruzarse con otro servicio.
        TreeMap<Long, List<Linea>> porExistencia = demanda(pedidos);
        Map<Long, Existencia> bloqueadas = new LinkedHashMap<>();
        for (Long id : porExistencia.keySet()) {
            bloqueadas.put(id, inventario.bloquearExistencia(id).orElseThrow());
        }
        List<Consumido> resultado = new ArrayList<>();
        porExistencia.forEach((id, lineas) ->
                resultado.add(aplicar(actor, comanda, mesa, bloqueadas.get(id), lineas)));
        return resultado;
    }

    private TreeMap<Long, List<Linea>> demanda(List<Pedido> pedidos) {
        Collection<Long> productos = pedidos.stream().map(Pedido::productoId).collect(Collectors.toSet());
        Map<Long, List<Recetas.Componente>> porReceta = recetas.porProducto(productos);
        Map<Long, Long> deInsumo = inventario.existenciasDeInsumos(porReceta.values().stream()
                .flatMap(List::stream).map(Recetas.Componente::insumoId).collect(Collectors.toSet()));
        Map<Long, Long> deReventa = inventario.existenciasDeProductos(productos);
        TreeMap<Long, List<Linea>> demanda = new TreeMap<>();
        for (Pedido pedido : pedidos) {
            BigDecimal unidades = BigDecimal.valueOf(pedido.cantidad());
            List<Recetas.Componente> receta = porReceta.get(pedido.productoId());
            if (receta == null) {
                demanda.computeIfAbsent(deReventa.get(pedido.productoId()), id -> new ArrayList<>())
                        .add(new Linea(pedido.detalleId(), unidades));
                continue;
            }
            for (Recetas.Componente componente : receta) {
                demanda.computeIfAbsent(deInsumo.get(componente.insumoId()), id -> new ArrayList<>())
                        .add(new Linea(pedido.detalleId(), componente.cantidad().multiply(unidades)));
            }
        }
        return demanda;
    }

    private Consumido aplicar(Long actor, Long comanda, int mesa, Existencia existencia, List<Linea> lineas) {
        BigDecimal previa = existencia.cantidadActual();
        BigDecimal total = lineas.stream().map(Linea::cantidad).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal saldo = previa;
        BigDecimal posterior = previa.subtract(total);
        if (posterior.abs().compareTo(GestionInventario.SALDO_MAXIMO) >= 0) {
            throw ReglaNegocioException.conflicto("EXISTENCIA_FUERA_DE_RANGO",
                    "La existencia de " + existencia.nombre() + " quedaría fuera de rango. Revisa su receta.");
        }
        for (Linea linea : lineas) {
            BigDecimal siguiente = saldo.subtract(linea.cantidad());
            inventario.insertarConsumo(existencia.id(), actor, linea.detalle(), saldo, siguiente);
            saldo = siguiente;
        }
        inventario.cambiarSaldo(existencia.id(), posterior);
        avisos.publicarEnTema(Avisos.TEMA_INVENTARIO, "EXISTENCIA_ACTUALIZADA", existencia.id(),
                existencia.version() + 1);

        boolean insuficiente = total.compareTo(previa) > 0;
        if (insuficiente) {
            alertar(comanda, mesa, existencia, total, previa);
        }
        return new Consumido(existencia.id(), existencia.nombre(), existencia.unidadMedida(), total, insuficiente);
    }

    private void alertar(Long comanda, int mesa, Existencia existencia, BigDecimal total, BigDecimal previa) {
        // Una existencia que ya era negativa no aporta nada a este servicio.
        BigDecimal faltante = total.subtract(previa.max(BigDecimal.ZERO)).stripTrailingZeros();
        avisos.notificarRolPorExistencia("ALMACENISTA", TipoNotificacion.ALERTA_INVENTARIO, existencia.id(),
                "Existencia insuficiente de " + existencia.nombre() + " al servir la comanda " + comanda + ".",
                Map.of("existenciaId", existencia.id().toString(), "comandaId", comanda.toString(), "mesa", mesa,
                        "faltante", faltante.toPlainString(), "unidadMedida", existencia.unidadMedida()));
        avisos.publicarEnTema(Avisos.TEMA_INVENTARIO, "ALERTA_INVENTARIO", existencia.id(), existencia.version() + 1);
    }
}
