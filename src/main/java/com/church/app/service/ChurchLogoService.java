package com.church.app.service;

import com.church.app.config.AppStorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Finds the image to show for a church, falling back to a shipped default.
 *
 * <p>Logos are files on disk named {@code <church_id>.png}, not rows in the database:
 * they are replaced rarely, read on every page, and a backup of the directory is easier
 * to reason about than blobs in a table. The trade-off is that a database restore alone
 * does not bring the logos back.
 */
@Service
public class ChurchLogoService {

    private static final Logger log = LoggerFactory.getLogger(ChurchLogoService.class);

    private static final String DEFAULT_LOGO = "static/images/default-church-logo.svg";
    private static final MediaType SVG = MediaType.valueOf("image/svg+xml");

    private final Path logoDir;

    public ChurchLogoService(AppStorageProperties storageProperties) {
        this.logoDir = Paths.get(storageProperties.getLogoDir()).toAbsolutePath().normalize();
        log.info("Church logos are read from {}", logoDir);
    }

    /**
     * @param churchId the church to render, or null for a platform user with no church
     *                 selected -- both cases yield the default logo
     */
    public ChurchLogo resolve(Long churchId) {
        if (churchId == null) {
            return defaultLogo();
        }

        // The id is a Long taken from the authenticated principal, never from the request,
        // so no user-supplied text reaches this path and traversal is not possible.
        Path file = logoDir.resolve(churchId + ".png");
        if (!Files.isRegularFile(file) || !Files.isReadable(file)) {
            return defaultLogo();
        }

        try {
            long lastModified = Files.getLastModifiedTime(file).toMillis();
            return new ChurchLogo(new FileSystemResource(file), MediaType.IMAGE_PNG, lastModified);
        } catch (IOException e) {
            log.warn("Logo for church {} exists but could not be read: {}", churchId, e.getMessage());
            return defaultLogo();
        }
    }

    private ChurchLogo defaultLogo() {
        // -1 disables conditional-GET handling: the default never changes between
        // deployments, and the response is small enough that a revalidation costs nothing.
        return new ChurchLogo(new ClassPathResource(DEFAULT_LOGO), SVG, -1L);
    }

    /** @param lastModified epoch millis, or -1 when the image has no meaningful timestamp */
    public record ChurchLogo(Resource resource, MediaType mediaType, long lastModified) {
    }
}
