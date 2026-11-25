package mx.sivant.servicio.aplicacion;

import java.util.List;
import java.util.Map;
import mx.sivant.auditoria.aplicacion.RegistroAuditoria;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import mx.sivant.notificaciones.aplicacion.Avisos;
import mx.sivant.notificaciones.dominio.TipoNotificacion;
import mx.sivant.servicio.aplicacion.DatosServicio.EnCola;
import mx.sivant.servicio.dominio.EstadoComanda;
import mx.sivant.servicio.infraestructura.ServicioJdbc;
import mx.sivant.servicio.infraestructura.ServicioJdbc.ComandaBloqueada;
import mx.sivant.servicio.infraestructura.ServicioJdbc.CuentaBloqueada;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF19 a RF21. Interfaz que servicio ofrece a cocina. */
@Service
@PreAuthorize("hasAuthority('COCINERO')")
public class PreparacionComandas {

    private final ServicioJdbc servicio;
    private final Avisos avisos;
    private final RegistroAuditoria auditoria;

    public PreparacionComandas(ServicioJdbc servicio, Avisos avisos, RegistroAuditoria auditoria) {
        this.servicio = servicio;
        this.avisos = avisos;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    public List<EnCola> cola() {
        return servicio.cola();
    }

    @Transactional
    public EnCola iniciar(Long actor, Long id, int version) {
        return transitar(actor, id, version, EstadoComanda.EN_PREPARACION);
    }

    @Transactional
    public EnCola marcarLista(Long actor, Long id, int version) {
        return transitar(actor, id, version, EstadoComanda.LISTA);
    }

    private EnCola transitar(Long actor, Long id, int version, EstadoComanda nuevo) {
        Long idCuenta = servicio.cuentaDeComanda(id).orElseThrow(GestionCuentas::sinComanda);
        CuentaBloqueada cuenta = servicio.bloquearCuenta(idCuenta).orElseThrow(GestionCuentas::sinCuenta);
        ComandaBloqueada comanda = servicio.bloquearComanda(id);
        if (!comanda.estado().puedePasarA(nuevo)) {
            throw ReglaNegocioException.conflicto("TRANSICION_INVALIDA",
                    "La comanda cambió de estado. Revisa la cola actualizada.");
        }
        // Una versión anterior puede ser una edición del mesero que cocina aún no ve.
        GestionCuentas.exigirVersion(comanda.version(), version);
        servicio.transitar(comanda, nuevo, actor);
        auditoria.registrar(actor, "COMANDA_" + nuevo.name(), "COMANDA", id,
                Map.of("estadoAnterior", comanda.estado().name()));
        avisos.publicarEnTema(Avisos.TEMA_COCINA, "COMANDA_" + nuevo.name(), id, version + 1);
        if (nuevo == EstadoComanda.LISTA) {
            avisos.notificarEmpleadoPorComanda(cuenta.meseroId(), TipoNotificacion.COMANDA_LISTA, id,
                    "Mesa " + cuenta.mesa() + ", cuenta " + cuenta.id() + ": la comanda " + id + " está lista.",
                    GestionCuentas.contexto(cuenta, id));
            avisos.publicarAEmpleado(cuenta.meseroId(), "COMANDA_LISTA", id, version + 1);
        } else {
            avisos.publicarAEmpleado(cuenta.meseroId(), "COMANDA_ACTUALIZADA", id, version + 1);
        }
        return servicio.cola().stream().filter(c -> c.id().equals(id)).findFirst().orElseThrow();
    }
}
