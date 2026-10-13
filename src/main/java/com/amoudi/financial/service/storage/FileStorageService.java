package com.amoudi.financial.service.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;

/**
 * Contract for storing and retrieving uploaded files (Galileo/BSP reports, receipts).
 */
public interface FileStorageService {

    /**
     * Stores an uploaded multipart file and returns the unique object key.
     *
     * @param file the multipart file
     * @param prefix sub-folder or category prefix (e.g., "imports")
     * @return unique object key
     * @throws IOException on I/O error
     */
    String store(MultipartFile file, String prefix) throws IOException;

    /**
     * Stores raw bytes and returns the unique object key.
     *
     * @param bytes binary content
     * @param originalFilename original name of the file
     * @param prefix sub-folder or category prefix
     * @return unique object key
     * @throws IOException on I/O error
     */
    String store(byte[] bytes, String originalFilename, String prefix) throws IOException;

    /**
     * Retrieves the binary stream for a stored object key.
     *
     * @param objectKey the object key
     * @return InputStream of the file content
     * @throws IOException on I/O error or if file does not exist
     */
    InputStream retrieve(String objectKey) throws IOException;

    /**
     * Checks if a file exists for the given object key.
     *
     * @param objectKey the object key
     * @return true if exists, false otherwise
     */
    boolean exists(String objectKey);

    /**
     * Deletes a file at the given object key safely.
     *
     * @param objectKey the object key
     * @return true if deleted or did not exist, false on failure
     */
    boolean delete(String objectKey);
}
