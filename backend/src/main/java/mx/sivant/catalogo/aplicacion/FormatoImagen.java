package mx.sivant.catalogo.aplicacion;

import mx.sivant.infraestructura.errores.ReglaNegocioException;

/** Reconoce el formato por los primeros bytes del archivo, no por su nombre ni por el tipo que declara el cliente. */
final class FormatoImagen {

    static final int TAMANO_MAXIMO = 2 * 1024 * 1024;

    private FormatoImagen() {
    }

    static String extension(byte[] b) {
        if (b == null || b.length == 0) {
            throw ReglaNegocioException.invalido("archivo", "Selecciona una imagen.");
        }
        if (b.length > TAMANO_MAXIMO) {
            throw ReglaNegocioException.invalido("archivo", "La imagen pesa más de 2 MB.");
        }
        if (empieza(b, 0, 0xFF, 0xD8, 0xFF)) {
            return "jpg";
        }
        if (empieza(b, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return "png";
        }
        if (empieza(b, 0, 'R', 'I', 'F', 'F') && empieza(b, 8, 'W', 'E', 'B', 'P')) {
            return "webp";
        }
        throw ReglaNegocioException.invalido("archivo", "Usa una imagen JPG, PNG o WebP.");
    }

    static String tipoContenido(String ruta) {
        if (ruta.endsWith(".png")) {
            return "image/png";
        }
        return ruta.endsWith(".webp") ? "image/webp" : "image/jpeg";
    }

    private static boolean empieza(byte[] b, int desde, int... firma) {
        if (b.length < desde + firma.length) {
            return false;
        }
        for (int i = 0; i < firma.length; i++) {
            if ((b[desde + i] & 0xFF) != firma[i]) {
                return false;
            }
        }
        return true;
    }
}
