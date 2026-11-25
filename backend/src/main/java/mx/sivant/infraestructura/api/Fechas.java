package mx.sivant.infraestructura.api;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/** Los días de los filtros se interpretan en la zona del negocio. */
public final class Fechas {

    public static final ZoneId ZONA_NEGOCIO = ZoneId.of("America/Mexico_City");

    private Fechas() {
    }

    public static OffsetDateTime inicio(LocalDate dia) {
        return dia == null ? null : dia.atStartOfDay(ZONA_NEGOCIO).toOffsetDateTime();
    }

    /** Límite excluido que incluye completo el día indicado. */
    public static OffsetDateTime fin(LocalDate dia) {
        return dia == null ? null : inicio(dia.plusDays(1));
    }
}
