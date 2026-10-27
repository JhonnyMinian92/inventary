package com.ecuapp.inventary.media;

import com.ecuapp.inventary.common.ResourceNotFoundException;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;

/** Public access is limited to server-generated UUID filenames for validated raster images. */
@RestController
public class PublicProductImageController {
    private static final Pattern SAFE_FILENAME = Pattern.compile(
        "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(png|jpg)"
    );
    private final ProductImageStorage storage;
    private final ProductImageRepository images;

    public PublicProductImageController(ProductImageStorage storage, ProductImageRepository images) {
        this.storage = storage;
        this.images = images;
    }

    @GetMapping("/api/v1/public/media/products/{fileName:.+}")
    public ResponseEntity<Resource> get(@PathVariable String fileName) {
        if (fileName == null || !SAFE_FILENAME.matcher(fileName).matches()) {
            throw new ResourceNotFoundException("Imagen pública", "no-disponible");
        }
        String publicUrl = "/api/v1/public/media/products/" + fileName;
        if (!images.existsByImageUrlAndPublishedTrue(publicUrl)) {
            throw new ResourceNotFoundException("Imagen pública", fileName);
        }
        Path path = storage.resolve(fileName);
        if (!Files.isRegularFile(path)) {
            throw new ResourceNotFoundException("Imagen pública", fileName);
        }
        final long length;
        try {
            length = Files.size(path);
        } catch (IOException ex) {
            throw new ResourceNotFoundException("Imagen pública", fileName);
        }

        MediaType contentType = fileName.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
            .contentType(contentType)
            .contentLength(length)
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(1)).cachePublic().mustRevalidate())
            .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.inline().filename(fileName).build().toString())
            .header("X-Content-Type-Options", "nosniff")
            .body(new FileSystemResource(path));
    }
}
