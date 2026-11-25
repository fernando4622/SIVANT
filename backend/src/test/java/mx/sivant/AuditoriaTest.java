package mx.sivant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import mx.sivant.auditoria.aplicacion.RegistroAuditoria;
import mx.sivant.empleados.dominio.Empleado;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.IllegalTransactionStateException;

@DisplayName("RNF09 · Auditoría de cambios")
class AuditoriaTest extends PruebaIntegracion {

    @Autowired
    RegistroAuditoria auditoria;

    ClienteApi admin;

    @BeforeEach
    void iniciarSesion() {
        admin = sesionDe(administrador);
    }

    @AfterEach
    void retirarFallo() {
        sql.execute("ALTER TABLE sivant.auditoria DROP CONSTRAINT IF EXISTS ck_fallo_simulado");
    }

    private List<Map<String, Object>> registros() {
        return sql.queryForList("""
                SELECT id_empleado, accion, entidad, id_registro, fecha_hora, detalle::text AS detalle
                FROM sivant.auditoria ORDER BY id_auditoria
                """);
    }

    private String dato(String ruta) {
        return sql.queryForObject("SELECT detalle #>> '{" + ruta + "}' FROM sivant.auditoria", String.class);
    }

    @Test
    @DisplayName("CP-35 El alta registra responsable, fecha y datos, sin la contraseña")
    void alta() {
        var r = admin.post("/empleados", Map.of("nombreCompleto", "Laura Gómez", "correo", "Laura@sivant.test",
                "contrasena", "Inicial.2026", "rol", "MESERO"));

        assertThat(registros()).singleElement().satisfies(a -> {
            assertThat(a.get("id_empleado")).isEqualTo(administrador.getId());
            assertThat(a.get("accion")).isEqualTo("EMPLEADO_REGISTRADO");
            assertThat(a.get("entidad")).isEqualTo("EMPLEADO");
            assertThat(a.get("id_registro").toString()).isEqualTo(r.json().get("id").asString());
            assertThat(a.get("detalle").toString()).contains("laura@sivant.test", "MESERO", "Laura Gómez")
                    .doesNotContain("Inicial.2026", "$2a$");
        });
        OffsetDateTime fecha = sql.queryForObject("SELECT fecha_hora FROM sivant.auditoria", OffsetDateTime.class);
        assertThat(Duration.between(fecha, OffsetDateTime.now(reloj)).abs()).isLessThan(Duration.ofMinutes(1));
    }

    @Test
    @DisplayName("CP-36 La edición registra el valor anterior y el nuevo; de la contraseña solo el hecho")
    void edicion() {
        Empleado e = crear("Nombre Uno", "uno@sivant.test", "MESERO");
        admin.patch("/empleados/" + e.getId(), Map.of("nombreCompleto", "Nombre Dos", "correo", "dos@sivant.test",
                "rol", "CAJERO", "contrasena", "Otra.Clave.2026"));

        assertThat(registros()).singleElement().satisfies(a -> {
            assertThat(a.get("accion")).isEqualTo("EMPLEADO_EDITADO");
            assertThat(a.get("id_registro")).isEqualTo(e.getId());
            assertThat(a.get("detalle").toString()).doesNotContain("Otra.Clave.2026", "$2a$");
        });
        assertThat(dato("nombreCompleto,anterior")).isEqualTo("Nombre Uno");
        assertThat(dato("nombreCompleto,nuevo")).isEqualTo("Nombre Dos");
        assertThat(dato("correo,anterior")).isEqualTo("uno@sivant.test");
        assertThat(dato("correo,nuevo")).isEqualTo("dos@sivant.test");
        assertThat(dato("rol,anterior")).isEqualTo("MESERO");
        assertThat(dato("rol,nuevo")).isEqualTo("CAJERO");
        assertThat(dato("contrasena")).isEqualTo("MODIFICADA");
    }

    @Test
    @DisplayName("CP-37 La desactivación registra el cambio de estado")
    void desactivacion() {
        Empleado e = crear("MESERO");
        admin.post("/empleados/" + e.getId() + "/desactivacion", Map.of("confirmado", true));
        assertThat(registros()).singleElement()
                .satisfies(a -> assertThat(a.get("accion")).isEqualTo("EMPLEADO_DESACTIVADO"));
        assertThat(dato("estado,anterior")).isEqualTo("ACTIVO");
        assertThat(dato("estado,nuevo")).isEqualTo("INACTIVO");
    }

    @Test
    @DisplayName("CP-38 Consultas, operaciones rechazadas y ediciones sin diferencia no generan registros")
    void sinCambios() {
        Empleado e = crear("MESERO");
        String ruta = "/empleados/" + e.getId();
        ClienteApi mesero = sesionDe(e);

        admin.get("/empleados");
        admin.patch(ruta, Map.of("nombreCompleto", e.getNombreCompleto(), "rol", "MESERO"));
        admin.patch(ruta, Map.of("correo", administrador.getCorreo()));
        admin.post(ruta + "/desactivacion", Map.of("confirmado", false));
        admin.post("/empleados/" + administrador.getId() + "/desactivacion", Map.of("confirmado", true));
        mesero.patch(ruta, Map.of("nombreCompleto", "Intento"));

        assertThat(registros()).isEmpty();
    }

    @Test
    @DisplayName("CP-39 Si no se puede auditar, la operación se revierte completa")
    void reversion() {
        Empleado e = crear("MESERO");
        sql.execute("ALTER TABLE sivant.auditoria ADD CONSTRAINT ck_fallo_simulado CHECK (false) NOT VALID");
        long total = empleados.count();

        assertThat(admin.post("/empleados", Map.of("nombreCompleto", "No Debe Existir", "correo", "no@sivant.test",
                "contrasena", "Inicial.2026", "rol", "MESERO")).estado()).isEqualTo(500);
        assertThat(admin.patch("/empleados/" + e.getId(), Map.of("nombreCompleto", "Otro")).estado()).isEqualTo(500);
        assertThat(admin.post("/empleados/" + e.getId() + "/desactivacion", Map.of("confirmado", true)).estado())
                .isEqualTo(500);

        assertThat(empleados.count()).isEqualTo(total);
        assertThat(recargar(e).getNombreCompleto()).isEqualTo(e.getNombreCompleto());
        assertThat(recargar(e).estaActivo()).isTrue();
    }

    @Test
    @DisplayName("CP-40 PostgreSQL impide modificar o eliminar registros de auditoría")
    void inmutable() {
        admin.post("/empleados", Map.of("nombreCompleto", "Laura", "correo", "laura@sivant.test",
                "contrasena", "Inicial.2026", "rol", "MESERO"));
        assertThatThrownBy(() -> sql.update("UPDATE sivant.auditoria SET accion = 'OTRA'"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> sql.update("DELETE FROM sivant.auditoria"))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> sql.update("DELETE FROM sivant.empleado WHERE id_empleado = ?",
                administrador.getId())).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(registros()).hasSize(1);
    }

    @Test
    @DisplayName("CP-41 No se puede auditar fuera de una transacción")
    void exigeTransaccion() {
        assertThatThrownBy(() -> auditoria.registrar(administrador.getId(), "PRUEBA", "EMPLEADO", 1L, Map.of()))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(registros()).isEmpty();
    }
}
