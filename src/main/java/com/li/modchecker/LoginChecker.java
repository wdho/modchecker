package com.li.modchecker;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.ConnectionData;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Checks server policy against Forge connection data before the player enters the world. */
public final class LoginChecker {

    private LoginChecker() {
    }

    public static boolean hasCompanion(ConnectionData data) {
        boolean hasMod = false;
        for (String modId : data.getModList()) {
            if (modId.equalsIgnoreCase(ModCheckerMod.MOD_ID)) {
                hasMod = true;
                break;
            }
        }
        boolean hasChannel = false;
        for (ResourceLocation channel : data.getChannels().keySet()) {
            if (channel.toString().equals(ModCheckerMod.REPORT_CHANNEL)) {
                hasChannel = true;
                break;
            }
        }
        return hasMod && hasChannel;
    }

    public static List<String> bannedMods(Collection<String> modIds) {
        Set<String> bannedLower = new LinkedHashSet<>(CheckerConfig.bannedModsLower());
        List<String> hits = new ArrayList<>();
        if (bannedLower.isEmpty()) {
            return hits;
        }
        for (String modId : modIds) {
            if (bannedLower.contains(modId.toLowerCase(Locale.ROOT))) {
                hits.add(modId);
            }
        }
        return hits;
    }

    /** Runs the policy shared by login admission and administrator diagnostics. */
    public static CheckResult checkConnection(ConnectionData data) {
        CheckResult result = new CheckResult();
        addConfigurationError(result, "mod manifest", ManifestManager.loadError());
        addConfigurationError(result, "hash manifest", HashManifestManager.loadError());
        addConfigurationError(result, "whitelist", WhitelistManager.loadError());
        if (!result.configurationErrors.isEmpty()) {
            return result;
        }
        if (data == null) {
            result.manifestMissing = true;
            result.manifestMissingFailure = true;
            return result;
        }
        if (!hasCompanion(data)) {
            result.configurationErrors.add("Matching Mod Checker client companion is required.");
        }
        if (CheckerConfig.requireHashManifest() && HashManifestManager.current() == null) {
            result.hashManifestMissingFailure = true;
        }
        Set<String> ignoredLower = CheckerConfig.ignoredModsLower();
        ignoredLower.add(ModCheckerMod.MOD_ID);
        Set<String> bannedLower = new LinkedHashSet<>(CheckerConfig.bannedModsLower());

        Map<String, String> actualLowerToOriginal = new LinkedHashMap<>();
        for (String modId : data.getModList()) {
            String lower = modId.toLowerCase(Locale.ROOT);
            actualLowerToOriginal.putIfAbsent(lower, modId);
            if (bannedLower.contains(lower) && !result.banned.contains(modId)) {
                result.banned.add(modId);
            }
        }

        ManifestData manifest = ManifestManager.current();
        if (manifest == null) {
            result.manifestMissing = true;
            result.manifestMissingFailure = CheckerConfig.requireManifest();
        } else {
            if (CheckerConfig.strictModList()) {
                Map<String, String> expectedLowerToOriginal = expectedClientMods(manifest, ignoredLower);
                for (Map.Entry<String, String> expected : expectedLowerToOriginal.entrySet()) {
                    if (!actualLowerToOriginal.containsKey(expected.getKey()) && !optionalWhitelisted(expected.getValue())) {
                        result.missing.add(expected.getValue());
                    }
                }
                for (Map.Entry<String, String> actual : actualLowerToOriginal.entrySet()) {
                    if (!expectedLowerToOriginal.containsKey(actual.getKey())
                            && !ignoredLower.contains(actual.getKey())
                            && !WhitelistManager.isAllowed(actual.getValue())) {
                        result.extra.add(actual.getValue());
                    }
                }
            }
            if (CheckerConfig.checkChannelVersions()) {
                addChannelVersionMismatches(result, manifest, data, ignoredLower, bannedLower);
            }
        }
        sort(result);
        return result;
    }

    /** The caller must first bind the report's id set to this connection's Forge handshake. */
    public static CheckResult checkWithReport(ConnectionData data, HashReportMessage report) {
        CheckResult result = checkConnection(data);
        if (result.hasFailure()) {
            return result;
        }
        Set<String> ignoredLower = CheckerConfig.ignoredModsLower();
        ignoredLower.add(ModCheckerMod.MOD_ID);
        Set<String> bannedLower = new LinkedHashSet<>(CheckerConfig.bannedModsLower());
        ManifestData manifest = ManifestManager.current();
        Set<String> whitelistedExtraLower = new LinkedHashSet<>();
        Map<String, String> expected = manifest == null ? Map.of() : expectedClientMods(manifest, ignoredLower);
        for (String id : report.mods.keySet()) {
            String lower = id.toLowerCase(Locale.ROOT);
            if (!expected.containsKey(lower) && !ignoredLower.contains(lower) && WhitelistManager.isAllowed(id)) {
                whitelistedExtraLower.add(lower);
            }
        }

        HashManifestData hashes = HashManifestManager.current();
        if (hashes == null) {
            if (CheckerConfig.requireHashManifest()) {
                result.hashManifestMissingFailure = true;
            }
        } else {
            for (Map.Entry<String, HashManifestData.ModHash> entry : hashes.mods.entrySet()) {
                String lower = entry.getKey().toLowerCase(Locale.ROOT);
                boolean self = lower.equals(ModCheckerMod.MOD_ID);
                if (self) {
                    // The companion is required separately; optionally verify its own file hash too.
                    if (!CheckerConfig.checkCompanionHash() || bannedLower.contains(lower)
                            || whitelistedExtraLower.contains(lower)) {
                        continue;
                    }
                } else if (!expectedLower(manifest, ignoredLower).contains(lower)
                        || ignoredLower.contains(lower)
                        || bannedLower.contains(lower)
                        || whitelistedExtraLower.contains(lower)) {
                    continue;
                }
                HashReportMessage.ClientMod clientMod = findReportMod(report, lower);
                if (clientMod == null) {
                    continue;
                }
                String expectedHash = entry.getValue().sha256;
                if (expectedHash == null || expectedHash.isBlank()
                        || "unavailable".equalsIgnoreCase(expectedHash) || "*".equals(expectedHash)) {
                    continue;
                }
                if (clientMod.sha256 == null || !expectedHash.equalsIgnoreCase(clientMod.sha256)) {
                    result.hashMismatches.add(entry.getKey()
                            + " (expected " + HashManifestManager.shortHash(expectedHash)
                            + ", client " + HashManifestManager.shortHash(clientMod.sha256) + ")");
                }
            }
        }

        sort(result);
        return result;
    }

    private static HashReportMessage.ClientMod findReportMod(HashReportMessage report, String lowerId) {
        for (Map.Entry<String, HashReportMessage.ClientMod> entry : report.mods.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).equals(lowerId)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean optionalWhitelisted(String id) {
        return CheckerConfig.whitelistAllowsMissing() && WhitelistManager.isAllowed(id);
    }

    private static Set<String> expectedLower(ManifestData manifest, Set<String> ignoredLower) {
        Set<String> expected = new LinkedHashSet<>();
        if (manifest == null) {
            return expected;
        }
        for (ManifestData.ModEntry entry : manifest.mods) {
            String lower = entry.id.toLowerCase(Locale.ROOT);
            if (ignoredLower.contains(lower)) {
                continue;
            }
            if ("client".equals(entry.side) || "both".equals(entry.side)) {
                expected.add(lower);
            }
        }
        return expected;
    }

    private static Map<String, String> expectedClientMods(ManifestData manifest, Set<String> ignoredLower) {
        Map<String, String> expected = new LinkedHashMap<>();
        for (ManifestData.ModEntry entry : manifest.mods) {
            String lower = entry.id.toLowerCase(Locale.ROOT);
            if (ignoredLower.contains(lower)) {
                continue;
            }
            if ("client".equals(entry.side) || "both".equals(entry.side)) {
                expected.putIfAbsent(lower, entry.id);
            }
        }
        return expected;
    }

    private static void addChannelVersionMismatches(CheckResult result, ManifestData manifest, ConnectionData data,
                                                    Set<String> ignoredLower, Set<String> bannedLower) {
        Map<String, String> clientChannels = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, String> entry : data.getChannels().entrySet()) {
            clientChannels.put(entry.getKey().toString(), entry.getValue());
        }
        for (Map.Entry<String, String> entry : manifest.channelVersions.entrySet()) {
            String namespace = namespaceOf(entry.getKey());
            if (ignoredLower.contains(namespace) || bannedLower.contains(namespace)
 ) {
                continue;
            }
            String clientVersion = clientChannels.get(entry.getKey());
            if (clientVersion == null || !clientVersion.equals(entry.getValue())) {
                result.versionMismatches.add(entry.getKey()
                        + " (expected " + entry.getValue() + ", client " + (clientVersion == null ? "missing" : clientVersion) + ")");
            }
        }
    }

    private static String namespaceOf(String channel) {
        int index = channel.indexOf(':');
        return index <= 0 ? "" : channel.substring(0, index).toLowerCase(Locale.ROOT);
    }

    public static String formatFailure(CheckResult result, String playerName) {
        if (!result.configurationErrors.isEmpty()) {
            return "[ModChecker] Server configuration is invalid; contact the administrator.";
        }
        if (result.manifestMissingFailure) {
            return "[ModChecker] Server mod manifest is required but not available.";
        }
        if (result.hashManifestMissingFailure) {
            return CheckerConfig.hashManifestMissingMessage();
        }
        return CheckerConfig.kickMessage()
                .replace("%player%", playerName)
                .replace("%banned%", formatList(result.banned))
                .replace("%missing%", formatList(result.missing))
                .replace("%extra%", formatList(result.extra))
                .replace("%versions%", formatList(result.versionMismatches))
                .replace("%hashes%", formatList(result.hashMismatches));
    }

    private static void addConfigurationError(CheckResult result, String label, String error) {
        if (error != null) {
            result.configurationErrors.add(label + ": " + error);
        }
    }

    public static String formatList(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "无";
        }
        if (values.size() <= 12) {
            return String.join(", ", values);
        }
        return String.join(", ", values.subList(0, 12)) + " ... (+" + (values.size() - 12) + ")";
    }

    private static void sort(CheckResult result) {
        result.banned.sort(String.CASE_INSENSITIVE_ORDER);
        result.missing.sort(String.CASE_INSENSITIVE_ORDER);
        result.extra.sort(String.CASE_INSENSITIVE_ORDER);
        result.versionMismatches.sort(String.CASE_INSENSITIVE_ORDER);
        result.hashMismatches.sort(String.CASE_INSENSITIVE_ORDER);
    }

    public static final class CheckResult {
        public final List<String> configurationErrors = new ArrayList<>();
        public final List<String> banned = new ArrayList<>();
        public final List<String> missing = new ArrayList<>();
        public final List<String> extra = new ArrayList<>();
        public final List<String> versionMismatches = new ArrayList<>();
        public final List<String> hashMismatches = new ArrayList<>();
        public boolean manifestMissing;
        public boolean manifestMissingFailure;
        public boolean hashManifestMissingFailure;

        public boolean hasFailure() {
            return !configurationErrors.isEmpty()
                    || manifestMissingFailure
                    || hashManifestMissingFailure
                    || !banned.isEmpty()
                    || !missing.isEmpty()
                    || !extra.isEmpty()
                    || !versionMismatches.isEmpty()
                    || !hashMismatches.isEmpty();
        }
    }
}
