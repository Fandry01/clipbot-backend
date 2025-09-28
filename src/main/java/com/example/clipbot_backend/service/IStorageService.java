package com.example.clipbot_backend.service;

import java.net.URI;
import java.nio.file.Path;

public interface IStorageService {
    /** Voor presigned upload (S3); lokaal kan null/’noop’ terugkomen of een file-URL. */
    default URI presignUpload(String objectKey, String contentType, long ttlSeconds) {
        return null;
    }
    /** Presigned download (S3); lokaal kan dit een file:// of http:// mapping zijn of null. */
    default URI presignDownload(String objectKey, long ttlSeconds) {
        return null;
    }
    Path resolveRaw(String objectKey);
    Path resolveOut(String objectKey);

    /** Schrijf een bestand naar raw/out. Maakt mappen aan indien nodig. */
    void uploadToRaw(Path sourceFile, String objectKey);
    void uploadToOut(Path sourceFile, String objectKey);
    void downloadFromRaw(String objectKey, Path targetFile);

    /** Lees/bestaan/verwijderen. */
    boolean existsInRaw(String objectKey);
    boolean existsInOut(String objectKey);
    void deleteRaw(String objectKey);
    void deleteOut(String objectKey);


}
