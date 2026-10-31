package com.li.modchecker;

import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

/**
 * Computes the client-side mod fingerprint report. A cancellation callback is checked between
 * files and between read chunks so a disconnected login does not keep draining disk I/O.
 */
public final class HashReportFactory {

    private static final int BUFFER_SIZE = 64 * 1024;

    private HashReportFactory() {
    }

    public static HashReportMessage create() {
        return create(() -> false);
    }

    public static HashReportMessage create(BooleanSupplier cancelled) {
        checkCancelled(cancelled);
        Map<String, CachedFingerprint> cache = new LinkedHashMap<>();
        Map<String, HashReportMessage.ClientMod> mods = new LinkedHashMap<>();
        for (IModInfo info : ModList.get().getMods()) {
            checkCancelled(cancelled);
            Path resolvedPath = null;
            try {
                resolvedPath = info.getOwningFile().getFile().getFilePath();
            } catch (Throwable ignored) {
                // built-in mods / unusual providers may not expose a file path
            }
            final Path modFilePath = stableModFilePath(info, resolvedPath);
            CachedFingerprint fingerprint;
            if (modFilePath == null) {
                fingerprint = CachedFingerprint.UNAVAILABLE;
            } else {
                try {
                    String key = modFilePath.toAbsolutePath().normalize().toString();
                    fingerprint = cache.computeIfAbsent(key, unused -> fingerprint(modFilePath, cancelled));
                } catch (java.util.concurrent.CancellationException e) {
                    throw e;
                } catch (Throwable t) {
                    fingerprint = CachedFingerprint.UNAVAILABLE;
                }
            }
            mods.put(info.getModId(), new HashReportMessage.ClientMod(
                    info.getDisplayName(),
                    info.getVersion().toString(),
                    fingerprint.sha256,
                    fingerprint.size));
        }
        checkCancelled(cancelled);
        return new HashReportMessage(mods);
    }

    private static final Pattern TOML_MOD_ID = Pattern.compile("(?m)^\\s*modId\\s*=\\s*[\"']([^\"']+)[\"']");
    private static final Pattern FABRIC_MOD_ID = Pattern.compile("\\\"id\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static volatile Path cachedModsDir;
    private static volatile ModFileIndex cachedModIndex;

    /**
     * Forge exposes each logical mod with an owning file. For normal mods that is the jar in the
     * mods directory, but JarJar/bootstrap mods may expose a virtual or runtime-extracted file.
     * Resolve anomalous paths back to the stable top-level artifact in the mods directory.
     */
    static Path stableModFilePath(IModInfo info, Path reported) {
        if (info == null || reported == null) {
            return reported;
        }
        try {
            return canonicalModFilePath(reported, info.getModId(), FMLPaths.MODSDIR.get());
        } catch (Throwable ignored) {
            return reported;
        }
    }

    static Path canonicalModFilePath(Path reported, String modId, Path modsDir) {
        if (reported == null || modId == null || modId.isBlank()) {
            return reported;
        }
        Path normalized = reported.toAbsolutePath().normalize();
        Path mods = modsDir == null ? null : modsDir.toAbsolutePath().normalize();
        boolean underMods = mods != null && normalized.startsWith(mods);
        if (underMods && (Files.isRegularFile(normalized) || Files.isDirectory(normalized))) {
            return reported;
        }
        if (mods == null || !Files.isDirectory(mods)) {
            return reported;
        }
        Path outer = index(mods).resolve(modId);
        return outer != null ? outer : reported;
    }

    private static ModFileIndex index(Path modsDir) {
        Path normalized = modsDir.toAbsolutePath().normalize();
        ModFileIndex current = cachedModIndex;
        if (current != null && normalized.equals(cachedModsDir)) {
            return current;
        }
        synchronized (HashReportFactory.class) {
            if (cachedModIndex == null || !normalized.equals(cachedModsDir)) {
                cachedModIndex = ModFileIndex.build(normalized);
                cachedModsDir = normalized;
            }
            return cachedModIndex;
        }
    }

    private static final class ModFileIndex {
        private final Path modsDir;
        private final Map<String, Path> topLevel = new LinkedHashMap<>();
        private final List<Path> topLevelJars = new ArrayList<>();
        private volatile Map<String, Path> nested;

        private ModFileIndex(Path modsDir) {
            this.modsDir = modsDir;
        }

        private static ModFileIndex build(Path modsDir) {
            ModFileIndex index = new ModFileIndex(modsDir);
            List<Path> children = new ArrayList<>();
            try (Stream<Path> stream = Files.list(modsDir)) {
                stream.forEach(children::add);
            } catch (Throwable ignored) {
                return index;
            }
            children.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
            for (Path child : children) {
                if (Files.isDirectory(child)) {
                    Map<String, Path> directoryIds = new LinkedHashMap<>();
                    collectDirectoryIds(child, directoryIds, child);
                    directoryIds.forEach(index.topLevel::putIfAbsent);
                } else if (Files.isRegularFile(child) && child.getFileName().toString().endsWith(".jar")) {
                    index.topLevelJars.add(child);
                    Map<String, Path> ids = new LinkedHashMap<>();
                    collectJarMetadata(child, child, ids);
                    ids.forEach(index.topLevel::putIfAbsent);
                }
            }
            return index;
        }

        private Path resolve(String modId) {
            String key = modId.toLowerCase(Locale.ROOT);
            Path top = topLevel.get(key);
            if (top != null) {
                return top;
            }
            Map<String, Path> nestedIndex = nested;
            if (nestedIndex == null) {
                synchronized (this) {
                    if (nested == null) {
                        Map<String, Path> built = new LinkedHashMap<>();
                        for (Path jar : topLevelJars) {
                            try (ZipFile zip = new ZipFile(jar.toFile())) {
                                for (var entries = zip.entries(); entries.hasMoreElements(); ) {
                                    ZipEntry entry = entries.nextElement();
                                    if (!entry.isDirectory() && entry.getName().endsWith(".jar")) {
                                        byte[] data = readEntry(zip, entry);
                                        collectIdsFromJarBytes(data, jar, built, 1);
                                    }
                                }
                            } catch (Throwable ignored) {
                                // A broken jar should not prevent other mods from resolving.
                            }
                        }
                        nested = built;
                    }
                    nestedIndex = nested;
                }
            }
            return nestedIndex.get(key);
        }
    }

    private static void collectDirectoryIds(Path directory, Map<String, Path> ids, Path owner) {
        try {
            Path modsToml = directory.resolve("META-INF/mods.toml");
            if (Files.isRegularFile(modsToml)) {
                parseModIds(Files.readString(modsToml), owner, ids);
            }
            Path fabric = directory.resolve("fabric.mod.json");
            if (Files.isRegularFile(fabric)) {
                parseModIds(Files.readString(fabric), owner, ids);
            }
        } catch (Throwable ignored) {
            // best effort only
        }
    }

    private static void collectJarMetadata(Path jar, Path owner, Map<String, Path> ids) {
        try (ZipFile zip = new ZipFile(jar.toFile())) {
            ZipEntry modsToml = zip.getEntry("META-INF/mods.toml");
            if (modsToml != null) {
                parseModIds(new String(readEntry(zip, modsToml), StandardCharsets.UTF_8), owner, ids);
            }
            ZipEntry fabric = zip.getEntry("fabric.mod.json");
            if (fabric != null) {
                parseModIds(new String(readEntry(zip, fabric), StandardCharsets.UTF_8), owner, ids);
            }
        } catch (Throwable ignored) {
            // best effort only
        }
    }

    private static void collectIdsFromJarBytes(byte[] data, Path owner, Map<String, Path> ids, int depth) {
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (name.equals("META-INF/mods.toml") || name.equals("fabric.mod.json")) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    zip.transferTo(out);
                    parseModIds(out.toString(StandardCharsets.UTF_8), owner, ids);
                } else if (depth < 3 && name.endsWith(".jar")) {
                    ByteArrayOutputStream out = new ByteArrayOutputStream();
                    zip.transferTo(out);
                    collectIdsFromJarBytes(out.toByteArray(), owner, ids, depth + 1);
                }
            }
        } catch (Throwable ignored) {
            // best effort only
        }
    }

    private static void parseModIds(String text, Path owner, Map<String, Path> ids) {
        Matcher toml = TOML_MOD_ID.matcher(text);
        while (toml.find()) {
            ids.putIfAbsent(toml.group(1).toLowerCase(Locale.ROOT), owner);
        }
        Matcher fabric = FABRIC_MOD_ID.matcher(text);
        while (fabric.find()) {
            ids.putIfAbsent(fabric.group(1).toLowerCase(Locale.ROOT), owner);
        }
    }

    private static byte[] readEntry(ZipFile zip, ZipEntry entry) throws IOException {
        try (InputStream in = zip.getInputStream(entry)) {
            return in.readAllBytes();
        }
    }

    private static CachedFingerprint fingerprint(Path path, BooleanSupplier cancelled) {
        checkCancelled(cancelled);
        try {
            if (Files.isRegularFile(path)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream in = Files.newInputStream(path)) {
                    byte[] buffer = new byte[BUFFER_SIZE];
                    int read;
                    while ((read = in.read(buffer)) > 0) {
                        checkCancelled(cancelled);
                        digest.update(buffer, 0, read);
                    }
                }
                return new CachedFingerprint(hex(digest.digest()), Files.size(path));
            }
            if (Files.isDirectory(path)) {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                List<Path> files = new ArrayList<>();
                try (var stream = Files.walk(path)) {
                    stream.filter(Files::isRegularFile)
                            .filter(file -> !isConfigPath(path, file))
                            .forEach(file -> {
                                checkCancelled(cancelled);
                                files.add(file);
                            });
                }
                checkCancelled(cancelled);
                files.sort(Comparator.comparing(p -> path.relativize(p).toString()));
                checkCancelled(cancelled);
                long total = 0;
                for (Path file : files) {
                    checkCancelled(cancelled);
                    String relative = path.relativize(file).toString().replace('\\', '/');
                    digest.update(relative.getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) 0);
                    try (InputStream in = Files.newInputStream(file)) {
                        byte[] buffer = new byte[BUFFER_SIZE];
                        int read;
                        while ((read = in.read(buffer)) > 0) {
                            checkCancelled(cancelled);
                            digest.update(buffer, 0, read);
                        }
                    }
                    total += Files.size(file);
                }
                return new CachedFingerprint(hex(digest.digest()), total);
            }
            return CachedFingerprint.UNAVAILABLE;
        } catch (CancellationException e) {
            throw e;
        } catch (Throwable t) {
            return CachedFingerprint.UNAVAILABLE;
        }
    }

    /** Config files are runtime/settings data, not part of the mod distribution. */
    private static boolean isConfigPath(Path root, Path file) {
        Path relative = root.relativize(file);
        for (Path part : relative) {
            String name = part.toString();
            if (name.equalsIgnoreCase("config") || name.equalsIgnoreCase("configs")) {
                return true;
            }
        }
        return false;
    }

    private static void checkCancelled(BooleanSupplier cancelled) {
        if (cancelled != null && cancelled.getAsBoolean()) {
            throw new CancellationException("Mod Checker report cancelled");
        }
    }

    private static String hex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(Character.forDigit((b >>> 4) & 0xF, 16));
            builder.append(Character.forDigit(b & 0xF, 16));
        }
        return builder.toString();
    }

    private record CachedFingerprint(String sha256, long size) {
        private static final CachedFingerprint UNAVAILABLE = new CachedFingerprint("unavailable", -1);
    }
}