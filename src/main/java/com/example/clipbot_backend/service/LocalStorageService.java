package com.example.clipbot_backend.service;

import com.example.clipbot_backend.exception.StorageException;
import com.example.clipbot_backend.service.Interfaces.StorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.*;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;


public class LocalStorageService implements StorageService {
    private static final Logger LOGGER = LoggerFactory.getLogger(LocalStorageService.class);

    private final Path baseDir;
    private final Path rawDir;
    private final Path outDir;

    public LocalStorageService(Path baseDir, String rawPrefix, String outPrefix) {
        this.baseDir = baseDir.toAbsolutePath().normalize();
        this.rawDir = this.baseDir.resolve(rawPrefix).normalize();
        this.outDir = this.baseDir.resolve(outPrefix).normalize();

        try {
            Files.createDirectories(rawDir);
            Files.createDirectories(outDir);
            LOGGER.info("LocalStorageService ready. base={}, raw={}, out={}", this.baseDir, this.rawDir, this.outDir);
        } catch (IOException e){
            throw new StorageException("Cannot create storage directories", e);
        }
    }
    @Override
    public Path resolveRaw(String objectKey){
        Path p = safeResolve(rawDir, objectKey);
        return p;
    }

    @Override
    public Path resolveOut(String objectKey){
        Path p = safeResolve(outDir,objectKey);
        return p;
    }
    @Override
    public void uploadToRaw(Path sourceFile, String objectKey) {
        Path target = safeResolve(rawDir, objectKey);
        copyFile(sourceFile, target);
    }

    @Override
    public void uploadToOut(Path sourceFile, String objectKey) {
        Path target = safeResolve(outDir, objectKey);
        copyFile(sourceFile, target);
    }

    @Override
    public void downloadFromRaw(String objectKey, Path targetFile){
        Path src = safeResolve(rawDir, objectKey);
        try{
            Files.createDirectories(targetFile.toAbsolutePath().getParent());
            Files.copy(src, targetFile, REPLACE_EXISTING);
        }catch (IOException e){
            throw new StorageException("Download failed from raw: " + objectKey + " -> " + targetFile, e);
        }
    }
    @Override
    public boolean existsInRaw(String objectKey) {
        return Files.exists(safeResolve(rawDir, objectKey));
    }

    @Override
    public boolean existsInOut(String objectKey) {
        return Files.exists(safeResolve(outDir, objectKey));
    }

    @Override
    public void deleteRaw(String objectKey) {
        deleteIfExists(safeResolve(rawDir, objectKey));
    }

    @Override
    public void deleteOut(String objectKey) {
        deleteIfExists(safeResolve(outDir, objectKey));
    }

    private Path safeResolve(Path root, String objectKey) {
        if(objectKey == null || objectKey.isBlank()){
            throw new StorageException("objectKey is blank");
        }
        // Force forward slashes; strip leading slashes
        String normalizedKey = objectKey.replace('\\', '/').replaceAll("^/+", "");
        Path p = root.resolve(normalizedKey).normalize();
        if (!p.startsWith(root)) {
            throw new StorageException("Invalid objectKey (path traversal?): " + objectKey);
        }
        return p;
    }
    private void copyFile(Path source, Path target) {
        try {
            Files.createDirectories(target.getParent());
            Files.copy(source, target, REPLACE_EXISTING);
        } catch (IOException e) {
            throw new StorageException("Copy failed to " + target, e);
        }
    }

    private void deleteIfExists(Path p) {
        try {
            Files.deleteIfExists(p);
        } catch (IOException e) {
            throw new StorageException("Delete failed: " + p, e);
        }
    }
    @Override public Path rootRaw() { return rawDir; }
    @Override public Path rootOut() { return outDir; }

}
