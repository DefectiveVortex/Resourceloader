package org.vortex.resourceloader;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import org.apache.commons.io.FileUtils;
import org.vortex.resourceloader.util.FileUtil;
import org.vortex.resourceloader.util.PackFormats;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.nio.file.*;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

public class ResourcePackMerger {
    private final Resourceloader plugin;
    private final Logger logger;
    private static final int BUFFER_SIZE = 32768;
    private static final int THREAD_POOL_SIZE = Math.min(4, Runtime.getRuntime().availableProcessors());
    private static final String MCMETA = "pack.mcmeta";

    // JSON files the game itself combines across stacked packs instead of letting the top pack replace them.
    // A merged pack has to reproduce that, otherwise lower packs silently lose their entries.
    private static final Pattern LANG_FILE = Pattern.compile("assets/[^/]+/lang/[^/]+\\.json");
    private static final Pattern ATLAS_FILE = Pattern.compile("assets/[^/]+/atlases/[^/]+\\.json");
    private static final Pattern SOUNDS_FILE = Pattern.compile("assets/[^/]+/sounds\\.json");

    private final ExecutorService executor;
    private final Set<File> pendingCleanup;
    private final ObjectMapper mapper = new ObjectMapper();

    public ResourcePackMerger(Resourceloader plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.executor = Executors.newFixedThreadPool(THREAD_POOL_SIZE, r -> {
            Thread t = new Thread(r, "Resourceloader-Merge");
            t.setDaemon(true);
            return t;
        });
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
            cleanup(dir);
        }
    }

    /**
     * Merges packs into {@code outputName} in the packs directory.
     * Packs are listed lowest priority first: a file in a later pack overrides the same file in an earlier one.
     */
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

            // Extract packs in parallel, each into its own numbered directory
            List<Future<File>> extractFutures = new ArrayList<>();
            for (int i = 0; i < inputPacks.size(); i++) {
                File pack = inputPacks.get(i);
                File extractDir = new File(workDir, "input_" + i);
                extractFutures.add(executor.submit(() -> extractPack(pack, extractDir)));
            }

            // Wait for all extractions to complete
            List<File> extractedDirs = new ArrayList<>();
            for (Future<File> future : extractFutures) {
                try {
                    extractedDirs.add(future.get());
                } catch (ExecutionException e) {
                    throw new IOException("Failed to extract pack: " + e.getCause().getMessage(), e.getCause());
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while extracting packs", e);
                }
            }

            // Merge directories in order (last pack has highest priority)
            File outputDir = new File(workDir, "merged");
            outputDir.mkdirs();

            List<Map<String, Object>> metas = new ArrayList<>();
            for (File sourceDir : extractedDirs) {
                Map<String, Object> meta = readJsonFile(new File(sourceDir, MCMETA));
                if (meta != null) {
                    metas.add(meta);
                }
                mergeDirectory(sourceDir, outputDir);

                // Cleanup extracted directory after merging to free space
                FileUtils.deleteDirectory(sourceDir);
            }

            // pack.mcmeta has to be final before the directory is zipped
            writePackMeta(outputDir, metas);

            // Create output file
            File tempOutputFile = new File(workDir, "output.zip.tmp");
            zipDirectory(outputDir, tempOutputFile);

            // Validate the generated zip file
            try {
                FileUtil.validateZipFile(tempOutputFile);
            } catch (IOException e) {
                throw new IOException("Generated merged pack is invalid: " + e.getMessage(), e);
            }

            // Move to final location atomically
            File finalOutputFile = new File(plugin.getPackManager().getResolvedResourcePackDirectory(), outputName);
            finalOutputFile.getParentFile().mkdirs();

            Files.move(tempOutputFile.toPath(), finalOutputFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

            logger.info("Resource packs merged successfully!");
            return finalOutputFile;

        } catch (Exception e) {
            throw new IOException("Failed to merge resource packs: " + e.getMessage(), e);
        } finally {
            cleanup(workDir);
        }
    }

    private File extractPack(File pack, File extractDir) throws IOException {
        Path root = extractDir.getCanonicalFile().toPath();
        try (ZipFile zipFile = new ZipFile(pack)) {
            Enumeration<? extends ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                ZipEntry entry = entries.nextElement();
                if (entry.getName().startsWith("__MACOSX/")) {
                    continue;
                }

                // Refuse entries such as "../../plugins/x.jar" that would land outside the work directory
                Path target = root.resolve(entry.getName()).normalize();
                if (!target.startsWith(root)) {
                    logger.warning("Skipping unsafe entry '" + entry.getName() + "' in " + pack.getName());
                    continue;
                }
                File entryFile = target.toFile();

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

    private void mergeDirectory(File sourceDir, File targetDir) throws IOException {
        if (!sourceDir.exists()) {
            return;
        }

        List<Path> files;
        try (Stream<Path> walk = Files.walk(sourceDir.toPath())) {
            files = walk.filter(Files::isRegularFile).collect(Collectors.toList());
        }

        for (Path sourcePath : files) {
            String relative = sourceDir.toPath().relativize(sourcePath).toString().replace('\\', '/');
            if (relative.equals(MCMETA)) {
                continue; // combined separately in writePackMeta
            }
            File targetFile = new File(targetDir, relative);
            try {
                if (targetFile.exists() && isCombinedJson(relative)) {
                    mergeJsonFile(relative, targetFile, sourcePath.toFile());
                } else {
                    // Same rule as the game: the higher pack's file replaces the lower one
                    FileUtils.copyFile(sourcePath.toFile(), targetFile);
                }
            } catch (IOException e) {
                logger.warning("Failed to merge file " + relative + ": " + e.getMessage());
            }
        }
    }

    private boolean isCombinedJson(String relative) {
        return LANG_FILE.matcher(relative).matches()
            || ATLAS_FILE.matcher(relative).matches()
            || SOUNDS_FILE.matcher(relative).matches();
    }

    @SuppressWarnings("unchecked")
    private void mergeJsonFile(String relative, File targetFile, File sourceFile) throws IOException {
        Map<String, Object> higher = readJsonFile(sourceFile);
        Map<String, Object> lower = readJsonFile(targetFile);
        if (higher == null || lower == null) {
            // One side is not a JSON object; fall back to plain replacement
            FileUtils.copyFile(sourceFile, targetFile);
            return;
        }

        if (LANG_FILE.matcher(relative).matches()) {
            lower.putAll(higher);
        } else if (ATLAS_FILE.matcher(relative).matches()) {
            List<Object> sources = new ArrayList<>(asList(lower.get("sources")));
            for (Object source : asList(higher.get("sources"))) {
                if (!sources.contains(source)) {
                    sources.add(source);
                }
            }
            lower.putAll(higher);
            lower.put("sources", sources);
        } else {
            // sounds.json: events add their sounds to lower packs' events unless they set "replace"
            for (Map.Entry<String, Object> event : higher.entrySet()) {
                Object existing = lower.get(event.getKey());
                if (!(event.getValue() instanceof Map) || !(existing instanceof Map)
                        || Boolean.TRUE.equals(((Map<?, ?>) event.getValue()).get("replace"))) {
                    lower.put(event.getKey(), event.getValue());
                    continue;
                }
                Map<?, ?> higherEvent = (Map<?, ?>) event.getValue();
                Map<?, ?> lowerEvent = (Map<?, ?>) existing;
                Map<String, Object> combined = new LinkedHashMap<>((Map<String, Object>) lowerEvent);
                combined.putAll((Map<String, Object>) higherEvent);
                List<Object> sounds = new ArrayList<>(asList(lowerEvent.get("sounds")));
                sounds.addAll(asList(higherEvent.get("sounds")));
                combined.put("sounds", sounds);
                lower.put(event.getKey(), combined);
            }
        }

        writeJsonFile(targetFile, lower);
    }

    private static List<?> asList(Object value) {
        return value instanceof List ? (List<?>) value : Collections.emptyList();
    }

    /**
     * Writes the merged pack.mcmeta: the top pack's metadata, with a format range covering every input pack
     * and the server's own version, and the overlays/language/filter sections of all inputs combined.
     */
    @SuppressWarnings("unchecked")
    private void writePackMeta(File packDir, List<Map<String, Object>> metas) throws IOException {
        Map<String, Object> mcmeta = metas.isEmpty() ? new LinkedHashMap<>() : new LinkedHashMap<>(metas.get(metas.size() - 1));

        Map<String, Object> pack = mcmeta.get("pack") instanceof Map
            ? new LinkedHashMap<>((Map<String, Object>) mcmeta.get("pack")) : new LinkedHashMap<>();
        mcmeta.put("pack", pack);

        PackFormats.Range range = null;
        List<Object> overlays = new ArrayList<>();
        Set<Object> overlayDirs = new HashSet<>();
        Map<String, Object> languages = new LinkedHashMap<>();
        List<Object> filters = new ArrayList<>();
        for (Map<String, Object> meta : metas) {
            Object section = meta.get("pack");
            if (section instanceof Map) {
                PackFormats.Range declared = PackFormats.readRange((Map<String, Object>) section);
                if (declared != null) {
                    range = range == null ? declared : range.span(declared);
                }
            }
            if (meta.get("overlays") instanceof Map) {
                for (Object entry : asList(((Map<?, ?>) meta.get("overlays")).get("entries"))) {
                    Object dir = entry instanceof Map ? ((Map<?, ?>) entry).get("directory") : entry;
                    if (overlayDirs.add(dir)) {
                        overlays.add(entry);
                    }
                }
            }
            if (meta.get("language") instanceof Map) {
                languages.putAll((Map<String, Object>) meta.get("language"));
            }
            if (meta.get("filter") instanceof Map) {
                for (Object block : asList(((Map<?, ?>) meta.get("filter")).get("block"))) {
                    if (!filters.contains(block)) {
                        filters.add(block);
                    }
                }
            }
        }
        if (!overlays.isEmpty()) {
            mcmeta.put("overlays", Collections.singletonMap("entries", overlays));
        }
        if (!languages.isEmpty()) {
            mcmeta.put("language", languages);
        }
        if (!filters.isEmpty()) {
            mcmeta.put("filter", Collections.singletonMap("block", filters));
        }

        PackFormats.Version server = PackFormats.currentResourceFormat();
        if (server != null) {
            PackFormats.Range serverRange = new PackFormats.Range(server, server);
            range = range == null ? serverRange : range.span(serverRange);
        }
        if (range == null) {
            range = new PackFormats.Range(new PackFormats.Version(34, 0), new PackFormats.Version(34, 0));
            logger.warning("Could not determine a pack format for the merged pack; defaulting to 34 (1.21)");
        }
        PackFormats.writeRange(pack, range, server);
        pack.put("description", "Merged Resource Pack");

        logger.info("Merged pack declares formats " + range + (server != null ? " (server uses " + server + ")" : ""));
        writeJsonFile(new File(packDir, MCMETA), mcmeta);
    }

    private Map<String, Object> readJsonFile(File file) {
        if (!file.isFile()) {
            return null;
        }
        try {
            return mapper.readValue(file, new TypeReference<LinkedHashMap<String, Object>>() {
            });
        } catch (IOException e) {
            logger.warning("Failed to read JSON file " + file.getName() + ": " + e.getMessage());
            return null;
        }
    }

    private void writeJsonFile(File file, Map<String, Object> content) throws IOException {
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
        List<Path> files;
        try (Stream<Path> walk = Files.walk(sourceDir.toPath())) {
            files = walk.filter(path -> !Files.isDirectory(path)).sorted().collect(Collectors.toList());
        }
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFile))) {
            for (Path path : files) {
                zos.putNextEntry(new ZipEntry(sourceDir.toPath().relativize(path).toString().replace('\\', '/')));
                Files.copy(path, zos);
                zos.closeEntry();
            }
        }
    }

    private void cleanup(File workDir) {
        try {
            if (workDir != null && workDir.exists()) {
                FileUtils.deleteDirectory(workDir);
            }
        } catch (IOException e) {
            logger.warning("Failed to clean up temporary directory: " + e.getMessage());
        } finally {
            pendingCleanup.remove(workDir);
        }
    }
}
