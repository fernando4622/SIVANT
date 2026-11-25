package mx.sivant.identidad.infraestructura;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.function.Supplier;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/** Patrón de Spring Security para SPA: cookie XSRF-TOKEN y encabezado X-XSRF-TOKEN. */
final class CsrfSpaHandler implements CsrfTokenRequestHandler {

    private final CsrfTokenRequestHandler plano = new CsrfTokenRequestAttributeHandler();
    private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

    @Override
    public void handle(HttpServletRequest solicitud, HttpServletResponse respuesta, Supplier<CsrfToken> token) {
        xor.handle(solicitud, respuesta, token);
        // Fuerza la escritura de la cookie.
        token.get();
    }

    @Override
    public String resolveCsrfTokenValue(HttpServletRequest solicitud, CsrfToken token) {
        boolean vieneEnEncabezado = StringUtils.hasText(solicitud.getHeader(token.getHeaderName()));
        return (vieneEnEncabezado ? plano : xor).resolveCsrfTokenValue(solicitud, token);
    }
}
