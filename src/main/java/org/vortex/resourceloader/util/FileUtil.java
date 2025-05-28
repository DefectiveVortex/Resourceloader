// FileUtil.java
package org.vortex.resourceloader.util;

import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.stream.Stream;
import java.util.zip.*;
import java.util.logging.Logger;

public class FileUtil {
    private static final int BUFFER_SIZE = 8192;
    private static final String TEMP_PREFIX = "resourceloader_";
    private static final String TEMP_SUFFIX = ".tmp";

    public static byte[] calcSHA1(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new FileNotFoundException("File does not exist");
        }

        try {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
            try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file), BUFFER_SIZE)) {
                byte[] buffer = new byte[BUFFER_SIZE];
            int read;
                while ((read = bis.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException("SHA-1 algorithm not available", e);
        }
    }

    public static void validateZipFile(File file) throws IOException {
        if (file == null || !file.exists()) {
            throw new FileNotFoundException("File does not exist");
        }

        try (ZipFile zipFile = new ZipFile(file)) {
            if (zipFile.size() == 0) {
                throw new IOException("ZIP file is empty");
            }
        } catch (ZipException e) {
            throw new IOException("Invalid ZIP file format", e);
        }
    }

    public static long getFolderSize(Path folder) throws IOException {
        if (!Files.isDirectory(folder)) {
            throw new IllegalArgumentException("Path is not a directory: " + folder);
        }

        try (Stream<Path> walk = Files.walk(folder)) {
            return walk.filter(Files::isRegularFile)
                      .mapToLong(p -> {
                          try {
                              return Files.size(p);
                          } catch (IOException e) {
                              return 0L;
                          }
                      })
                      .sum();
        }
    }

    public static void safeMove(Path source, Path target) throws IOException {
        if (!Files.exists(source)) {
            throw new FileNotFoundException("Source file does not exist: " + source);
        }

        Path tempFile = null;
        try {
            // Create parent directories if they don't exist
            Files.createDirectories(target.getParent());
            
            // Create temp file in the same directory as target
            tempFile = Files.createTempFile(
                target.getParent(),
                TEMP_PREFIX,
                TEMP_SUFFIX
            );

            // Copy to temp file
            Files.copy(source, tempFile, StandardCopyOption.REPLACE_EXISTING);

            // Atomic move from temp to target
            try {
                Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                // Fallback to non-atomic move if atomic is not supported
                Files.move(tempFile, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            // Clean up temp file if it still exists
            if (tempFile != null) {
                try {
                    Files.deleteIfExists(tempFile);
                } catch (IOException e) {
                    // Log but don't throw - this is cleanup code
                    Logger.getLogger(FileUtil.class.getName())
                          .warning("Failed to delete temporary file: " + tempFile);
                }
            }
        }
    }

    public static void cleanDirectory(Path directory) throws IOException {
        if (!Files.isDirectory(directory)) {
            throw new IllegalArgumentException("Path is not a directory: " + directory);
        }

        try (Stream<Path> walk = Files.walk(directory)) {
            walk.sorted((a, b) -> b.toString().length() - a.toString().length()) // Delete children first
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // Log but continue with other files
                        Logger.getLogger(FileUtil.class.getName())
                              .warning("Failed to delete: " + path);
                    }
                });
        }
    }

    public static String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return "";
        }
        return filename.substring(filename.lastIndexOf(".") + 1);
    }

    public static boolean isValidResourcePack(File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            return false;
        }

        String extension = getFileExtension(file.getName()).toLowerCase();
        if (!extension.equals("zip")) {
            return false;
        }

        try {
            validateZipFile(file);
            return true;
        } catch (IOException e) {
            return false;
        }
    }
}