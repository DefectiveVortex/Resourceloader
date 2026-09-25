package org.vortex.resourceloader.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.vortex.resourceloader.Resourceloader;
import org.vortex.resourceloader.util.PackFormats;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PackValidator {
    private final ObjectMapper mapper;
    private final List<ValidationIssue> issues;

    public PackValidator(Resourceloader plugin) {
        this.mapper = new ObjectMapper();
        this.issues = new ArrayList<>();
    }

    public ValidationResult validate(File packFile) {
        issues.clear();

        try {
            if (!packFile.exists()) {
                addIssue("Pack file does not exist", true);
                return result();
            }

            if (!packFile.getName().toLowerCase().endsWith(".zip")) {
                addIssue("Pack file must be a ZIP file", true);
                return result();
            }

            try (ZipFile zip = new ZipFile(packFile)) {
                // Check pack.mcmeta
                ZipEntry mcmetaEntry = zip.getEntry("pack.mcmeta");
                if (mcmetaEntry == null) {
                    addIssue("Missing pack.mcmeta file", true);
                } else {
                    validateMcMeta(zip, mcmetaEntry);
                }

                // Check assets directory
                boolean hasAssets = false;
                Enumeration<? extends ZipEntry> entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().startsWith("assets/")) {
                        hasAssets = true;
                        break;
                    }
                }
                if (!hasAssets) {
                    addIssue("Missing assets directory", true);
                }

                // Validate JSON files
                Map<String, Set<String>> textureReferences = new HashMap<>();
                entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    if (entry.getName().endsWith(".json")) {
                        validateJsonFile(zip, entry, textureReferences);
                    }
                }

                // Validate texture references
                validateTextureReferences(zip, textureReferences);

                // Check for unnecessary files
                entries = zip.entries();
                while (entries.hasMoreElements()) {
                    ZipEntry entry = entries.nextElement();
                    String name = entry.getName().toLowerCase();
                    if (name.contains("__macosx") || name.contains(".ds_store")) {
                        addIssue("Contains unnecessary system files: " + entry.getName(), false);
                    }
                }

            } catch (IOException e) {
                addIssue("Failed to read ZIP file: " + e.getMessage(), true);
            }
        } catch (Exception e) {
            addIssue("Unexpected error during validation: " + e.getMessage(), true);
        }

        return result();
    }

    private ValidationResult result() {
        boolean critical = issues.stream().anyMatch(ValidationIssue::isCritical);
        return new ValidationResult(!critical, new ArrayList<>(issues));
    }

    @SuppressWarnings("unchecked")
    private void validateMcMeta(ZipFile zip, ZipEntry entry) {
        try {
            Map<String, Object> mcmeta = mapper.readValue(zip.getInputStream(entry), Map.class);
            if (!(mcmeta.get("pack") instanceof Map<?, ?> packSection)) {
                addIssue("pack.mcmeta is missing 'pack' section", true);
                return;
            }

            Map<String, Object> pack = (Map<String, Object>) packSection;
            // The same checks the game runs; a pack failing them does not load at all
            for (String problem : PackFormats.findRejections(pack)) {
                addIssue(problem, true);
            }
            if (!pack.containsKey("description")) {
                addIssue("pack.mcmeta is missing 'description'", false);
            }

            PackFormats.Range range = PackFormats.readRange(pack);
            PackFormats.Version server = PackFormats.currentResourceFormat();
            if (range != null && server != null && !range.contains(server)) {
                addIssue("Pack declares format " + range + " but this server's version uses " + server
                    + "; players will be warned it was made for " + (range.max().compareTo(server) < 0
                    ? "an older" : "a newer") + " version", false);
            }
        } catch (IOException e) {
            addIssue("Invalid pack.mcmeta JSON: " + e.getMessage(), true);
        }
    }

    private void validateJsonFile(ZipFile zip, ZipEntry entry, Map<String, Set<String>> textureReferences) {
        try {
            JsonNode json = mapper.readTree(zip.getInputStream(entry));

            // Collect texture references from models
            if (entry.getName().contains("/models/") && json.path("textures").isObject()) {
                Set<String> textures = new HashSet<>();
                json.get("textures").elements().forEachRemaining(value -> {
                    // A texture is "ns:path" or, since 1.21.6, {"sprite": "ns:path", ...}
                    String tex = value.isTextual() ? value.asText() : value.path("sprite").asText(null);
                    if (tex != null && !tex.startsWith("#")) {
                        textures.add(tex);
                    }
                });
                if (!textures.isEmpty()) {
                    textureReferences.put(entry.getName(), textures);
                }
            }
        } catch (IOException e) {
            addIssue("Invalid JSON in " + entry.getName() + ": " + e.getMessage(), false);
        }
    }

    private void validateTextureReferences(ZipFile zip, Map<String, Set<String>> textureReferences) {
        textureReferences.forEach((model, textures) -> {
            textures.forEach(texture -> {
                int colon = texture.indexOf(':');
                String namespace = colon >= 0 ? texture.substring(0, colon) : "minecraft";
                String path = colon >= 0 ? texture.substring(colon + 1) : texture;
                // Vanilla textures come from the game itself, so only custom namespaces can be missing
                if (namespace.equals("minecraft")) {
                    return;
                }
                String texturePath = "assets/" + namespace + "/textures/" + path + ".png";
                if (zip.getEntry(texturePath) == null) {
                    addIssue("Missing texture '" + texture + "' referenced in " + model, false);
                }
            });
        });
    }

    private void addIssue(String message, boolean critical) {
        issues.add(new ValidationIssue(message, critical));
    }

    public record ValidationResult(boolean isValid, List<ValidationIssue> issues) {
        public List<String> getFormattedIssues() {
            List<String> formatted = new ArrayList<>();
            formatted.add("Validation Results:");

            List<ValidationIssue> criticalIssues = issues.stream()
                .filter(ValidationIssue::isCritical)
                .toList();

            List<ValidationIssue> warnings = issues.stream()
                .filter(i -> !i.isCritical())
                .toList();

            if (!criticalIssues.isEmpty()) {
                formatted.add("Critical Issues:");
                criticalIssues.forEach(i -> formatted.add("- " + i.message()));
            }

            if (!warnings.isEmpty()) {
                formatted.add("Warnings:");
                warnings.forEach(i -> formatted.add("- " + i.message()));
            }

            if (issues.isEmpty()) {
                formatted.add("No issues found!");
            }

            return formatted;
        }
    }

    public record ValidationIssue(String message, boolean isCritical) {}
}
