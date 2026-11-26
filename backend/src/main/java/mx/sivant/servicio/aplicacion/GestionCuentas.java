package mx.sivant.servicio.aplicacion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import mx.sivant.auditoria.aplicacion.RegistroAuditoria;
import mx.sivant.catalogo.aplicacion.CatalogoVenta;
import mx.sivant.empleados.aplicacion.EmpleadosActivos;
import mx.sivant.identidad.aplicacion.EmpleadoPrincipal;
import mx.sivant.infraestructura.api.Pagina;
import mx.sivant.infraestructura.errores.ReglaNegocioException;
import mx.sivant.infraestructura.idempotencia.Idempotencia;
import mx.sivant.notificaciones.aplicacion.Avisos;
import mx.sivant.notificaciones.dominio.TipoNotificacion;
import mx.sivant.servicio.aplicacion.DatosServicio.Comanda;
import mx.sivant.servicio.aplicacion.DatosServicio.Cuenta;
import mx.sivant.servicio.aplicacion.DatosServicio.Detalle;
import mx.sivant.servicio.aplicacion.DatosServicio.LineaEditada;
import mx.sivant.servicio.aplicacion.DatosServicio.LineaNueva;
import mx.sivant.servicio.aplicacion.DatosServicio.Mesa;
import mx.sivant.servicio.aplicacion.DatosServicio.ResumenCuenta;
import mx.sivant.servicio.dominio.EstadoComanda;
import mx.sivant.servicio.dominio.EstadoCuenta;
import mx.sivant.servicio.infraestructura.ServicioJdbc;
import mx.sivant.servicio.infraestructura.ServicioJdbc.ComandaBloqueada;
import mx.sivant.servicio.infraestructura.ServicioJdbc.CuentaBloqueada;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** RF01 a RF03. El orden de bloqueo es siempre cuenta y después comanda. */
@Service
public class GestionCuentas {

    public static final int CANTIDAD_MAXIMA = 999;
    private static final String CAJERO = "CAJERO";
    private static final String CUENTA = "CUENTA";
    private static final String COMANDA = "COMANDA";

    private final ServicioJdbc servicio;
    private final CatalogoVenta catalogo;
    private final EmpleadosActivos empleados;
    private final Idempotencia idempotencia;
    private final Avisos avisos;
    private final RegistroAuditoria auditoria;

    public GestionCuentas(ServicioJdbc servicio, CatalogoVenta catalogo, EmpleadosActivos empleados,
            Idempotencia idempotencia, Avisos avisos, RegistroAuditoria auditoria) {
        this.servicio = servicio;
        this.catalogo = catalogo;
        this.empleados = empleados;
        this.idempotencia = idempotencia;
        this.avisos = avisos;
        this.auditoria = auditoria;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('MESERO', 'CAJERO')")
    public List<Mesa> mesas() {
        return servicio.mesas();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('MESERO', 'CAJERO')")
    public Pagina<ResumenCuenta> cuentas(EmpleadoPrincipal actor, EstadoCuenta estado, Long mesa, int pagina,
            int tamano) {
        Long mesero = CAJERO.equals(actor.rol()) ? null : actor.id();
        return servicio.cuentas(mesero, estado, mesa, pagina, tamano);
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyAuthority('MESERO', 'CAJERO')")
    public Cuenta cuenta(EmpleadoPrincipal actor, Long id) {
        return servicio.cuenta(id).filter(c -> CAJERO.equals(actor.rol()) || c.meseroId().equals(actor.id()))
                .orElseThrow(GestionCuentas::sinCuenta);
    }

    @Transactional
    @PreAuthorize("hasAuthority('MESERO')")
    public Cuenta abrir(EmpleadoPrincipal actor, String clave, Long mesa) {
        return idempotencia.ejecutar(actor.id(), "ABRIR_CUENTA", mesa.toString(), clave, Map.of("mesaId", mesa),
                Cuenta.class, () -> {
                    if (!empleados.mantener(actor.id(), "MESERO")) {
                        throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "ACCESO_DENEGADO",
                                "Tu acceso cambió. Inicia sesión de nuevo.", Map.of());
                    }
                    if (!servicio.existeMesa(mesa)) {
                        throw ReglaNegocioException.invalido("mesaId", "La mesa no existe.");
                    }
                    Long id = servicio.abrirCuenta(mesa, actor.id()).orElseThrow(() ->
                            ReglaNegocioException.conflicto("MESA_OCUPADA", "La mesa ya tiene una cuenta abierta."));
                    auditoria.registrar(actor.id(), "CUENTA_ABIERTA", CUENTA, id, Map.of("mesa", mesa));
                    return servicio.cuenta(id).orElseThrow();
                });
    }

    @Transactional
    @PreAuthorize("hasAuthority('MESERO')")
    public Cuenta cerrarSinCobro(EmpleadoPrincipal actor, String clave, Long id, int version) {
        return idempotencia.ejecutar(actor.id(), "CERRAR_CUENTA", id.toString(), clave, Map.of("version", version),
                Cuenta.class, () -> {
                    CuentaBloqueada cuenta = cuentaPropiaAbierta(actor, id);
                    exigirVersion(cuenta.version(), version);
                    DatosServicio.Cuenta datos = servicio.cuenta(id).orElseThrow();
                    if (datos.tieneComandasVigentes()) {
                        throw ReglaNegocioException.conflicto("CUENTA_CON_COMANDAS",
                                "La cuenta tiene comandas vigentes. Debe cobrarse.");
                    }
                    servicio.cerrarCuenta(id);
                    auditoria.registrar(actor.id(), "CUENTA_CERRADA_SIN_COBRO", CUENTA, id,
                            Map.of("mesa", cuenta.mesa()));
                    return servicio.cuenta(id).orElseThrow();
                });
    }

    @Transactional
    @PreAuthorize("hasAuthority('MESERO')")
    public Comanda enviar(EmpleadoPrincipal actor, String clave, Long idCuenta, List<LineaNueva> lineas) {
        return idempotencia.ejecutar(actor.id(), "ENVIAR_COMANDA", idCuenta.toString(), clave, lineas, Comanda.class,
                () -> aplicarEnvio(actor, idCuenta, lineas));
    }

    private Comanda aplicarEnvio(EmpleadoPrincipal actor, Long idCuenta, List<LineaNueva> lineas) {
        cuentaPropiaAbierta(actor, idCuenta);
        record Linea(Long producto, String observaciones) {
        }
        Map<Linea, Integer> agrupadas = new LinkedHashMap<>();
        for (LineaNueva linea : lineas) {
            String observaciones = linea.observaciones() == null ? "" : linea.observaciones().strip();
            agrupadas.merge(new Linea(linea.productoId(), observaciones), linea.cantidad(), Integer::sum);
        }
        if (agrupadas.values().stream().anyMatch(c -> c > CANTIDAD_MAXIMA)) {
            throw ReglaNegocioException.invalido("detalles", "La cantidad máxima por producto es " + CANTIDAD_MAXIMA + ".");
        }
        var productos = new TreeSet<Long>();
        agrupadas.keySet().forEach(l -> productos.add(l.producto()));
        Map<Long, BigDecimal> precios = catalogo.preciosVigentes(productos);
        if (!precios.keySet().containsAll(productos)) {
            throw ReglaNegocioException.conflicto("PRODUCTO_NO_DISPONIBLE",
                    "Un producto ya no está disponible. Actualiza el catálogo.");
        }
        Long id = servicio.insertarComanda(idCuenta);
        agrupadas.forEach((linea, cantidad) -> servicio.insertarDetalle(id, linea.producto(), cantidad,
                precios.get(linea.producto()), linea.observaciones()));
        servicio.registrarTransicion(id, null, EstadoComanda.ENVIADA, actor.id());
        servicio.subirVersionCuenta(idCuenta);
        auditoria.registrar(actor.id(), "COMANDA_ENVIADA", COMANDA, id, Map.of("cuenta", idCuenta));
        avisos.publicarEnTema(Avisos.TEMA_COCINA, "COMANDA_ENVIADA", id, 0);
        return servicio.comanda(id);
    }

    /** Omitir un detalle equivale a eliminarlo. Los precios guardados no cambian. */
    @Transactional
    @PreAuthorize("hasAuthority('MESERO')")
    public Cuenta editar(EmpleadoPrincipal actor, Long id, int version, List<LineaEditada> lineas) {
        Long idCuenta = servicio.cuentaDeComanda(id).orElseThrow(GestionCuentas::sinComanda);
        CuentaBloqueada cuenta = cuentaPropiaAbierta(actor, idCuenta);
        ComandaBloqueada comanda = servicio.bloquearComanda(id);
        exigirVersion(comanda.version(), version);
        if (!comanda.estado().esEditable()) {
            throw ReglaNegocioException.conflicto("COMANDA_NO_EDITABLE",
                    "La comanda ya no puede modificarse. Registra el consumo en una comanda nueva.");
        }
        Map<Long, Integer> nuevas = new HashMap<>();
        for (LineaEditada linea : lineas) {
            if (nuevas.put(linea.detalleId(), linea.cantidad()) != null) {
                throw ReglaNegocioException.invalido("detalles", "No repitas detalles.");
            }
        }
        List<Detalle> actuales = servicio.comanda(id).detalles();
        if (nuevas.isEmpty()) {
            throw ReglaNegocioException.conflicto("ULTIMO_DETALLE",
                    "La comanda debe conservar un producto. Para retirarla completa, pide al cajero cancelarla.");
        }
        if (!actuales.stream().map(Detalle::id).toList().containsAll(nuevas.keySet())) {
            throw ReglaNegocioException.invalido("detalles", "Un detalle no pertenece a la comanda.");
        }
        List<Map<String, Object>> cambios = aplicarCambios(actuales, nuevas);
        if (!cambios.isEmpty()) {
            servicio.subirVersionComanda(id);
            servicio.subirVersionCuenta(idCuenta);
            auditoria.registrar(actor.id(), "COMANDA_EDITADA", COMANDA, id, Map.of("cambios", cambios));
            if (comanda.estado() == EstadoComanda.EN_PREPARACION) {
                avisos.notificarRolPorComanda("COCINERO", TipoNotificacion.MODIFICACION_COMANDA, id,
                        "Mesa " + cuenta.mesa() + ": cambió la comanda " + id + " en preparación.",
                        contexto(cuenta, id));
            }
            avisos.publicarEnTema(Avisos.TEMA_COCINA, "MODIFICACION_COMANDA", id, version + 1);
        }
        return servicio.cuenta(idCuenta).orElseThrow();
    }

    private List<Map<String, Object>> aplicarCambios(List<Detalle> actuales, Map<Long, Integer> nuevas) {
        List<Map<String, Object>> cambios = new ArrayList<>();
        for (Detalle detalle : actuales) {
            Integer cantidad = nuevas.get(detalle.id());
            if (cantidad == null) {
                servicio.eliminarDetalle(detalle.id());
            } else if (cantidad != detalle.cantidad()) {
                servicio.cambiarCantidad(detalle.id(), cantidad);
            } else {
                continue;
            }
            cambios.add(Map.of("producto", detalle.productoId(), "observaciones", detalle.observaciones(),
                    "anterior", detalle.cantidad(), "nuevo", cantidad == null ? 0 : cantidad));
        }
        return cambios;
    }

    @Transactional
    @PreAuthorize("hasAuthority('CAJERO')")
    public Comanda cancelar(EmpleadoPrincipal actor, String clave, Long id, int version, String justificacion) {
        Map<String, Object> cuerpo = new LinkedHashMap<>();
        cuerpo.put("version", version);
        cuerpo.put("justificacion", justificacion.strip());
        return idempotencia.ejecutar(actor.id(), "CANCELAR_COMANDA", id.toString(), clave, cuerpo, Comanda.class,
                () -> aplicarCancelacion(actor, id, version, justificacion.strip()));
    }

    private Comanda aplicarCancelacion(EmpleadoPrincipal actor, Long id, int version, String justificacion) {
        Long idCuenta = servicio.cuentaDeComanda(id).orElseThrow(GestionCuentas::sinComanda);
        CuentaBloqueada cuenta = servicio.bloquearCuenta(idCuenta).orElseThrow(GestionCuentas::sinCuenta);
        exigirAbierta(cuenta);
        ComandaBloqueada comanda = servicio.bloquearComanda(id);
        exigirVersion(comanda.version(), version);
        if (!comanda.estado().esCancelable()) {
            throw ReglaNegocioException.conflicto("COMANDA_NO_CANCELABLE", "La comanda ya no puede cancelarse.");
        }
        servicio.transitar(comanda, EstadoComanda.CANCELADA, actor.id());
        servicio.insertarCancelacion(id, actor.id(), justificacion);
        servicio.subirVersionCuenta(idCuenta);
        auditoria.registrar(actor.id(), "COMANDA_CANCELADA", COMANDA, id,
                Map.of("estadoAnterior", comanda.estado().name(), "justificacion", justificacion));
        avisos.notificarRolPorComanda("COCINERO", TipoNotificacion.CANCELACION_COMANDA, id,
                "Mesa " + cuenta.mesa() + ": se canceló la comanda " + id + ".", contexto(cuenta, id));
        avisos.publicarEnTema(Avisos.TEMA_COCINA, "CANCELACION_COMANDA", id, version + 1);
        avisos.publicarAEmpleado(cuenta.meseroId(), "COMANDA_ACTUALIZADA", id, version + 1);
        return servicio.comanda(id);
    }

    private CuentaBloqueada cuentaPropiaAbierta(EmpleadoPrincipal actor, Long id) {
        CuentaBloqueada cuenta = servicio.bloquearCuenta(id).filter(c -> c.meseroId().equals(actor.id()))
                .orElseThrow(GestionCuentas::sinCuenta);
        exigirAbierta(cuenta);
        return cuenta;
    }

    private static void exigirAbierta(CuentaBloqueada cuenta) {
        if (cuenta.estado() != EstadoCuenta.ABIERTA) {
            throw ReglaNegocioException.conflicto("CUENTA_CERRADA", "La cuenta ya está cerrada.");
        }
    }

    static void exigirVersion(int actual, int enviada) {
        if (actual != enviada) {
            throw ReglaNegocioException.conflicto("VERSION_OBSOLETA",
                    "La información cambió. Revisa los datos actualizados.");
        }
    }

    static Map<String, Object> contexto(CuentaBloqueada cuenta, Long comanda) {
        return Map.of("mesa", cuenta.mesa(), "cuentaId", cuenta.id().toString(), "comandaId", comanda.toString());
    }

    static ReglaNegocioException sinCuenta() {
        return ReglaNegocioException.noEncontrado("La cuenta no existe.");
    }

    static ReglaNegocioException sinComanda() {
        return ReglaNegocioException.noEncontrado("La comanda no existe.");
    }
}
