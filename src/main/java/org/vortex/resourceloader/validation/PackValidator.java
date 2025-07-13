package org.vortex.resourceloader.validation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.vortex.resourceloader.Resourceloader;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class PackValidator {
    private final Resourceloader plugin;
    private final Logger logger;
    private final ObjectMapper mapper;
    private final List<ValidationIssue> issues;

    public PackValidator(Resourceloader plugin) {
        this.plugin = plugin;
        this.logger = plugin.getLogger();
        this.mapper = new ObjectMapper();
        this.issues = new ArrayList<>();
    }

    public ValidationResult validate(File packFile) {
        issues.clear();
        boolean isCritical = false;

        try {
            if (!packFile.exists()) {
                addIssue("Pack file does not exist", true);
                return new ValidationResult(false, issues);
            }

            if (!packFile.getName().toLowerCase().endsWith(".zip")) {
                addIssue("Pack file must be a ZIP file", true);
                return new ValidationResult(false, issues);
            }

            try (ZipFile zip = new ZipFile(packFile)) {
                // Check pack.mcmeta
                ZipEntry mcmetaEntry = zip.getEntry("pack.mcmeta");
                if (mcmetaEntry == null) {
                    addIssue("Missing pack.mcmeta file", true);
                    isCritical = true;
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
                    isCritical = true;
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
                isCritical = true;
            }
        } catch (Exception e) {
            addIssue("Unexpected error during validation: " + e.getMessage(), true);
            isCritical = true;
        }

        return new ValidationResult(!isCritical, issues);
    }

    private void validateMcMeta(ZipFile zip, ZipEntry entry) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> mcmeta = mapper.readValue(zip.getInputStream(entry), Map.class);
            if (!mcmeta.containsKey("pack")) {
                addIssue("pack.mcmeta is missing 'pack' section", true);
                return;
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> pack = (Map<String, Object>) mcmeta.get("pack");
            if (!pack.containsKey("pack_format")) {
                addIssue("pack.mcmeta is missing 'pack_format'", true);
            }
            if (!pack.containsKey("description")) {
                addIssue("pack.mcmeta is missing 'description'", false);
            }
        } catch (IOException e) {
            addIssue("Invalid pack.mcmeta JSON: " + e.getMessage(), true);
        }
    }

    private void validateJsonFile(ZipFile zip, ZipEntry entry, Map<String, Set<String>> textureReferences) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> json = mapper.readValue(zip.getInputStream(entry), Map.class);
            
            // Collect texture references from models
            if (entry.getName().contains("/models/")) {
                if (json.containsKey("textures")) {
                    Set<String> textures = new HashSet<>();
                    @SuppressWarnings("unchecked")
                    Map<String, String> textureMap = (Map<String, String>) json.get("textures");
                    textureMap.values().forEach(tex -> {
                        if (!tex.startsWith("#")) {
                            textures.add(tex);
                        }
                    });
                    if (!textures.isEmpty()) {
                        textureReferences.put(entry.getName(), textures);
                    }
                }
            }
        } catch (IOException e) {
            addIssue("Invalid JSON in " + entry.getName() + ": " + e.getMessage(), false);
        }
    }

    private void validateTextureReferences(ZipFile zip, Map<String, Set<String>> textureReferences) {
        textureReferences.forEach((model, textures) -> {
            textures.forEach(texture -> {
                String texturePath = "assets/minecraft/textures/" + texture + ".png";
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
