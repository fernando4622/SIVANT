package mx.sivant;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

/** Permite simular el paso del tiempo sin esperar. */
public class RelojAjustable extends Clock {

    private volatile Duration desfase = Duration.ZERO;

    public void avanzar(Duration tiempo) {
        desfase = desfase.plus(tiempo);
    }

    @Override
    public Instant instant() {
        return Instant.now().plus(desfase);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return this;
    }
}
