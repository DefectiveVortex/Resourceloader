package org.vortex.resourceloader.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.bukkit.Bukkit;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads and writes the version fields of pack.mcmeta.
 *
 * Since 1.21.9 (resource format 65) packs declare "min_format"/"max_format" as [major, minor].
 * The game rejects a pack.mcmeta outright when:
 * - it supports a format above 64 without both min_format and max_format, or
 * - it supports a format from 15 to 64 without supported_formats (and pack_format), or
 * - it uses supported_formats or min_format/max_format with a lowest format below 15.
 */
public final class PackFormats {
    public static final int FIRST_MINOR_FORMAT = 65;
    public static final int LAST_LEGACY_FORMAT = 64;
    public static final int FIRST_MULTI_VERSION_FORMAT = 15;
    private static final int ANY_MINOR = Integer.MAX_VALUE;

    private static volatile Version currentResourceFormat;
    private static volatile boolean detected;

    private PackFormats() {
    }

    public static final class Version implements Comparable<Version> {
        private final int major;
        private final int minor;

        public Version(int major, int minor) {
            this.major = major;
            this.minor = minor;
        }

        public int major() {
            return major;
        }

        public int minor() {
            return minor;
        }

        @Override
        public int compareTo(Version o) {
            return major != o.major ? Integer.compare(major, o.major) : Integer.compare(minor, o.minor);
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Version && ((Version) o).major == major && ((Version) o).minor == minor;
        }

        @Override
        public int hashCode() {
            return 31 * major + minor;
        }

        @Override
        public String toString() {
            return minor == ANY_MINOR ? major + ".*" : major + "." + minor;
        }
    }

    public static final class Range {
        private final Version min;
        private final Version max;

        public Range(Version min, Version max) {
            this.min = min;
            this.max = max;
        }

        public Version min() {
            return min;
        }

        public Version max() {
            return max;
        }

        public boolean contains(Version v) {
            return min.compareTo(v) <= 0 && max.compareTo(v) >= 0;
        }

        public Range span(Range other) {
            return new Range(min.compareTo(other.min) <= 0 ? min : other.min,
                max.compareTo(other.max) >= 0 ? max : other.max);
        }

        @Override
        public String toString() {
            return min.equals(max) ? min.toString() : min + " - " + max;
        }
    }

    /** The resource pack format of the running server, or null if it cannot be determined. */
    public static Version currentResourceFormat() {
        if (!detected) {
            currentResourceFormat = detectCurrentResourceFormat();
            detected = true;
        }
        return currentResourceFormat;
    }

    private static Version detectCurrentResourceFormat() {
        // Mojang-mapped servers (Paper) expose the constants directly
        try {
            Class<?> constants = Class.forName("net.minecraft.SharedConstants");
            try {
                Field major = constants.getField("RESOURCE_PACK_FORMAT_MAJOR");
                Field minor = constants.getField("RESOURCE_PACK_FORMAT_MINOR");
                return new Version(major.getInt(null), minor.getInt(null));
            } catch (NoSuchFieldException e) {
                return new Version(constants.getField("RESOURCE_PACK_FORMAT").getInt(null), 0);
            }
        } catch (Throwable ignored) {
        }

        // Every server jar ships the vanilla version.json
        try (InputStream in = Bukkit.getServer().getClass().getClassLoader().getResourceAsStream("version.json")) {
            if (in != null) {
                JsonNode packVersion = new ObjectMapper().readTree(in).path("pack_version");
                if (packVersion.has("resource_major")) {
                    return new Version(packVersion.get("resource_major").asInt(),
                        packVersion.path("resource_minor").asInt(0));
                }
                if (packVersion.has("resource")) {
                    return new Version(packVersion.get("resource").asInt(), 0);
                }
                if (packVersion.isInt()) {
                    return new Version(packVersion.asInt(), 0);
                }
            }
        } catch (Throwable ignored) {
        }

        return fromMinecraftVersion(Bukkit.getBukkitVersion());
    }

    /** Last resort for servers without version.json. */
    private static Version fromMinecraftVersion(String bukkitVersion) {
        Matcher m = Pattern.compile("(\\d+\\.\\d+(\\.\\d+)?)").matcher(bukkitVersion == null ? "" : bukkitVersion);
        if (!m.find()) {
            return null;
        }
        int format;
        switch (m.group(1)) {
            case "1.21.7": case "1.21.8": format = 64; break;
            case "1.21.6": format = 63; break;
            case "1.21.5": format = 55; break;
            case "1.21.4": format = 46; break;
            case "1.21.2": case "1.21.3": format = 42; break;
            case "1.21": case "1.21.1": format = 34; break;
            case "1.20.5": case "1.20.6": format = 32; break;
            case "1.20.3": case "1.20.4": format = 22; break;
            case "1.20.2": format = 18; break;
            case "1.20": case "1.20.1": format = 15; break;
            case "1.19.4": format = 13; break;
            case "1.19.3": format = 12; break;
            case "1.19": case "1.19.1": case "1.19.2": format = 9; break;
            case "1.18": case "1.18.1": case "1.18.2": format = 8; break;
            case "1.17": case "1.17.1": format = 7; break;
            case "1.16.2": case "1.16.3": case "1.16.4": case "1.16.5": format = 6; break;
            case "1.15": case "1.15.1": case "1.15.2": case "1.16": case "1.16.1": format = 5; break;
            case "1.13": case "1.13.1": case "1.13.2":
            case "1.14": case "1.14.1": case "1.14.2": case "1.14.3": case "1.14.4": format = 4; break;
            default: format = -1;
        }
        return format < 0 ? null : new Version(format, 0);
    }

    /**
     * The formats a pack.mcmeta "pack" section declares support for, or null if it declares none.
     */
    public static Range readRange(Map<String, Object> pack) {
        if (pack == null) {
            return null;
        }
        Object minFormat = pack.get("min_format");
        Object maxFormat = pack.get("max_format");
        if (minFormat != null && maxFormat != null) {
            Version min = readVersion(minFormat, false);
            Version max = readVersion(maxFormat, true);
            if (min != null && max != null) {
                return new Range(min, max);
            }
        }

        Range range = null;
        Integer packFormat = asInt(pack.get("pack_format"));
        if (packFormat != null) {
            range = new Range(new Version(packFormat, 0), new Version(packFormat, ANY_MINOR));
        }
        Range supported = readSupportedFormats(pack.get("supported_formats"));
        if (supported != null) {
            range = range == null ? supported : range.span(supported);
        }
        return range;
    }

    private static Range readSupportedFormats(Object value) {
        Integer single = asInt(value);
        if (single != null) {
            return new Range(new Version(single, 0), new Version(single, ANY_MINOR));
        }
        if (value instanceof List && ((List<?>) value).size() == 2) {
            List<?> list = (List<?>) value;
            Integer lo = asInt(list.get(0));
            Integer hi = asInt(list.get(1));
            if (lo != null && hi != null) {
                return new Range(new Version(lo, 0), new Version(hi, ANY_MINOR));
            }
        }
        if (value instanceof Map) {
            Map<?, ?> map = (Map<?, ?>) value;
            Integer lo = asInt(map.get("min_inclusive"));
            Integer hi = asInt(map.get("max_inclusive"));
            if (lo != null && hi != null) {
                return new Range(new Version(lo, 0), new Version(hi, ANY_MINOR));
            }
        }
        return null;
    }

    private static Version readVersion(Object value, boolean upperBound) {
        Integer major = asInt(value);
        if (major != null) {
            return new Version(major, upperBound ? ANY_MINOR : 0);
        }
        if (value instanceof List && !((List<?>) value).isEmpty()) {
            List<?> list = (List<?>) value;
            Integer maj = asInt(list.get(0));
            Integer min = list.size() > 1 ? asInt(list.get(1)) : null;
            if (maj != null) {
                return new Version(maj, min != null ? min : (upperBound ? ANY_MINOR : 0));
            }
        }
        return null;
    }

    private static Integer asInt(Object value) {
        return value instanceof Number ? ((Number) value).intValue() : null;
    }

    /** Problems the game itself would reject this pack.mcmeta for. Empty if it would load. */
    public static List<String> findRejections(Map<String, Object> pack) {
        List<String> problems = new ArrayList<>();
        Range range = readRange(pack);
        if (range == null) {
            problems.add("pack.mcmeta declares no format: add \"min_format\" and \"max_format\""
                + " (or \"pack_format\" for packs older than 1.21.9)");
            return problems;
        }
        boolean hasMinMax = pack.get("min_format") != null && pack.get("max_format") != null;
        if (range.max().major() > LAST_LEGACY_FORMAT && !hasMinMax) {
            problems.add("pack.mcmeta supports formats newer than " + LAST_LEGACY_FORMAT
                + " but is missing \"min_format\" and \"max_format\"");
        }
        boolean multiVersion = hasMinMax || pack.get("supported_formats") != null;
        if (multiVersion && range.min().major() < FIRST_MULTI_VERSION_FORMAT) {
            problems.add("pack.mcmeta uses supported_formats/min_format with format " + range.min().major()
                + "; those fields need a lowest format of " + FIRST_MULTI_VERSION_FORMAT
                + " or later (use only \"pack_format\" for older packs)");
        }
        if (hasMinMax && range.min().major() <= LAST_LEGACY_FORMAT && range.min().major() >= FIRST_MULTI_VERSION_FORMAT
            && pack.get("supported_formats") == null) {
            problems.add("pack.mcmeta supports format " + range.min().major() + ", so it also needs"
                + " \"supported_formats\": [" + range.min().major() + ", " + LAST_LEGACY_FORMAT + "]");
        }
        return problems;
    }

    /**
     * Rewrites the version fields of a "pack" section so that it declares {@code range}, in a form every
     * game version in that range accepts. Clients before 1.20.2 read only pack_format, so when the server's
     * own format ({@code server}, may be null) is covered, pack_format names it.
     */
    public static void writeRange(Map<String, Object> pack, Range range, Version server) {
        pack.remove("pack_format");
        pack.remove("supported_formats");
        pack.remove("min_format");
        pack.remove("max_format");

        if (server != null && server.major() < FIRST_MULTI_VERSION_FORMAT) {
            // Before 1.20 there are no ranges, and newer games refuse ranges starting this low
            pack.put("pack_format", server.major());
            return;
        }

        Version min = range.min();
        Version max = range.max();
        if (min.major() < FIRST_MULTI_VERSION_FORMAT && max.major() > min.major()) {
            // The game refuses multi-version packs that start below 15 (1.20)
            min = new Version(FIRST_MULTI_VERSION_FORMAT, 0);
        }
        if (max.major() >= FIRST_MINOR_FORMAT) {
            pack.put("min_format", Arrays.asList(min.major(), min.minor()));
            pack.put("max_format", max.minor() == ANY_MINOR ? (Object) max.major() : Arrays.asList(max.major(), max.minor()));
        }
        if (min.major() < FIRST_MINOR_FORMAT) {
            int legacyMax = Math.min(max.major(), LAST_LEGACY_FORMAT);
            boolean serverCovered = server != null && server.major() >= min.major() && server.major() <= legacyMax;
            pack.put("pack_format", serverCovered ? server.major() : min.major());
            if (legacyMax > min.major() || max.major() >= FIRST_MINOR_FORMAT) {
                pack.put("supported_formats", Arrays.asList(min.major(), legacyMax));
            }
        }
    }
}
