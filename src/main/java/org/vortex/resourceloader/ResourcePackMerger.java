package org.vortex.resourceloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.io.FileUtils;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;
import java.nio.file.*;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ResourcePackMerger {
    private final Resourceloader plugin;
    private final Logger logger;
    private static final int BUFFER_SIZE = 32768; // 32KB buffer
    private static final int THREAD_POOL_SIZE = Runtime.getRuntime().availableProcessors();
    private final ExecutorService executor;
    private final Set<File> pendingCleanup;

    public ResourcePackMerger(Resourceloader plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE);
        this.pendingCleanup = ConcurrentHashMap.newKeySet();
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            cleanupAllWorkDirs();
        }
    }

    private void cleanupAllWorkDirs() {
        for (File dir : pendingCleanup) {
            try {
                FileUtils.deleteDirectory(dir);
            } catch (IOException e) {
                logger.warning("Failed to clean up work directory: " + e.getMessage());
            }
        }
        pendingCleanup.clear();
    }

    public File mergeResourcePacks(List<File> inputPacks, String outputName) throws IOException {
        if (inputPacks.isEmpty()) {
            throw new IllegalArgumentException("No input packs provided");
        }

        // Calculate required space (rough estimate: sum of input sizes * 2 for safety)
        long requiredSpace = 0;
        for (File pack : inputPacks) {
            requiredSpace += pack.length() * 2;
        }

        // Create work directory in plugin's data folder instead of system temp
        File workDir = new File(plugin.getDataFolder(), "temp/merge_" + System.currentTimeMillis());
        workDir.mkdirs();
        pendingCleanup.add(workDir);

        try {
            // Check available space
            long availableSpace = workDir.getUsableSpace();
            if (availableSpace < requiredSpace) {
                throw new IOException("Insufficient disk space. Required: " + (requiredSpace / 1024 / 1024) + 
                                    "MB, Available: " + (availableSpace / 1024 / 1024) + "MB");
            }

            logger.info("Merging " + inputPacks.size() + " resource packs...");

            // Extract packs in parallel
            List<Future<File>> extractFutures = new ArrayList<>();
            for (File pack : inputPacks) {
                extractFutures.add(executor.submit(() -> extractPack(pack, workDir)));
            }

            // Wait for all extractions to complete
            List<File> extractedDirs = new ArrayList<>();
            for (Future<File> future : extractFutures) {
                try {
                    extractedDirs.add(future.get());
                } catch (Exception e) {
                    throw new IOException("Failed to extract pack: " + e.getMessage(), e);
                }
            }

            File outputDir = new File(workDir, "merged");
            outputDir.mkdirs();

            // Check space again after extraction
            availableSpace = workDir.getUsableSpace();
            if (availableSpace < requiredSpace / 2) {
                throw new IOException("Insufficient disk space after extraction. Available: " + 
                                    (availableSpace / 1024 / 1024) + "MB");
            }

            // Merge directories with progress tracking
            int totalDirs = extractedDirs.size();
            for (int i = 0; i < totalDirs; i++) {
                File sourceDir = extractedDirs.get(i);
                logger.info("Merging pack " + (i + 1) + "/" + totalDirs + "...");
                mergeDirectory(sourceDir, outputDir);
                
                // Update pack.mcmeta after merging
                createOrUpdateMcMeta(outputDir);
                
                // Cleanup extracted directory after merging to free space
                if (i < totalDirs - 1) { // Don't delete the last one yet
                    FileUtils.deleteDirectory(sourceDir);
                }
            }

            // Create output file
            File outputFile = new File(plugin.getDataFolder(), "packs" + File.separator + outputName);
            zipDirectory(outputDir, outputFile);
            logger.info("Resource packs merged successfully!");

            return outputFile;
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to merge resource packs: " + e.getMessage(), e);
        } finally {
            cleanupWorkDir(workDir);
            pendingCleanup.remove(workDir);
        }
    }

    private File extractPack(File pack, File workDir) throws IOException {
        if (!pack.getName().toLowerCase().endsWith(".zip")) {
            return pack;
        }

        File extractDir = new File(workDir, pack.getName().replace(".zip", ""));
        extractZipFast(pack, extractDir);
        return extractDir;
    }

    private void extractZipFast(File zipFile, File destDir) throws IOException {
        Map<String, Long> entrySizes = new HashMap<>();

        // First pass: calculate total size and create directory structure
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (!entry.isDirectory()) {
                    entrySizes.put(entry.getName(), entry.getSize());
                    new File(destDir, entry.getName()).getParentFile().mkdirs();
                }
            }
        }

        // Second pass: extract files with progress tracking
        try (ZipFile zip = new ZipFile(zipFile)) {
            for (Map.Entry<String, Long> entry : entrySizes.entrySet()) {
                String entryName = entry.getKey();
                ZipEntry zipEntry = zip.getEntry(entryName);
                File entryFile = new File(destDir, entryName);

                try (InputStream in = new BufferedInputStream(zip.getInputStream(zipEntry), BUFFER_SIZE);
                     OutputStream out = new BufferedOutputStream(new FileOutputStream(entryFile), BUFFER_SIZE)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int len;
                    while ((len = in.read(buffer)) > 0) {
                        out.write(buffer, 0, len);
                    }
                }
            }
        }
    }

    private void mergeDirectory(File sourceDir, File targetDir) throws IOException {
        if (!sourceDir.exists() || !sourceDir.isDirectory()) return;

        Path sourcePath = sourceDir.toPath();
        Path targetPath = targetDir.toPath();

        Files.walk(sourcePath)
            .parallel()
            .forEach(source -> {
                try {
                    Path target = targetPath.resolve(sourcePath.relativize(source));
                    if (Files.isDirectory(source)) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        if (source.toString().toLowerCase().endsWith(".json")) {
                            mergeJsonFiles(target.toFile(), source.toFile());
                        } else if (!Files.exists(target)) {
                            Files.copy(source, target);
                        }
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
    }

    private void zipDirectory(File sourceDir, File zipFile) throws IOException {
        Path sourcePath = sourceDir.toPath();
        
        // Create parent directories if they don't exist
        zipFile.getParentFile().mkdirs();
        
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile), BUFFER_SIZE))) {
            List<Path> files = Files.walk(sourcePath)
                .filter(path -> !Files.isDirectory(path))
                .collect(Collectors.toList());

            int totalFiles = files.size();
            int processed = 0;

            for (Path path : files) {
                String relativePath = sourcePath.relativize(path).toString().replace('\\', '/');
                zos.putNextEntry(new ZipEntry(relativePath));
                Files.copy(path, zos);
                zos.closeEntry();
                
                processed++;
                if (processed % 100 == 0 || processed == totalFiles) {
                    logger.info("Compressing: " + processed + "/" + totalFiles + " files...");
                }
            }
        }
    }

    private void mergeJsonFiles(File target, File source) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};
        
        Map<String, Object> sourceMap;
        Map<String, Object> targetMap;
        
        // Read source file with error handling
        try {
            sourceMap = mapper.readValue(source, typeRef);
        } catch (IOException e) {
            logger.warning("Failed to read source JSON file " + source.getName() + ": " + e.getMessage());
            throw e;
        }

        // Handle target file
        if (target.exists()) {
            try {
                targetMap = mapper.readValue(target, typeRef);
                deepMerge(targetMap, sourceMap);
            } catch (IOException e) {
                logger.warning("Failed to read target JSON file " + target.getName() + ": " + e.getMessage());
                // If target is corrupted, use source
                targetMap = sourceMap;
            }
        } else {
            targetMap = sourceMap;
        }

        // Ensure parent directory exists
        target.getParentFile().mkdirs();

        // Write with proper error handling and temp file
        File tempFile = new File(target.getParentFile(), target.getName() + ".tmp");
        try {
            ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
            writer.writeValue(tempFile, targetMap);
            
            // Atomic rename
            if (!tempFile.renameTo(target)) {
                // If rename fails, try copy and delete
                Files.copy(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                tempFile.delete();
            }
        } catch (IOException e) {
            tempFile.delete(); // Clean up temp file if write fails
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void deepMerge(Map<String, Object> target, Map<String, Object> source) {
        if (target == null || source == null) return;

        try {
            for (Map.Entry<String, Object> entry : source.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (value instanceof Map) {
                    Map<String, Object> targetChild = target.containsKey(key) ? 
                        (Map<String, Object>) target.get(key) : new HashMap<>();
                    deepMerge(targetChild, (Map<String, Object>) value);
                    target.put(key, targetChild);
                } else if (value instanceof List) {
                    List<Object> targetList = target.containsKey(key) ?
                        (List<Object>) target.get(key) : new ArrayList<>();
                    targetList.addAll((List<Object>) value);
                    // Remove duplicates while preserving order
                    target.put(key, new ArrayList<>(new LinkedHashSet<>(targetList)));
                } else {
                    target.put(key, value);
                }
            }
        } catch (Exception e) {
            logger.warning("Error during JSON merge: " + e.getMessage());
            // Continue with best effort merge
        }
    }

    private void createOrUpdateMcMeta(File packDir) throws IOException {
        File mcmetaFile = new File(packDir, "pack.mcmeta");
        ObjectMapper mapper = new ObjectMapper();
        TypeReference<Map<String, Object>> typeRef = new TypeReference<>() {};
        
        Map<String, Object> mcmeta;
        Map<String, Object> pack;

        if (mcmetaFile.exists()) {
            mcmeta = mapper.readValue(mcmetaFile, typeRef);
            pack = mcmeta.get("pack") != null ? mapper.convertValue(mcmeta.get("pack"), typeRef) : new HashMap<>();
        } else {
            mcmeta = new HashMap<>();
            pack = new HashMap<>();
            mcmeta.put("pack", pack);
        }

        pack.put("pack_format", 15); // Current pack format as of 1.20
        pack.put("description", "Merged Resource Pack");

        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
        writer.writeValue(mcmetaFile, mcmeta);
    }

    private void cleanupWorkDir(File workDir) {
        CompletableFuture.runAsync(() -> {
            try {
                if (workDir != null && workDir.exists()) {
                    FileUtils.deleteDirectory(workDir);
                }
            } catch (IOException e) {
                logger.warning("Failed to clean up temporary directory: " + e.getMessage());
            }
        }, executor);
    }
}