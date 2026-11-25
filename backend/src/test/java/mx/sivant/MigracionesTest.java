package mx.sivant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

@DisplayName("Fase 0 · Arranque desde una base vacía")
class MigracionesTest extends PruebaIntegracion {

    @Autowired
    Flyway flyway;

    @Test
    @DisplayName("CP-01 Flyway aplica todas las migraciones y repetirlas no cambia nada")
    void migraciones() {
        assertThat(sql.queryForObject("SHOW server_version", String.class)).startsWith("18");
        // La creación del esquema queda registrada sin versión.
        assertThat(flyway.info().applied()).filteredOn(m -> m.getVersion() != null)
                .extracting(m -> m.getVersion().getVersion()).containsExactly("1", "2", "3");
        assertThat(flyway.info().pending()).isEmpty();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
    }

    @Test
    @DisplayName("CP-02 El catálogo contiene los cinco roles")
    void roles() {
        assertThat(sql.queryForList("SELECT nombre FROM sivant.rol ORDER BY nombre", String.class))
                .containsExactly("ADMINISTRADOR", "ALMACENISTA", "CAJERO", "COCINERO", "MESERO");
    }

    @Test
    @DisplayName("CP-03 PostgreSQL rechaza correo repetido, en mayúsculas o vacío, y estado desconocido")
    void restricciones() {
        insertar("uno@sivant.test", "ACTIVO");
        for (String correo : new String[] {"uno@sivant.test", "Dos@sivant.test", " tres@sivant.test", ""}) {
            assertThatThrownBy(() -> insertar(correo, "ACTIVO")).isInstanceOf(DataIntegrityViolationException.class);
        }
        assertThatThrownBy(() -> insertar("cuatro@sivant.test", "BAJA"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> sql.update("DELETE FROM sivant.rol WHERE nombre = 'MESERO'"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private void insertar(String correo, String estado) {
        sql.update("""
                INSERT INTO sivant.empleado (id_rol, nombre_completo, correo, contrasena_hash, estado)
                SELECT id_rol, 'Prueba', ?, 'x', ? FROM sivant.rol WHERE nombre = 'MESERO'
                """, correo, estado);
    }
}
