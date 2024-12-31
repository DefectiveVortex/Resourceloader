package org.vortex.resourceloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import org.apache.commons.io.FileUtils;
import java.io.*;
import java.util.*;
import java.nio.file.*;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.nio.channels.FileChannel;

public class ResourcePackMerger {
    private final Resourceloader plugin;
    private final Logger logger;
    private static final int BUFFER_SIZE = 8192 * 4; // 32KB buffer

    public ResourcePackMerger(Resourceloader plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
    }

    public File mergeResourcePacks(List<File> inputPacks, String outputName) throws IOException {
        File workDir = Files.createTempDirectory("resourcepack_merger_").toFile();

        try {
            List<File> extractedDirs = extractPacks(inputPacks, workDir);
            File outputDir = new File(workDir, "merged");
            outputDir.mkdirs();

            mergeDirectories(extractedDirs.toArray(new File[0]), outputDir);
            createOrUpdateMcMeta(outputDir);

            File outputFile = new File(plugin.getDataFolder(), "packs" + File.separator + outputName);
            zipDirectory(outputDir, outputFile);

            return outputFile;
        } finally {
            // Clean up in a separate thread to not block
            new Thread(() -> {
                try {
                    FileUtils.deleteDirectory(workDir);
                } catch (IOException e) {
                    logger.warning("Failed to clean up temporary directory: " + e.getMessage());
                }
            }).start();
        }
    }

    private List<File> extractPacks(List<File> inputPacks, File workDir) throws IOException {
        List<File> extractedDirs = new ArrayList<>();
        for (File pack : inputPacks) {
            if (pack.getName().toLowerCase().endsWith(".zip")) {
                File extractDir = new File(workDir, pack.getName().replace(".zip", ""));
                extractZipFast(pack, extractDir);
                extractedDirs.add(extractDir);
            } else if (pack.isDirectory()) {
                extractedDirs.add(pack);
            }
        }
        return extractedDirs;
    }

    private void extractZipFast(File zipFile, File destDir) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();

            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryFile = new File(destDir, entry.getName());

                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                    continue;
                }

                entryFile.getParentFile().mkdirs();
                try (InputStream in = new BufferedInputStream(zip.getInputStream(entry), BUFFER_SIZE);
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

    private void zipDirectory(File sourceDir, File zipFile) throws IOException {
        Path sourcePath = sourceDir.toPath();
        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipFile), BUFFER_SIZE))) {
            Files.walk(sourcePath)
                    .filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        try {
                            String relativePath = sourcePath.relativize(path).toString().replace('\\', '/');
                            zos.putNextEntry(new ZipEntry(relativePath));
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private void mergeDirectories(File[] sourceDirs, File targetDir) throws IOException {
        for (File sourceDir : sourceDirs) {
            if (!sourceDir.exists() || !sourceDir.isDirectory()) continue;

            Files.walk(sourceDir.toPath())
                    .forEach(sourcePath -> {
                        try {
                            Path targetPath = targetDir.toPath().resolve(sourceDir.toPath().relativize(sourcePath));
                            if (Files.isDirectory(sourcePath)) {
                                Files.createDirectories(targetPath);
                            } else {
                                if (!Files.exists(targetPath)) {
                                    Files.createDirectories(targetPath.getParent());
                                    Files.copy(sourcePath, targetPath);
                                } else if (sourcePath.toString().toLowerCase().endsWith(".json")) {
                                    mergeJsonFiles(targetPath.toFile(), sourcePath.toFile());
                                }
                            }
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private void mergeJsonFiles(File target, File source) throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> targetMap = mapper.readValue(target, Map.class);
        Map<String, Object> sourceMap = mapper.readValue(source, Map.class);

        // Deep merge the JSON objects
        deepMerge(targetMap, sourceMap);

        // Write the merged result back
        ObjectWriter writer = mapper.writerWithDefaultPrettyPrinter();
        writer.writeValue(target, targetMap);
    }

    @SuppressWarnings("unchecked")
    private void deepMerge(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value instanceof Map) {
                Map<String, Object> targetChild = (Map<String, Object>) target.getOrDefault(key, new HashMap<>());
                deepMerge(targetChild, (Map<String, Object>) value);
                target.put(key, targetChild);
            } else if (value instanceof List) {
                List<Object> targetList = (List<Object>) target.getOrDefault(key, new ArrayList<>());
                targetList.addAll((List<Object>) value);
                target.put(key, new ArrayList<>(new LinkedHashSet<>(targetList))); // Remove duplicates
            } else {
                target.put(key, value);
            }
        }
    }

    private void extractZip(File zipFile, File destDir) throws IOException {
        try (ZipFile zip = new ZipFile(zipFile)) {
            Enumeration<? extends ZipEntry> entries = zip.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                File entryFile = new File(destDir, entry.getName());

                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                } else {
                    entryFile.getParentFile().mkdirs();
                    try (InputStream in = zip.getInputStream(entry);
                         FileOutputStream out = new FileOutputStream(entryFile)) {
                        byte[] buffer = new byte[1024];
                        int len;
                        while ((len = in.read(buffer)) > 0) {
                            out.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    private void createOrUpdateMcMeta(File packDir) throws IOException {
        File mcmetaFile = new File(packDir, "pack.mcmeta");
        Map<String, Object> mcmeta;
        Map<String, Object> pack;

        if (mcmetaFile.exists()) {
            ObjectMapper mapper = new ObjectMapper();
            mcmeta = mapper.readValue(mcmetaFile, Map.class);
            pack = (Map<String, Object>) mcmeta.get("pack");
        } else {
            mcmeta = new HashMap<>();
            pack = new HashMap<>();
            mcmeta.put("pack", pack);
        }

        // Set pack format for latest version (you may want to make this configurable)
        pack.put("pack_format", 15); // Current pack format as of 1.20
        pack.put("description", "Merged Resource Pack");

        ObjectWriter writer = new ObjectMapper().writerWithDefaultPrettyPrinter();
        writer.writeValue(mcmetaFile, mcmeta);
    }
}