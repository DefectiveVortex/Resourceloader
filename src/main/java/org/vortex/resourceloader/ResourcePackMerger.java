package org.vortex.resourceloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.io.FileUtils;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ResourcePackMerger {
    private final Resourceloader plugin;
    private final Logger logger;
    private static final int BUFFER_SIZE = 32768;
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

        // Create work directory
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

            // Merge directories in order (last pack has highest priority)
            File outputDir = new File(workDir, "merged");
            outputDir.mkdirs();

            for (int i = 0; i < extractedDirs.size(); i++) {
                File sourceDir = extractedDirs.get(i);
                mergeDirectory(sourceDir, outputDir, i == extractedDirs.size() - 1);
                
                // Cleanup extracted directory after merging to free space
                if (i < extractedDirs.size() - 1) {
                    FileUtils.deleteDirectory(sourceDir);
                }
            }

            // Create output file
            File outputFile = new File(plugin.getDataFolder(), "packs/" + outputName);
            zipDirectory(outputDir, outputFile);

            // Update pack.mcmeta with latest format
            updatePackMeta(outputDir);

            logger.info("Resource packs merged successfully!");
            return outputFile;

        } catch (Exception e) {
            throw new IOException("Failed to merge resource packs: " + e.getMessage(), e);
        } finally {
            cleanup(workDir);
        }
    }

    private File extractPack(File pack, File workDir) throws IOException {
        if (!pack.getName().toLowerCase().endsWith(".zip")) {
            return pack;
        }

        File extractDir = new File(workDir, pack.getName().replace(".zip", ""));
        try (ZipFile zipFile = new ZipFile(pack)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryFile = new File(extractDir, entry.getName());

                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                    continue;
                }

                entryFile.getParentFile().mkdirs();
                try (InputStream in = zipFile.getInputStream(entry);
                     OutputStream out = new FileOutputStream(entryFile)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }
            }
        }
        return extractDir;
    }

    private void mergeDirectory(File sourceDir, File targetDir, boolean isLastPack) throws IOException {
        if (!sourceDir.exists()) {
            return;
        }

        Files.walk(sourceDir.toPath())
            .filter(Files::isRegularFile)
            .forEach(sourcePath -> {
                try {
                    Path relativePath = sourceDir.toPath().relativize(sourcePath);
                    File targetFile = new File(targetDir, relativePath.toString());
                    
                    if (sourcePath.toString().endsWith(".json")) {
                        mergeJsonFile(targetFile, sourcePath.toFile(), isLastPack);
                    } else {
                        // For non-JSON files, newer pack always takes priority
                        if (isLastPack || !targetFile.exists()) {
                            FileUtils.copyFile(sourcePath.toFile(), targetFile);
                        }
                    }
                } catch (IOException e) {
                    logger.warning("Failed to merge file " + sourcePath + ": " + e.getMessage());
                }
            });
    }

    private void mergeJsonFile(File targetFile, File sourceFile, boolean isLastPack) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        
        // Read source file
        Map<String, Object> sourceMap = readJsonFile(sourceFile, mapper);
        if (sourceMap == null) return;

        // If target doesn't exist or this is the last pack, just copy/overwrite
        if (!targetFile.exists() || isLastPack) {
            targetFile.getParentFile().mkdirs();
            FileUtils.copyFile(sourceFile, targetFile);
            return;
        }

        // Read target file
        Map<String, Object> targetMap = readJsonFile(targetFile, mapper);
        if (targetMap == null) {
            FileUtils.copyFile(sourceFile, targetFile);
            return;
        }

        // Special handling for different types of JSON files
        if (isModelFile(targetFile)) {
            mergeModelFile(targetMap, sourceMap);
        } else if (isLanguageFile(targetFile)) {
            // Language files just need simple merging with override
            targetMap.putAll(sourceMap);
        } else {
            // Default deep merge for other JSON files
            deepMerge(targetMap, sourceMap);
        }

        // Write merged result
        writeJsonFile(targetFile, targetMap, mapper);
    }

    private boolean isModelFile(File file) {
        String path = file.getPath().toLowerCase();
        return path.contains("models") || path.contains("blockstates") || path.endsWith(".model.json");
    }

    private boolean isLanguageFile(File file) {
        return file.getPath().toLowerCase().contains("lang");
    }

    @SuppressWarnings("unchecked")
    private void mergeModelFile(Map<String, Object> target, Map<String, Object> source) {
        // Handle parent field - newer pack's parent takes priority
        if (source.containsKey("parent")) {
            target.put("parent", source.get("parent"));
        }

        // Merge textures
        if (source.containsKey("textures")) {
            Map<String, Object> targetTextures = (Map<String, Object>) target.computeIfAbsent("textures", k -> new HashMap<>());
            targetTextures.putAll((Map<String, Object>) source.get("textures"));
        }

        // Merge elements - preserve both sets
        if (source.containsKey("elements")) {
            List<Object> targetElements = (List<Object>) target.computeIfAbsent("elements", k -> new ArrayList<>());
            targetElements.addAll((List<Object>) source.get("elements"));
        }

        // Handle display settings
        if (source.containsKey("display")) {
            target.put("display", source.get("display"));
        }

        // Merge overrides with duplicate checking
        if (source.containsKey("overrides")) {
            List<Map<String, Object>> targetOverrides = (List<Map<String, Object>>) target.computeIfAbsent("overrides", k -> new ArrayList<>());
            List<Map<String, Object>> sourceOverrides = (List<Map<String, Object>>) source.get("overrides");
            
            Set<String> existingPredicates = new HashSet<>();
            targetOverrides.forEach(override -> existingPredicates.add(override.toString()));
            
            sourceOverrides.forEach(override -> {
                if (!existingPredicates.contains(override.toString())) {
                    targetOverrides.add(override);
                }
            });
        }
    }

    @SuppressWarnings("unchecked")
    private void deepMerge(Map<String, Object> target, Map<String, Object> source) {
        for (String key : source.keySet()) {
            Object sourceValue = source.get(key);
            if (sourceValue instanceof Map) {
                Map<String, Object> targetMap = (Map<String, Object>) target.computeIfAbsent(key, k -> new HashMap<>());
                deepMerge(targetMap, (Map<String, Object>) sourceValue);
            } else {
                target.put(key, sourceValue);
            }
        }
    }

    private Map<String, Object> readJsonFile(File file, ObjectMapper mapper) {
        try {
            return mapper.readValue(file, new TypeReference<Map<String, Object>>() {});
        } catch (IOException e) {
            logger.warning("Failed to read JSON file " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private void writeJsonFile(File file, Map<String, Object> content, ObjectMapper mapper) throws IOException {
        File tempFile = new File(file.getParentFile(), file.getName() + ".tmp");
        try {
            file.getParentFile().mkdirs();
            mapper.writerWithDefaultPrettyPrinter().writeValue(tempFile, content);
            Files.move(tempFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            tempFile.delete();
            throw e;
        }
    }

    private void zipDirectory(File sourceDir, File zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            Files.walk(sourceDir.toPath())
                .filter(path -> !Files.isDirectory(path))
                .forEach(path -> {
                    ZipEntry zipEntry = new ZipEntry(sourceDir.toPath().relativize(path).toString().replace('\\', '/'));
                    try {
                        zos.putNextEntry(zipEntry);
                        Files.copy(path, zos);
                        zos.closeEntry();
                    } catch (IOException e) {
                        logger.warning("Failed to add file to zip: " + path);
                    }
                });
        }
    }

    private int getPackFormat() {
        String version = plugin.getServer().getBukkitVersion();
        
        // Extract the main version number (e.g., "1.20.4-R0.1-SNAPSHOT" -> "1.20.4")
        version = version.split("-")[0];
        
        // Map Minecraft versions to pack_format numbers
        return switch (version) {
            case "1.20.3", "1.20.4" -> 18;  // 1.20.3 - 1.20.4
            case "1.20.2" -> 17;            // 1.20.2
            case "1.20", "1.20.1" -> 15;    // 1.20 - 1.20.1
            case "1.19.4" -> 13;            // 1.19.4
            case "1.19.3" -> 12;            // 1.19.3
            case "1.19.1", "1.19.2" -> 9;   // 1.19 - 1.19.2
            case "1.18.2" -> 8;             // 1.18.2
            case "1.18", "1.18.1" -> 7;     // 1.18 - 1.18.1
            case "1.17", "1.17.1" -> 7;     // 1.17 - 1.17.1
            case "1.16.2", "1.16.3", "1.16.4", "1.16.5" -> 6;  // 1.16.2 - 1.16.5
            case "1.16", "1.16.1" -> 5;     // 1.16 - 1.16.1
            case "1.15", "1.15.1", "1.15.2" -> 5;  // 1.15.x
            case "1.14", "1.14.1", "1.14.2", "1.14.3", "1.14.4" -> 4;  // 1.14.x
            case "1.13", "1.13.1", "1.13.2" -> 4;  // 1.13.x
            case "1.21", "1.21.1", "1.21.2", "1.21.3", "1.21.4", "1.21.5", "1.21.6", "1.21.7" -> 22; // Future versions
            default -> {
                // For unknown versions, try to make an educated guess
                // This helps with future versions until we update the mapping
                String[] parts = version.split("\\.");
                if (parts.length >= 2) {
                    int major = Integer.parseInt(parts[1]);
                    if (major >= 21) { // Future versions
                        yield 22;
                    } else if (major >= 20) {
                        yield 18;
                    } else if (major >= 19) {
                        yield 13;
                    }
                }
                // Default to latest known format if we can't determine version
                yield 22;
            }
        };
    }

    private void updatePackMeta(File packDir) throws IOException {
        File mcmetaFile = new File(packDir, "pack.mcmeta");
        ObjectMapper mapper = new ObjectMapper();
        
        Map<String, Object> mcmeta;
        if (mcmetaFile.exists()) {
            mcmeta = readJsonFile(mcmetaFile, mapper);
            if (mcmeta == null) {
                mcmeta = new HashMap<>();
            }
        } else {
            mcmeta = new HashMap<>();
        }

        Object packObj = mcmeta.computeIfAbsent("pack", k -> new HashMap<>());
        Map<String, Object> pack;
        if (packObj instanceof Map<?, ?> mapObj) {
            @SuppressWarnings("unchecked")
            Map<String, Object> safePack = (Map<String, Object>) mapObj;
            pack = safePack;
        } else {
            pack = new HashMap<>();
            mcmeta.put("pack", pack);
        }
        
        // Use the server's version to determine pack format
        int packFormat = getPackFormat();
        pack.put("pack_format", packFormat);
        pack.put("description", "Merged Resource Pack (Format: " + packFormat + ")");

        logger.info("Setting merged pack format to " + packFormat + " for server version " + 
            plugin.getServer().getBukkitVersion());

        writeJsonFile(mcmetaFile, mcmeta, mapper);
    }

    private void cleanup(File workDir) {
        CompletableFuture.runAsync(() -> {
            try {
                if (workDir != null && workDir.exists()) {
                    FileUtils.deleteDirectory(workDir);
                }
            } catch (IOException e) {
                logger.warning("Failed to clean up temporary directory: " + e.getMessage());
            } finally {
                pendingCleanup.remove(workDir);
            }
        }, executor);
    }
}