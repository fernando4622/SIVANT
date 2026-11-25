package mx.sivant.ventas.dominio;

import java.util.Locale;

/** Referencias que entregan la terminal o el banco. Nunca datos de la tarjeta. */
public final class Referencias {

    /** Letras, números y guiones, con espacios alrededor que se descartan. */
    public static final String FORMATO = "^ *[A-Za-z0-9-]{0,40} *$";

    private Referencias() {
    }

    public static String normalizar(String referencia) {
        if (referencia == null || referencia.isBlank()) {
            return null;
        }
        return referencia.strip().toUpperCase(Locale.ROOT);
    }

    /** De 13 a 19 dígitos que cumplen Luhn: probablemente es el número de una tarjeta. */
    public static boolean pareceTarjeta(String referencia) {
        String digitos = referencia.replace("-", "");
        if (!digitos.matches("\\d{13,19}")) {
            return false;
        }
        int suma = 0;
        for (int i = 0; i < digitos.length(); i++) {
            int digito = digitos.charAt(digitos.length() - 1 - i) - '0';
            if (i % 2 == 1) {
                digito = digito > 4 ? digito * 2 - 9 : digito * 2;
            }
            suma += digito;
        }
        return suma % 10 == 0;
    }
}
