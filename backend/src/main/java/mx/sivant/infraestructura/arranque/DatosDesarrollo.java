package mx.sivant.infraestructura.arranque;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Personas ficticias, una por rol. Existe únicamente en el perfil dev. */
@Component
@Profile("dev")
class DatosDesarrollo implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DatosDesarrollo.class);

    private final AltaInicial alta;
    private final String contrasena;

    DatosDesarrollo(AltaInicial alta, @Value("${sivant.desarrollo.contrasena}") String contrasena) {
        this.alta = alta;
        this.contrasena = contrasena;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (contrasena.isBlank()) {
            LOG.warn("SIVANT_DEV_CONTRASENA está vacía: no se crean cuentas ficticias.");
            return;
        }
        alta.crearSiFalta("Rodrigo Herrera", "admin@sivant.test", contrasena, "ADMINISTRADOR", true);
        alta.crearSiFalta("Juan Pérez", "mesero@sivant.test", contrasena, "MESERO", true);
        alta.crearSiFalta("María López", "cajero@sivant.test", contrasena, "CAJERO", true);
        alta.crearSiFalta("Carla Ruiz", "cocinero@sivant.test", contrasena, "COCINERO", true);
        alta.crearSiFalta("Pedro Torres", "almacenista@sivant.test", contrasena, "ALMACENISTA", true);
        alta.crearSiFalta("Ana Valenzuela", "inactivo@sivant.test", contrasena, "MESERO", false);
    }
}
