package mx.sivant.empleados.infraestructura;

import java.util.Locale;
import mx.sivant.empleados.dominio.Empleado;
import mx.sivant.empleados.dominio.EstadoEmpleado;
import org.springframework.data.jpa.domain.Specification;

public final class FiltrosEmpleado {

    private static final char ESCAPE = '\\';

    private FiltrosEmpleado() {
    }

    public static Specification<Empleado> con(String q, String nombre, String correo, String rol,
            EstadoEmpleado estado) {
        Specification<Empleado> filtro = Specification.unrestricted();
        if (tieneTexto(q)) {
            filtro = filtro.and(contiene("nombreCompleto", q).or(contiene("correo", q)));
        }
        if (tieneTexto(nombre)) {
            filtro = filtro.and(contiene("nombreCompleto", nombre));
        }
        if (tieneTexto(correo)) {
            filtro = filtro.and(contiene("correo", correo));
        }
        if (tieneTexto(rol)) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("rol").get("nombre"), rol));
        }
        if (estado != null) {
            filtro = filtro.and((raiz, consulta, cb) -> cb.equal(raiz.get("estado"), estado));
        }
        return filtro;
    }

    private static Specification<Empleado> contiene(String campo, String texto) {
        String patron = "%" + texto.strip().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        return (raiz, consulta, cb) -> cb.like(cb.lower(raiz.get(campo)), patron, ESCAPE);
    }

    private static boolean tieneTexto(String valor) {
        return valor != null && !valor.isBlank();
    }
}
