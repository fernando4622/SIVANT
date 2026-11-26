package mx.sivant.catalogo.infraestructura;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Guarda las imágenes en una carpeta local del servidor. La base solo conserva la ruta relativa, así que para el
 * despliegue basta con cambiar esta clase por un almacén externo sin tocar los datos.
 */
@Component
public class AlmacenImagenes {

    private static final Logger LOG = LoggerFactory.getLogger(AlmacenImagenes.class);

    private final Path raiz;

    public AlmacenImagenes(@Value("${sivant.imagenes.directorio:almacen}") String directorio) {
        this.raiz = Path.of(directorio).toAbsolutePath().normalize();
    }

    /** Escribe el archivo con un nombre nuevo y devuelve su ruta relativa. */
    public String guardar(byte[] contenido, String extension) {
        String ruta = "productos/" + UUID.randomUUID() + "." + extension;
        Path destino = archivo(ruta);
        try {
            Files.createDirectories(destino.getParent());
            Path temporal = Files.createTempFile(destino.getParent(), "subida-", ".tmp");
            Files.write(temporal, contenido);
            Files.move(temporal, destino, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return ruta;
    }

    public Path archivo(String ruta) {
        Path destino = raiz.resolve(ruta).normalize();
        if (!destino.startsWith(raiz)) {
            throw new IllegalArgumentException("Ruta fuera del almacén");
        }
        return destino;
    }

    /** Un archivo que no se pudo borrar solo ocupa espacio: no interrumpe la operación. */
    public void borrar(String ruta) {
        try {
            Files.deleteIfExists(archivo(ruta));
        } catch (IOException e) {
            LOG.warn("No se pudo borrar la imagen {}", ruta, e);
        }
    }
}
