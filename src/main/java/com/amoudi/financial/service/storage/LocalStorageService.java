package com.amoudi.financial.service.storage;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * Local filesystem implementation of FileStorageService.
 * Stores files securely under the configured root upload directory.
 */
@Slf4j
@Service
public class LocalStorageService implements FileStorageService {

    private final Path rootLocation;

    public LocalStorageService(@Value("${app.storage.local.upload-dir:./storage/uploads}") String uploadDir) {
        this.rootLocation = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootLocation);
            log.info("Initialized local file storage at: {}", this.rootLocation);
        } catch (IOException e) {
            throw new IllegalStateException("Could not initialize storage directory: " + this.rootLocation, e);
        }
    }

    @Override
    public String store(MultipartFile file, String prefix) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty or null file");
        }
        return store(file.getBytes(), file.getOriginalFilename(), prefix);
    }

    @Override
    public String store(byte[] bytes, String originalFilename, String prefix) throws IOException {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("Cannot store empty content");
        }

        String safePrefix = StringUtils.hasText(prefix) ? prefix.replaceAll("[^a-zA-Z0-9_-]", "") : "general";
        String dateSubDir = LocalDate.now().format(DateTimeFormatter.BASIC_ISO_DATE);
        Path targetDir = rootLocation.resolve(safePrefix).resolve(dateSubDir).normalize();
        Files.createDirectories(targetDir);

        String cleanFilename = sanitizeFilename(originalFilename);
        String uniqueFileName = UUID.randomUUID() + "_" + cleanFilename;
        Path targetPath = targetDir.resolve(uniqueFileName).normalize();

        // Path traversal guard
        if (!targetPath.startsWith(rootLocation)) {
            throw new SecurityException("Cannot store file outside current storage directory");
        }

        try (InputStream is = new ByteArrayInputStream(bytes)) {
            Files.copy(is, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        // Return relative object key
        String objectKey = rootLocation.relativize(targetPath).toString().replace('\\', '/');
        log.info("Stored file successfully with objectKey: {}", objectKey);
        return objectKey;
    }

    @Override
    public InputStream retrieve(String objectKey) throws IOException {
        if (!StringUtils.hasText(objectKey)) {
            throw new IllegalArgumentException("Object key cannot be null or empty");
        }
        Path filePath = rootLocation.resolve(objectKey).normalize();
        if (!filePath.startsWith(rootLocation)) {
            throw new SecurityException("Cannot retrieve file outside storage directory");
        }
        if (!Files.exists(filePath) || !Files.isReadable(filePath)) {
            throw new FileNotFoundException("File not found for key: " + objectKey);
        }
        return Files.newInputStream(filePath);
    }

    @Override
    public boolean exists(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return false;
        }
        try {
            Path filePath = rootLocation.resolve(objectKey).normalize();
            if (!filePath.startsWith(rootLocation)) {
                throw new SecurityException("Cannot check file outside storage directory");
            }
            return Files.exists(filePath);
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public boolean delete(String objectKey) {
        if (!StringUtils.hasText(objectKey)) {
            return false;
        }
        try {
            Path filePath = rootLocation.resolve(objectKey).normalize();
            if (!filePath.startsWith(rootLocation)) {
                throw new SecurityException("Cannot delete file outside storage directory");
            }
            boolean deleted = Files.deleteIfExists(filePath);
            if (deleted) {
                log.info("Deleted stored file for key: {}", objectKey);
            }
            return deleted;
        } catch (SecurityException se) {
            throw se;
        } catch (Exception e) {
            log.warn("Failed to delete stored file for key {}: {}", objectKey, e.getMessage());
            return false;
        }
    }

    private String sanitizeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "file.xlsx";
        }
        String clean = Paths.get(filename).getFileName().toString();
        // Remove potentially dangerous characters but keep extension
        clean = clean.replaceAll("[^a-zA-Z0-9._-]", "_");
        return StringUtils.hasText(clean) ? clean : "file.xlsx";
    }
}
