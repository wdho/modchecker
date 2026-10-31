package com.li.modchecker;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.network.ConnectionData;
import net.minecraftforge.network.NetworkHooks;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Loads, saves and generates the mod manifest file.
 */
public final class ManifestManager {

    public static final String FILE_NAME = "modchecker-manifest.json";

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static volatile ManifestData current;
    private static volatile String loadError;
    private static volatile int serverVersionMismatchCount;

    private ManifestManager() {
    }

    public static Path path() {
        return FMLPaths.CONFIGDIR.get().resolve(FILE_NAME);
    }

    public static ManifestData current() {
        return current;
    }

    public static String loadError() {
        return loadError;
    }

    public static int serverVersionMismatchCount() {
        return serverVersionMismatchCount;
    }

    public static synchronized void load() {
        Path file = path();
        current = null;
        loadError = null;
        serverVersionMismatchCount = 0;

        if (Files.notExists(file)) {
            LOGGER.warn("[ModChecker] Manifest file not found: {} (generate it in game with /modchecker generate)", file);
            return;
        }
        try {
            if (!Files.isRegularFile(file)) {
                throw new IllegalStateException("manifest path is not a readable regular file");
            }
            if (Files.size(file) > 16L * 1024 * 1024) {
                throw new IllegalStateException("mod manifest exceeds 16 MiB");
            }
            String json = Files.readString(file, StandardCharsets.UTF_8);
            ManifestData data = ManifestValidation.parse(GSON, json, ManifestData.class);
            ManifestValidation.validate(data);
            int mismatches = checkServerVersions(data);
            current = data;
            serverVersionMismatchCount = mismatches;
            LOGGER.info("[ModChecker] Loaded manifest {}: {} mod entries, {} channel versions",
                    file, data.mods.size(), data.channelVersions.size());
        } catch (Exception e) {
            loadError = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
            LOGGER.error("[ModChecker] Failed to load manifest {}: {}", file, e.toString());
        }
    }

    private static int checkServerVersions(ManifestData data) {
        Map<String, String> server = serverMods();
        int mismatches = 0;
        for (ManifestData.ModEntry entry : data.mods) {
            if (!"server".equals(entry.side) && !"both".equals(entry.side)) {
                continue;
            }
            String actual = server.get(entry.id);
            if (actual != null && !"*".equals(entry.version) && !actual.equals(entry.version)) {
                mismatches++;
                if (mismatches <= 20) {
                    LOGGER.warn("[ModChecker] Manifest/server version mismatch for '{}': manifest={}, server={}",
                            entry.id, entry.version, actual);
                }
            }
        }
        if (mismatches > 0) {
            LOGGER.warn("[ModChecker] {} manifest/server version mismatch(es); regenerate the manifest after updating server mods.", mismatches);
        }
        return mismatches;
    }

    public static Map<String, String> serverMods() {
        Map<String, String> map = new LinkedHashMap<>();
        for (IModInfo info : ModList.get().getMods()) {
            map.put(info.getModId(), info.getVersion().toString());
        }
        return map;
    }

    /**
     * Expected client mod ids (entries with side client/both), excluding ignored ids.
     */
    public static Set<String> expectedClientIds(ManifestData data) {
        Set<String> expected = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        Set<String> ignored = CheckerConfig.ignoredModsLower();
        ignored.add(ModCheckerMod.MOD_ID);
        for (ManifestData.ModEntry entry : data.mods) {
            if ("client".equals(entry.side) || "both".equals(entry.side)) {
                if (!ignored.contains(entry.id.toLowerCase(Locale.ROOT))) {
                    expected.add(entry.id);
                }
            }
        }
        return expected;
    }

    /**
     * Generates the manifest from the server's own mod list plus the client mod list / channel
     * versions reported by the given (admin) player during the Forge handshake.
     */
    public static synchronized ManifestData generate(ServerPlayer admin) {
        ConnectionData data = NetworkHooks.getConnectionData(admin.connection.connection);
        if (data == null) {
            throw new IllegalStateException("No Forge handshake data for " + admin.getGameProfile().getName()
                    + " (vanilla client, or the player did not connect through Forge)");
        }

        Map<String, String> server = serverMods();
        List<String> clientIds = List.copyOf(data.getModList());
        Map<String, String> clientLowerToOriginal = new LinkedHashMap<>();
        for (String id : clientIds) {
            clientLowerToOriginal.putIfAbsent(id.toLowerCase(Locale.ROOT), id);
        }

        Set<String> bannedLower = new LinkedHashSet<>(CheckerConfig.bannedModsLower());
        Set<String> ignoredLower = CheckerConfig.ignoredModsLower();
        ignoredLower.add(ModCheckerMod.MOD_ID);

        TreeSet<String> union = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        union.addAll(server.keySet());
        union.addAll(clientIds);

        ManifestData out = new ManifestData();
        out.generatedAt = DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now());
        out.generatedBy = admin.getGameProfile().getName();

        List<String> skippedBanned = new ArrayList<>();
        List<String> skippedIgnored = new ArrayList<>();
        for (String id : union) {
            String lower = id.toLowerCase(Locale.ROOT);
            if (bannedLower.contains(lower)) {
                skippedBanned.add(id);
                continue;
            }
            boolean inServer = server.containsKey(id);
            boolean inClient = clientLowerToOriginal.containsKey(lower);
            String side;
            if (ModCheckerMod.MOD_ID.equals(id)) {
                // Companion admission is enforced separately by the mandatory login protocol.
                side = inServer ? "server" : "client";
            } else if (inServer && inClient) {
                side = "both";
            } else if (inServer) {
                side = "server";
            } else {
                side = "client";
            }
            String version = inServer ? server.get(id) : "*";
            boolean ignored = ignoredLower.contains(lower);
            if (ignored && !ModCheckerMod.MOD_ID.equals(id)) {
                // Keep ignored mods in the manifest for documentation, but they are excluded from checks.
                skippedIgnored.add(id);
            }
            out.mods.add(new ManifestData.ModEntry(id, version, side));
        }

        for (Map.Entry<ResourceLocation, String> entry : data.getChannels().entrySet()) {
            String namespace = entry.getKey().getNamespace().toLowerCase(Locale.ROOT);
            if (bannedLower.contains(namespace) || ignoredLower.contains(namespace)) {
                continue;
            }
            out.channelVersions.put(entry.getKey().toString(), entry.getValue());
        }

        ManifestValidation.validate(out);
        int mismatches = checkServerVersions(out);
        Path file = path();
        try {
            writeJson(file, out, GSON);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write manifest " + file + ": " + e.getMessage(), e);
        }

        current = out;
        loadError = null;
        serverVersionMismatchCount = mismatches;
        LOGGER.info("[ModChecker] Manifest generated by {}: {} entries, {} expected client mods, {} channel versions, {} banned skipped, {} ignored noted.",
                admin.getGameProfile().getName(), out.mods.size(), expectedClientIds(out).size(),
                out.channelVersions.size(), skippedBanned.size(), skippedIgnored.size());
        if (!skippedBanned.isEmpty()) {
            LOGGER.info("[ModChecker] Banned mods excluded from manifest: {}", skippedBanned);
        }
        return out;
    }

    /** Commit complete JSON with one atomic replacement; a failed commit leaves the old file intact. */
    static void writeJson(Path file, Object data, Gson gson) throws IOException {
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), file.getFileName() + ".", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
                gson.toJson(data, writer);
            }
            if (Files.isRegularFile(file)) {
                Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            }
            // Do not fall back to a non-atomic overwrite when the filesystem cannot provide this guarantee.
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
