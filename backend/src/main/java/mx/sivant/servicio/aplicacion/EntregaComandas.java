package mx.sivant.servicio.aplicacion;

import java.util.List;
import java.util.Map;
import mx.sivant.auditoria.aplicacion.RegistroAuditoria;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import mx.sivant.infraestructura.idempotencia.Idempotencia;
import mx.sivant.inventario.aplicacion.ConsumoInventario;
import mx.sivant.inventario.aplicacion.ConsumoInventario.Consumido;
import mx.sivant.inventario.aplicacion.ConsumoInventario.Pedido;
import mx.sivant.notificaciones.aplicacion.Avisos;
import mx.sivant.servicio.aplicacion.DatosServicio.Comanda;
import mx.sivant.servicio.dominio.EstadoComanda;
import mx.sivant.servicio.dominio.EstadoCuenta;
import mx.sivant.servicio.infraestructura.ServicioJdbc;
import mx.sivant.servicio.infraestructura.ServicioJdbc.ComandaBloqueada;
import mx.sivant.servicio.infraestructura.ServicioJdbc.CuentaBloqueada;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF23 y RF24. La transición, los movimientos y las alertas se confirman o se revierten juntos. */
@Service
public class EntregaComandas {

    private final ServicioJdbc servicio;
    private final ConsumoInventario consumo;
    private final Idempotencia idempotencia;
    private final Avisos avisos;
    private final RegistroAuditoria auditoria;

    public EntregaComandas(ServicioJdbc servicio, ConsumoInventario consumo, Idempotencia idempotencia, Avisos avisos,
            RegistroAuditoria auditoria) {
        this.servicio = servicio;
        this.consumo = consumo;
        this.idempotencia = idempotencia;
        this.avisos = avisos;
        this.auditoria = auditoria;
    }

    public record Servida(Comanda comanda, boolean cuentaAbierta, List<Consumido> consumo) {
    }

    @Transactional
    @PreAuthorize("hasAuthority('MESERO')")
    public Servida servir(EmpleadoPrincipal actor, String clave, Long id, int version) {
        return idempotencia.ejecutar(actor.id(), "SERVIR_COMANDA", id.toString(), clave, Map.of("version", version),
                Servida.class, () -> aplicar(actor, id, version));
    }

    private Servida aplicar(EmpleadoPrincipal actor, Long id, int version) {
        Long idCuenta = servicio.cuentaDeComanda(id).orElseThrow(GestionCuentas::sinComanda);
        CuentaBloqueada cuenta = servicio.bloquearCuenta(idCuenta).filter(c -> c.meseroId().equals(actor.id()))
                .orElseThrow(GestionCuentas::sinComanda);
        ComandaBloqueada comanda = servicio.bloquearComanda(id);
        if (!comanda.estado().puedePasarA(EstadoComanda.SERVIDA)) {
            throw ReglaNegocioException.conflicto("TRANSICION_INVALIDA",
                    "Solo se puede servir una comanda lista. Revisa su estado.");
        }
        GestionCuentas.exigirVersion(comanda.version(), version);
        servicio.transitar(comanda, EstadoComanda.SERVIDA, actor.id());
        Comanda servida = servicio.comanda(id);
        List<Consumido> consumido = consumo.consumir(actor.id(), id, cuenta.mesa(), servida.detalles().stream()
                .map(d -> new Pedido(d.id(), d.productoId(), d.cantidad())).toList());
        auditoria.registrar(actor.id(), "COMANDA_SERVIDA", "COMANDA", id, Map.of("consumo", consumido.stream()
                .map(c -> Map.of("existencia", c.existenciaId(), "cantidad", c.cantidad().toPlainString(),
                        "insuficiente", c.insuficiente())).toList()));
        avisos.publicarEnTema(Avisos.TEMA_COCINA, "COMANDA_SERVIDA", id, version + 1);
        avisos.publicarAEmpleado(actor.id(), "COMANDA_ACTUALIZADA", id, version + 1);
        return new Servida(servida, cuenta.estado() == EstadoCuenta.ABIERTA, consumido);
    }
}
