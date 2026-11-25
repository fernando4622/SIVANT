package mx.sivant.infraestructura.arranque;

import mx.sivant.empleados.dominio.Rol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Crea el primer administrador con las variables SIVANT_ADMIN_*, solo si no existe ninguno. */
@Component
@Profile("!dev")
class AdministradorInicial implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(AdministradorInicial.class);
    private static final int LONGITUD_MINIMA = 12;

    private final AltaInicial alta;
    private final Datos datos;

    AdministradorInicial(AltaInicial alta, Datos datos) {
        this.alta = alta;
        this.datos = datos;
    }

    @ConfigurationProperties("sivant.administrador-inicial")
    record Datos(String nombre, String correo, String contrasena) {

        boolean completos() {
            return !vacio(nombre) && !vacio(correo) && !vacio(contrasena);
        }

        private static boolean vacio(String valor) {
            return valor == null || valor.isBlank();
        }

        @Override
        public String toString() {
            return "Datos[...]";
        }
    }

    @Override
    public void run(ApplicationArguments args) {
        if (alta.existeRol(Rol.ADMINISTRADOR)) {
            return;
        }
        if (!datos.completos()) {
            LOG.warn("No existe un administrador. Define SIVANT_ADMIN_NOMBRE, SIVANT_ADMIN_CORREO y "
                    + "SIVANT_ADMIN_CONTRASENA para crearlo.");
            return;
        }
        if (datos.contrasena().length() < LONGITUD_MINIMA) {
            throw new IllegalStateException(
                    "SIVANT_ADMIN_CONTRASENA debe tener al menos " + LONGITUD_MINIMA + " caracteres.");
        }
        alta.crearSiFalta(datos.nombre(), datos.correo(), datos.contrasena(), Rol.ADMINISTRADOR, true);
        LOG.info("Administrador inicial creado. Retira las variables SIVANT_ADMIN_*.");
    }
}
