package com.li.modchecker;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * COMMON config, written to {@code config/modchecker-common.toml} on the server.
 */
public final class CheckerConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BANNED_MODS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> IGNORED_MODS;
    public static final ForgeConfigSpec.ConfigValue<String> KICK_MESSAGE;
    public static final ForgeConfigSpec.ConfigValue<String> COMPANION_KICK_MESSAGE;
    public static final ForgeConfigSpec.ConfigValue<String> HASH_TIMEOUT_MESSAGE;
    public static final ForgeConfigSpec.ConfigValue<String> HASH_MANIFEST_MISSING_MESSAGE;
    public static final ForgeConfigSpec.ConfigValue<String> HANDSHAKE_TIMEOUT_MESSAGE;
    public static final ForgeConfigSpec.ConfigValue<String> HANDSHAKE_BUSY_MESSAGE;
    public static final ForgeConfigSpec.BooleanValue STRICT_MOD_LIST;
    public static final ForgeConfigSpec.BooleanValue CHECK_CHANNEL_VERSIONS;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_MANIFEST;
    public static final ForgeConfigSpec.BooleanValue REQUIRE_HASH_MANIFEST;
    public static final ForgeConfigSpec.BooleanValue CHECK_COMPANION_HASH;
    public static final ForgeConfigSpec.BooleanValue ONLY_DEDICATED_SERVER;
    public static final ForgeConfigSpec.BooleanValue LAN_ENFORCEMENT;
    public static final ForgeConfigSpec.BooleanValue WHITELIST_ALLOWS_MISSING;
    public static final ForgeConfigSpec.BooleanValue EXEMPT_OPS;
    public static final ForgeConfigSpec.BooleanValue LOG_REJECTIONS;
    public static final ForgeConfigSpec.BooleanValue LOG_LOGIN_PHASES;
    public static final ForgeConfigSpec.IntValue HASH_TIMEOUT_TICKS;
    public static final ForgeConfigSpec.IntValue HANDSHAKE_TIMEOUT_TICKS;
    public static final ForgeConfigSpec.IntValue TOTAL_LOGIN_TIMEOUT_TICKS;
    public static final ForgeConfigSpec.IntValue MAX_PENDING_LOGINS;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("Mod Checker - server-side mod list / hash verification.",
                        "Edit this file on the server and restart the server (or use /modchecker reload for the manifest files).")
                .push("checker");

        BANNED_MODS = builder
                .comment("Client mod ids that are always rejected, even if they are present in the manifest/whitelist.",
                        "Default: tweakeroo")
                .defineList("bannedMods", List.of("tweakeroo"), o -> o instanceof String);

        IGNORED_MODS = builder
                .comment("Mod ids ignored when comparing the client list with the manifest/hash manifest.",
                        "Use this for client-side cosmetic/UI mods that are allowed to vary between players.")
                .defineList("ignoredMods", List.of(), o -> o instanceof String);

        KICK_MESSAGE = builder
                .comment("Message shown to rejected players.",
                        "Placeholders: %player%, %banned%, %missing%, %extra%, %versions%, %hashes%",
                        "An empty list is rendered as '无' (none).")
                .define("kickMessage", "\u00a7c客户端模组与服务器清单不一致，已被拒绝进入。\n\u00a77被禁模组：\u00a7f%banned%\n\u00a77缺少模组：\u00a7f%missing%\n\u00a77多余模组：\u00a7f%extra%\n\u00a77频道版本不一致：\u00a7f%versions%\n\u00a77文件哈希不一致：\u00a7f%hashes%\n\u00a77请联系管理员获取与服务器一致的整合包。");

        COMPANION_KICK_MESSAGE = builder
                .comment("Message shown when the Mod Checker client companion is missing.",
                        "Placeholders: %player%")
                .define("companionKickMessage", "\u00a7c进入本服务器必须安装 Mod Checker 客户端校验组件（与服务端相同的 jar）。\n\u00a77请把 Mod Checker 客户端 jar（或完整服务端 jar）放进客户端 mods 后重连。");

        HASH_TIMEOUT_MESSAGE = builder
                .comment("Message shown when the companion is present but no hash report arrives in time.",
                        "Placeholders: %player%")
                .define("hashTimeoutMessage", "\u00a7c未能在规定时间内收到客户端哈希校验数据。\n\u00a77请确认 Mod Checker 客户端组件正常加载后重连。");

        HASH_MANIFEST_MISSING_MESSAGE = builder
                .comment("Message shown when requireHashManifest=true and the hash manifest is missing/invalid.")
                .define("hashManifestMissingMessage", "\u00a7c服务器尚未配置哈希清单（modchecker-hashes.json），请联系管理员。");

        HANDSHAKE_TIMEOUT_MESSAGE = builder
                .comment("Message shown when the Forge login data does not become ready in time (before the report timeout starts).",
                        "Placeholders: %player%")
                .define("handshakeTimeoutMessage", "\u00a7c登录准备超时：服务器尚未收到完整的 Forge 登录数据，请稍后重连。");

        HANDSHAKE_BUSY_MESSAGE = builder
                .comment("Message shown when the number of pending login verifications reaches maxPendingLogins.")
                .define("handshakeBusyMessage", "\u00a7c服务器登录校验队列已满，请稍后重连。");

        STRICT_MOD_LIST = builder
                .comment("Require the client's mod id set to exactly match the manifest (missing/extra mods are rejected).",
                        "Extras listed in config/modchecker-whitelist.txt are allowed.")
                .define("strictModList", true);

        CHECK_CHANNEL_VERSIONS = builder
                .comment("Compare the client's Forge network channel versions with the versions recorded in the manifest.")
                .define("checkChannelVersions", true);

        REQUIRE_MANIFEST = builder
                .comment("Reject players while the mod manifest file is missing or invalid.")
                .define("requireManifest", false);

        REQUIRE_HASH_MANIFEST = builder
                .comment("Reject players while the hash manifest file is missing or invalid.",
                        "Keep this false until you have generated modchecker-hashes.json with /modchecker generate-hashes.")
                .define("requireHashManifest", false);

        CHECK_COMPANION_HASH = builder
                .comment("Compare the SHA-256 of the Mod Checker companion jar itself.",
                        "Keep false when clients may use either the full/server jar or the minimal client jar; their own jar hash differs.",
                        "Only enable if all clients use exactly the same companion jar.")
                .define("checkCompanionHash", false);

        HASH_TIMEOUT_TICKS = builder
                .comment("Report response timeout, starting only after the Forge login data is ready (20 ticks = 1 second).",
                        "Large mod packs can take a few seconds to hash on the client; increase this if needed.",
                        "Each phase is an upper bound; real deadlines are capped by totalLoginTimeoutTicks and still must fit within vanilla's ~600-tick login timeout.")
                .defineInRange("hashTimeoutTicks", 400, 20, 6000);

        HANDSHAKE_TIMEOUT_TICKS = builder
                .comment("Per-phase upper bound for waiting on Forge login data (20 ticks = 1 second).",
                        "The report timeout only begins once the Forge mod list is available.",
                        "The effective handshake deadline is also capped by totalLoginTimeoutTicks.")
                .defineInRange("handshakeTimeoutTicks", 400, 20, 600);

        TOTAL_LOGIN_TIMEOUT_TICKS = builder
                .comment("Overall LOGIN budget shared by handshake preparation and report response (20 ticks = 1 second).",
                        "Vanilla disconnects at about 600 ticks; keep a margin for authentication and other login work.",
                        "This budget starts when Mod Checker begins login negotiation; it does not include TCP setup or external authentication.",
                        "The report deadline is capped by this budget; per-phase values are upper bounds, not additive promises.")
                .defineInRange("totalLoginTimeoutTicks", 500, 100, 600);

        MAX_PENDING_LOGINS = builder
                .comment("Maximum number of connections that may wait for login verification at the same time.",
                        "Connections above this limit are rejected immediately to protect server resources.")
                .defineInRange("maxPendingLogins", 64, 1, 1024);

        ONLY_DEDICATED_SERVER = builder
                .comment("When lanEnforcement=false, only enforce content policy on dedicated servers.",
                        "Protocol 2 and report exchange are still required for every peer.")
                .define("onlyDedicatedServer", true);

        LAN_ENFORCEMENT = builder
                .comment("Enforce content policy on integrated/LAN servers too (PCL LAN, EasyTier, tunneled LAN worlds).",
                        "Remote LAN clients must install the Mod Checker companion; the host player is not affected.",
                        "A tunnel is transport-transparent; this option changes whether policy runs on non-dedicated servers, not an address match.")
                .define("lanEnforcement", true);

        WHITELIST_ALLOWS_MISSING = builder
                .comment("Treat whitelisted mod ids as optional: clients may be missing them, or have a different jar/hash/channel.",
                        "This is what /modchecker whitelist add is intended for: add an optional mod so clients without it can join.",
                        "Set false to restore the old strict behavior where whitelist only permits extra mods.")
                .define("whitelistAllowsMissing", true);

        EXEMPT_OPS = builder
                .comment("Skip content policy for server operators with permission level 2; matching protocol 2 is still required.")
                .define("exemptOps", false);

        LOG_REJECTIONS = builder
                .comment("Log rejected players and details to the server console.")
                .define("logRejections", true);

        LOG_LOGIN_PHASES = builder
                .comment("Log login-phase timings (negotiation, Forge data ready, report received, accepted).",
                        "Useful for diagnosing intermittent login stalls; no tokens or credentials are logged.")
                .define("logLoginPhases", false);

        builder.pop();
        SPEC = builder.build();
    }

    private CheckerConfig() {
    }

    public static List<String> bannedModsLower() {
        return lowerList(BANNED_MODS, List.of("tweakeroo"));
    }

    public static Set<String> ignoredModsLower() {
        return new LinkedHashSet<>(lowerList(IGNORED_MODS, List.of()));
    }

    public static String kickMessage() {
        try {
            return KICK_MESSAGE.get();
        } catch (IllegalStateException e) {
            return "§cClient mods mismatch: banned=%banned% missing=%missing% extra=%extra% versions=%versions% hashes=%hashes%";
        }
    }

    public static String companionKickMessage() {
        try {
            return COMPANION_KICK_MESSAGE.get();
        } catch (IllegalStateException e) {
            return "§cThe Mod Checker client companion is required.";
        }
    }

    public static String hashTimeoutMessage() {
        try {
            return HASH_TIMEOUT_MESSAGE.get();
        } catch (IllegalStateException e) {
            return "§cTimed out waiting for the client hash report.";
        }
    }

    public static String hashManifestMissingMessage() {
        try {
            return HASH_MANIFEST_MISSING_MESSAGE.get();
        } catch (IllegalStateException e) {
            return "§cThe server hash manifest is missing.";
        }
    }

    public static boolean strictModList() {
        try { return STRICT_MOD_LIST.get(); } catch (IllegalStateException e) { return true; }
    }

    public static boolean checkChannelVersions() {
        try { return CHECK_CHANNEL_VERSIONS.get(); } catch (IllegalStateException e) { return true; }
    }

    public static boolean requireManifest() {
        try { return REQUIRE_MANIFEST.get(); } catch (IllegalStateException e) { return false; }
    }

    public static boolean requireHashManifest() {
        try { return REQUIRE_HASH_MANIFEST.get(); } catch (IllegalStateException e) { return false; }
    }

    public static boolean checkCompanionHash() {
        try { return CHECK_COMPANION_HASH.get(); } catch (IllegalStateException e) { return true; }
    }

    public static int hashTimeoutTicks() {
        try { return HASH_TIMEOUT_TICKS.get(); } catch (IllegalStateException e) { return 400; }
    }

    public static int handshakeTimeoutTicks() {
        try { return HANDSHAKE_TIMEOUT_TICKS.get(); } catch (IllegalStateException e) { return 400; }
    }

    public static int totalLoginTimeoutTicks() {
        try { return TOTAL_LOGIN_TIMEOUT_TICKS.get(); } catch (IllegalStateException e) { return 500; }
    }

    public static int maxPendingLogins() {
        try { return MAX_PENDING_LOGINS.get(); } catch (IllegalStateException e) { return 64; }
    }

    public static String handshakeTimeoutMessage() {
        try { return HANDSHAKE_TIMEOUT_MESSAGE.get(); } catch (IllegalStateException e) {
            return "§cTimed out waiting for Forge login data.";
        }
    }

    public static String handshakeBusyMessage() {
        try { return HANDSHAKE_BUSY_MESSAGE.get(); } catch (IllegalStateException e) {
            return "§cToo many pending login verifications; try again later.";
        }
    }

    public static boolean onlyDedicatedServer() {
        try { return ONLY_DEDICATED_SERVER.get(); } catch (IllegalStateException e) { return true; }
    }

    public static boolean lanEnforcement() {
        try { return LAN_ENFORCEMENT.get(); } catch (IllegalStateException e) { return true; }
    }

    public static boolean whitelistAllowsMissing() {
        try { return WHITELIST_ALLOWS_MISSING.get(); } catch (IllegalStateException e) { return true; }
    }

    public static boolean exemptOps() {
        try { return EXEMPT_OPS.get(); } catch (IllegalStateException e) { return false; }
    }

    public static boolean logRejections() {
        try { return LOG_REJECTIONS.get(); } catch (IllegalStateException e) { return true; }
    }

    public static boolean logLoginPhases() {
        try { return LOG_LOGIN_PHASES.get(); } catch (IllegalStateException e) { return false; }
    }

    private static List<String> lowerList(ForgeConfigSpec.ConfigValue<List<? extends String>> value, List<String> fallback) {
        try {
            List<String> result = new ArrayList<>();
            for (String entry : value.get()) {
                if (entry != null && !entry.isBlank()) {
                    result.add(entry.trim().toLowerCase(Locale.ROOT));
                }
            }
            return result;
        } catch (IllegalStateException e) {
            return fallback;
        }
    }
}