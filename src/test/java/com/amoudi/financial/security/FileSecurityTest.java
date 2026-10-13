package com.amoudi.financial.security;

import com.amoudi.financial.exception.BadRequestException;
import com.amoudi.financial.exception.FileValidationException;
import com.amoudi.financial.service.storage.LocalStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSecurityTest {

    @TempDir
    Path tempStorageDir;

    private LocalStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = new LocalStorageService(tempStorageDir.toString());
    }

    @Test
    @DisplayName("Path traversal with Unix ../../ is rejected with SecurityException")
    void retrieve_pathTraversalUnix_throwsSecurityException() {
        assertThatThrownBy(() -> storageService.retrieve("../../etc/passwd"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Cannot retrieve file outside storage directory");
    }

    @Test
    @DisplayName("Path traversal with Windows ..\\..\\ is rejected with SecurityException")
    void retrieve_pathTraversalWindows_throwsSecurityException() {
        assertThatThrownBy(() -> storageService.retrieve("..\\..\\Windows\\System32\\cmd.exe"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Cannot retrieve file outside storage directory");
    }

    @Test
    @DisplayName("Absolute path /etc/passwd is rejected with SecurityException")
    void retrieve_absolutePath_throwsSecurityException() {
        assertThatThrownBy(() -> storageService.retrieve("/etc/passwd"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("Delete with path traversal is rejected with SecurityException")
    void delete_pathTraversal_throwsSecurityException() {
        assertThatThrownBy(() -> storageService.delete("../../system32/drivers/etc/hosts"))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("Cannot delete file outside storage directory");
    }

    @Test
    @DisplayName("Check exists with path traversal is rejected with SecurityException")
    void exists_pathTraversal_throwsSecurityException() {
        assertThatThrownBy(() -> storageService.exists("../../../some_file.txt"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    @DisplayName("Store and retrieve legitimate file succeeds inside isolated root")
    void storeAndRetrieve_legitimateFile_succeeds() throws IOException {
        byte[] content = "Authoritative Receipt Data".getBytes();
        String key = storageService.store(content, "receipt.pdf", "receipts");

        assertThat(key).isNotNull();
        assertThat(key).startsWith("receipts/");
        assertThat(storageService.exists(key)).isTrue();

        try (var is = storageService.retrieve(key)) {
            byte[] readBytes = is.readAllBytes();
            assertThat(readBytes).isEqualTo(content);
        }

        // Clean up delete
        boolean deleted = storageService.delete(key);
        assertThat(deleted).isTrue();
        assertThat(storageService.exists(key)).isFalse();
    }

    @Test
    @DisplayName("Sanitizes malicious filename with path traversal characters on store")
    void store_sanitizesMaliciousFilename() throws IOException {
        byte[] content = "Test Content".getBytes();
        // Malicious filename attempting to escape
        String key = storageService.store(content, "../../../evil.pdf", "receipts");

        assertThat(key).startsWith("receipts/");
        assertThat(storageService.exists(key)).isTrue();

        // Ensure file was actually saved within tempStorageDir
        Path actualPath = tempStorageDir.resolve(key).normalize();
        assertThat(actualPath).startsWith(tempStorageDir);
    }
}
