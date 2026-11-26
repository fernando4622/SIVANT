package mx.sivant.identidad.infraestructura;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sivant.sesion")
public record PropiedadesSesion(Duration inactividad) {
}
