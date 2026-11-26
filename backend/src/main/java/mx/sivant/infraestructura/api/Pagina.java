package mx.sivant.infraestructura.api;

import java.util.List;
import java.util.function.Function;

/** Página con el contrato común de los listados. */
public record Pagina<T>(List<T> contenido, int page, int size, long totalElementos, int totalPaginas) {

    public static <T> Pagina<T> de(List<T> contenido, int page, int size, long total) {
        return new Pagina<>(contenido, page, size, total, (int) ((total + size - 1) / size));
    }

    public <R> Pagina<R> con(Function<T, R> conversion) {
        return new Pagina<>(contenido.stream().map(conversion).toList(), page, size, totalElementos, totalPaginas);
    }
}
