package com.ecuapp.inventary.media;

import com.ecuapp.inventary.common.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stores validated product images under an application-controlled directory.
 * Only raster PNG and JPEG files with a valid signature and decodable pixel data are accepted.
 * User-supplied filenames and MIME types are never used to construct paths or select file formats.
 */
@Component
public class ProductImageStorage {
    public static final long DEFAULT_MAX_BYTES = 5L * 1024 * 1024;
    private static final long MAX_PIXELS = 12_000_000L;
    private static final Pattern SAFE_FILENAME = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg)"
    );

    public record StoredImage(String fileName, String contentType, long size) {
        public String publicUrl() {
            return "/api/v1/public/media/products/" + fileName;
        }
    }

    private final Path root;
    private final long maxBytes;

    public ProductImageStorage(
        @Value("${app.media.storage-path:./data/product-images}") String storagePath,
        @Value("${app.media.max-upload-bytes:5242880}") long maxBytes
    ) {
        if (maxBytes < 1 || maxBytes > 25L * 1024 * 1024) {
            throw new IllegalStateException("MEDIA_MAX_UPLOAD_BYTES debe estar entre 1 byte y 25 MiB.");
        }
        this.root = Path.of(storagePath).toAbsolutePath().normalize();
        this.maxBytes = maxBytes;
    }

    public StoredImage store(MultipartFile upload) {
        if (upload == null || upload.isEmpty()) {
            throw new BusinessRuleException("Selecciona un archivo de imagen no vacío.");
        }
        if (upload.getSize() > maxBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "La imagen supera el límite de tamaño permitido.");
        }

        final byte[] bytes;
        try {
            bytes = upload.getBytes();
        } catch (IOException ex) {
            throw new BusinessRuleException("No se pudo leer el archivo de imagen.");
        }
        if (bytes.length == 0 || bytes.length > maxBytes) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "El archivo de imagen no cumple el tamaño permitido.");
        }

        String extension;
        String contentType;
        if (isPng(bytes)) {
            extension = ".png";
            contentType = "image/png";
        } else if (isJpeg(bytes)) {
            extension = ".jpg";
            contentType = "image/jpeg";
        } else {
            throw new BusinessRuleException("Formato no permitido. Usa una imagen PNG o JPEG válida.");
        }
        validateDecodedImage(bytes, contentType);

        String fileName = UUID.randomUUID().toString() + extension;
        Path destination = resolve(fileName);
        try {
            Files.createDirectories(root);
            Files.write(destination, bytes, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        } catch (IOException ex) {
            throw new IllegalStateException("No se pudo guardar la imagen en el almacenamiento configurado.", ex);
        }
        return new StoredImage(fileName, contentType, bytes.length);
    }

    public Path resolve(String fileName) {
        if (fileName == null || !SAFE_FILENAME.matcher(fileName).matches()) {
            throw new BusinessRuleException("Nombre de archivo de imagen no válido.");
        }
        Path resolved = root.resolve(fileName).normalize();
        if (!resolved.startsWith(root)) {
            throw new BusinessRuleException("Ruta de imagen fuera del almacenamiento permitido.");
        }
        return resolved;
    }

    public void deleteQuietly(String fileName) {
        try {
            Files.deleteIfExists(resolve(fileName));
        } catch (IOException | RuntimeException ignored) {
            // A rollback-cleanup failure must not mask the original database error; an operator can
            // safely remove orphan files because names are random UUIDs and never accepted from clients.
        }
    }

    private void validateDecodedImage(byte[] bytes, String expectedContentType) {
        try (ByteArrayInputStream input = new ByteArrayInputStream(bytes);
             ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) throw new BusinessRuleException("El archivo no contiene una imagen válida.");
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) throw new BusinessRuleException("No se pudo decodificar la imagen.");
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                boolean correctFormat = expectedContentType.equals("image/png")
                    ? format.equals("png")
                    : format.equals("jpeg") || format.equals("jpg");
                if (!correctFormat) throw new BusinessRuleException("La extensión y el contenido de la imagen no coinciden.");
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || (long) width * height > MAX_PIXELS) {
                    throw new BusinessRuleException("La resolución de la imagen supera el máximo permitido.");
                }
                if (reader.read(0) == null) throw new BusinessRuleException("No se pudo decodificar la imagen.");
            } finally {
                reader.dispose();
            }
        } catch (BusinessRuleException ex) {
            throw ex;
        } catch (IOException | RuntimeException ex) {
            throw new BusinessRuleException("El archivo está corrupto o no contiene una imagen compatible.");
        }
    }

    private boolean isPng(byte[] bytes) {
        byte[] signature = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
        if (bytes.length < signature.length) return false;
        for (int i = 0; i < signature.length; i++) if (bytes[i] != signature[i]) return false;
        return true;
    }

    private boolean isJpeg(byte[] bytes) {
        return bytes.length >= 3
            && (bytes[0] & 0xFF) == 0xFF
            && (bytes[1] & 0xFF) == 0xD8
            && (bytes[2] & 0xFF) == 0xFF;
    }
}
