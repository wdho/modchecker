package com.li.modchecker;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Extra client mods allowed beyond the manifest. Only exact lowercase mod ids are authorized.
 */
public final class WhitelistManager {

    public static final String FILE_NAME = "modchecker-whitelist.txt";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Set<String> ENTRIES = new LinkedHashSet<>();
    private static volatile String loadError;

    private WhitelistManager() {
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    public static synchronized void load() {
        ENTRIES.clear();
        loadError = null;
        Path file = path();
        try {
            if (!Files.isRegularFile(file)) {
                Files.createDirectories(file.getParent());
                Files.writeString(file, defaultContent(), StandardCharsets.UTF_8);
                LOGGER.info("[ModChecker] Created default whitelist: {}", file);
                return;
            }
            if (Files.size(file) > 16L * 1024 * 1024) {
                throw new IllegalStateException("whitelist exceeds 16 MiB");
            }
            Set<String> parsed = new LinkedHashSet<>();
            int lineNumber = 0;
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                lineNumber++;
                String value = line.trim();
                if (value.isEmpty() || value.startsWith("#")) {
                    continue;
                }
                try {
                    ManifestValidation.modId(value);
                } catch (IllegalStateException e) {
                    throw new IllegalStateException("whitelist line " + lineNumber + ": " + e.getMessage());
                }
                if (!parsed.add(value)) {
                    throw new IllegalStateException("duplicate whitelist mod id at line " + lineNumber + ": " + value);
                }
                if (parsed.size() > 4096) {
                    throw new IllegalStateException("whitelist exceeds 4096 mod ids");
                }
            }
            ENTRIES.addAll(parsed);
            LOGGER.info("[ModChecker] Loaded whitelist {}: {} entries", file, ENTRIES.size());
        } catch (IOException | IllegalStateException e) {
            loadError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            LOGGER.error("[ModChecker] Failed to load whitelist {}: {}", file, e.toString());
        }
    }

    public static synchronized boolean isAllowed(String modId) {
        return loadError == null && modId != null && ENTRIES.contains(modId);
    }

    public static String loadError() {
        return loadError;
    }

    public static synchronized int size() {
        return ENTRIES.size();
    }

    public static synchronized List<String> entries() {
        return new ArrayList<>(ENTRIES);
    }

    /**
     * Appends one exact lowercase mod id to the whitelist and reloads it.
     * The write is atomic; a reload failure restores the previous file.
     *
     * @return true if the entry was added, false if it was already present
     */
    public static synchronized boolean add(String modId) throws IOException {
        String value = modId == null ? "" : modId.trim();
        ManifestValidation.modId(value);
        if (loadError != null) {
            throw new IllegalStateException("whitelist is currently invalid: " + loadError);
        }
        if (ENTRIES.contains(value)) {
            return false;
        }
        if (ENTRIES.size() >= 4096) {
            throw new IllegalStateException("whitelist exceeds 4096 mod ids");
        }

        Path file = path();
        Files.createDirectories(file.getParent());
        String original = Files.isRegularFile(file) ? Files.readString(file, StandardCharsets.UTF_8) : defaultContent();
        String updated = original.endsWith("\n") ? original : original + "\n";
        updated += value + "\n";
        Path temp = file.resolveSibling(FILE_NAME + ".tmp");
        Files.writeString(temp, updated, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }

        load();
        if (loadError != null) {
            // Put the previous content back so a bad edit cannot silently corrupt the whitelist.
            Path rollback = file.resolveSibling(FILE_NAME + ".rollback");
            Files.writeString(rollback, original, StandardCharsets.UTF_8);
            try {
                Files.move(rollback, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(rollback, file, StandardCopyOption.REPLACE_EXISTING);
            }
            throw new IllegalStateException("whitelist reload failed: " + loadError);
        }
        return ENTRIES.contains(value);
    }

    private static String defaultContent() {
        return "# Mod Checker whitelist\n"
                + "# One exact lowercase mod id per line. Display names and quoted names are rejected.\n"
                + "# Extra client mods listed here may join even if they are not in the server manifest.\n"
                + "# Examples:\n"
                + "#   jei\n"
                + "#\n";
    }
}
