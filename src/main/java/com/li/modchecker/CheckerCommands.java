package com.li.modchecker;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.forgespi.language.IModInfo;
import net.minecraftforge.network.ConnectionData;
import net.minecraftforge.network.NetworkHooks;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CompletableFuture;

/**
 * Admin commands:
 * <ul>
 *   <li>{@code /modchecker status}</li>
 *   <li>{@code /modchecker generate [player]} - mod id manifest</li>
 *   <li>{@code /modchecker generate-hashes [player]} - SHA-256 manifest</li>
 *   <li>{@code /modchecker reload} - reload manifest + hash manifest + whitelist</li>
 *   <li>{@code /modchecker mods [player]}</li>
 *   <li>{@code /modchecker check [player]}</li>
 *   <li>{@code /modchecker whitelist}</li>
 *   <li>{@code /modchecker whitelist add <modid>}</li>
 * </ul>
 */
public final class CheckerCommands {

    private CheckerCommands() {
    }

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("modchecker")
                .requires(source -> source.hasPermission(2))
                .executes(context -> status(context.getSource()))
                .then(Commands.literal("status")
                        .executes(context -> status(context.getSource())))
                .then(Commands.literal("reload")
                        .executes(context -> reload(context.getSource())))
                .then(Commands.literal("generate")
                        .executes(context -> generate(context, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> generate(context, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("generate-hashes")
                        .executes(context -> generateHashes(context, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> generateHashes(context, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("mods")
                        .executes(context -> mods(context, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> mods(context, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("check")
                        .executes(context -> check(context, null))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(context -> check(context, EntityArgument.getPlayer(context, "player")))))
                .then(Commands.literal("whitelist")
                        .executes(context -> whitelist(context.getSource()))
                        .then(Commands.literal("add")
                                .then(Commands.argument("modid", StringArgumentType.greedyString())
                                        .suggests(CheckerCommands::suggestWhitelistIds)
                                        .executes(context -> whitelistAdd(context.getSource(),
                                                StringArgumentType.getString(context, "modid")))))));
    }

    private static ServerPlayer target(CommandContext<CommandSourceStack> context, ServerPlayer explicit) {
        if (explicit != null) {
            return explicit;
        }
        Entity entity = context.getSource().getEntity();
        return entity instanceof ServerPlayer player ? player : null;
    }

    private static int status(CommandSourceStack source) {
        ManifestData manifest = ManifestManager.current();
        HashManifestData hashes = HashManifestManager.current();

        source.sendSuccess(() -> Component.literal("§e[ModChecker] §7mod manifest: §f" + ManifestManager.path()), false);
        if (manifest == null) {
            String error = ManifestManager.loadError();
            source.sendSuccess(() -> Component.literal(error == null
                    ? "§c[ModChecker] mod manifest not loaded (missing file)"
                    : "§c[ModChecker] mod manifest not loaded: " + error), false);
        } else {
            source.sendSuccess(() -> Component.literal("§7[ModChecker] mod manifest: §f" + manifest.mods.size()
                    + " §7entries, §f" + ManifestManager.expectedClientIds(manifest).size()
                    + " §7expected client mods, §f" + manifest.channelVersions.size() + " §7channel versions"), false);
        }

        source.sendSuccess(() -> Component.literal("§e[ModChecker] §7hash manifest: §f" + HashManifestManager.path()), false);
        if (hashes == null) {
            String error = HashManifestManager.loadError();
            source.sendSuccess(() -> Component.literal(error == null
                    ? "§c[ModChecker] hash manifest not loaded (missing file)"
                    : "§c[ModChecker] hash manifest not loaded: " + error), false);
        } else {
            source.sendSuccess(() -> Component.literal("§7[ModChecker] hash manifest: §f" + hashes.mods.size() + " §7SHA-256 entries"), false);
        }

        source.sendSuccess(() -> Component.literal("§7[ModChecker] whitelist: §f" + WhitelistManager.path()
                + " §7(" + WhitelistManager.size() + " entries)"), false);
        if (WhitelistManager.loadError() != null) {
            source.sendFailure(Component.literal("[ModChecker] whitelist error: " + WhitelistManager.loadError()));
        }
        source.sendSuccess(() -> Component.literal("§7[ModChecker] strictModList=§f" + CheckerConfig.strictModList()
                + " §7checkChannelVersions=§f" + CheckerConfig.checkChannelVersions()
                + " §7protocol=§f" + ModCheckerNetwork.PROTOCOL
                + " §7requireHashManifest=§f" + CheckerConfig.requireHashManifest()
                + " §7hashTimeoutTicks=§f" + CheckerConfig.hashTimeoutTicks()), false);
        source.sendSuccess(() -> Component.literal("§7[ModChecker] bannedMods=§f" + CheckerConfig.bannedModsLower()), false);
        if (ManifestManager.serverVersionMismatchCount() > 0) {
            source.sendSuccess(() -> Component.literal("§c[ModChecker] " + ManifestManager.serverVersionMismatchCount()
                    + " manifest/server version mismatch(es) - check the server log and regenerate the manifest."), false);
        }
        return manifest == null ? 0 : manifest.mods.size();
    }

    private static int reload(CommandSourceStack source) {
        ManifestManager.load();
        HashManifestManager.load();
        WhitelistManager.load();
        ManifestData manifest = ManifestManager.current();
        HashManifestData hashes = HashManifestManager.current();
        boolean invalid = ManifestManager.loadError() != null || HashManifestManager.loadError() != null
                || WhitelistManager.loadError() != null;
        source.sendSuccess(() -> Component.literal((invalid ? "§c[ModChecker] reload failed: mods=" : "§a[ModChecker] reloaded: mods=")
                + (manifest == null ? "missing" : manifest.mods.size() + " entries")
                + ", hashes=" + (hashes == null ? "missing" : hashes.mods.size() + " entries")
                + ", whitelist=" + WhitelistManager.size() + " entries"), false);
        if (manifest == null && ManifestManager.loadError() != null) {
            source.sendFailure(Component.literal("[ModChecker] mod manifest error: " + ManifestManager.loadError()));
        }
        if (hashes == null && HashManifestManager.loadError() != null) {
            source.sendFailure(Component.literal("[ModChecker] hash manifest error: " + HashManifestManager.loadError()));
        }
        if (WhitelistManager.loadError() != null) {
            source.sendFailure(Component.literal("[ModChecker] whitelist error: " + WhitelistManager.loadError()));
        }
        if (invalid) {
            return 0;
        }
        return (manifest == null ? 0 : 1) + (hashes == null ? 0 : 1);
    }

    private static int generate(CommandContext<CommandSourceStack> context, ServerPlayer explicit) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = target(context, explicit);
        if (player == null) {
            source.sendFailure(Component.literal("[ModChecker] Run as a player, or use /modchecker generate <player>."));
            return 0;
        }
        try {
            ManifestData manifest = ManifestManager.generate(player);
            source.sendSuccess(() -> Component.literal("§a[ModChecker] mod manifest generated from §f"
                    + player.getGameProfile().getName() + "§a: §f" + manifest.mods.size()
                    + "§a entries, §f" + ManifestManager.expectedClientIds(manifest).size()
                    + "§a expected client mods, §f" + manifest.channelVersions.size()
                    + "§a channel versions -> §f" + ManifestManager.path()), false);
            return manifest.mods.size();
        } catch (Exception e) {
            source.sendFailure(Component.literal("[ModChecker] generate failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int generateHashes(CommandContext<CommandSourceStack> context, ServerPlayer explicit) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = target(context, explicit);
        if (player == null) {
            source.sendFailure(Component.literal("[ModChecker] Run as a player, or use /modchecker generate-hashes <player>."));
            return 0;
        }
        if (HashVerificationManager.report(player) == null) {
            source.sendFailure(Component.literal("[ModChecker] No hash report received from "
                    + player.getGameProfile().getName()
                    + " yet. Make sure the client companion is installed and wait a few seconds, then retry."));
            return 0;
        }
        try {
            HashManifestData data = HashManifestManager.generate(player);
            source.sendSuccess(() -> Component.literal("§a[ModChecker] hash manifest generated from §f"
                    + player.getGameProfile().getName() + "§a: §f" + data.mods.size()
                    + "§a SHA-256 entries -> §f" + HashManifestManager.path()), false);
            return data.mods.size();
        } catch (Exception e) {
            source.sendFailure(Component.literal("[ModChecker] generate-hashes failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int mods(CommandContext<CommandSourceStack> context, ServerPlayer explicit) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = target(context, explicit);
        if (player == null) {
            source.sendFailure(Component.literal("[ModChecker] Run as a player, or use /modchecker mods <player>."));
            return 0;
        }
        ConnectionData data = NetworkHooks.getConnectionData(player.connection.connection);
        if (data == null) {
            source.sendFailure(Component.literal("[ModChecker] No Forge handshake data for " + player.getGameProfile().getName() + "."));
            return 0;
        }
        TreeSet<String> ids = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        ids.addAll(data.getModList());
        boolean hasReport = HashVerificationManager.report(player) != null;
        source.sendSuccess(() -> Component.literal("§e[ModChecker] client mods of §f" + player.getGameProfile().getName()
                + "§e: §f" + ids.size() + "§e, channels: §f" + data.getChannels().size()
                + "§e, hash report: §f" + (hasReport ? "received" : "not received")), false);
        List<String> all = new ArrayList<>(ids);
        int chunkSize = 16;
        for (int start = 0; start < all.size(); start += chunkSize) {
            int end = Math.min(all.size(), start + chunkSize);
            String line = String.join(", ", all.subList(start, end));
            source.sendSuccess(() -> Component.literal("§7" + line), false);
        }
        return all.size();
    }

    private static int check(CommandContext<CommandSourceStack> context, ServerPlayer explicit) {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = target(context, explicit);
        if (player == null) {
            source.sendFailure(Component.literal("[ModChecker] Run as a player, or use /modchecker check <player>."));
            return 0;
        }
        ConnectionData data = NetworkHooks.getConnectionData(player.connection.connection);
        if (data == null) {
            source.sendFailure(Component.literal("[ModChecker] No Forge handshake data for " + player.getGameProfile().getName() + "."));
            return 0;
        }
        HashReportMessage report = HashVerificationManager.report(player);
        if (report == null) {
            source.sendFailure(Component.literal("[ModChecker] No verified report for this connection."));
            return 0;
        }
        LoginChecker.CheckResult result = LoginChecker.checkWithReport(data, report);
        source.sendSuccess(() -> Component.literal("§e[ModChecker] check of §f" + player.getGameProfile().getName()
                + "§e: §f" + (result.hasFailure() ? "§cFAIL" : "§aPASS")
                + " §7(manifest + verified connection report)"), false);
        if (result.manifestMissing) {
            source.sendSuccess(() -> Component.literal("§7mod manifest: §f" + (result.manifestMissingFailure ? "missing (rejected)" : "missing")), false);
        }
        if (result.hashManifestMissingFailure) {
            source.sendSuccess(() -> Component.literal("§7hash manifest: §fmissing (rejected)"), false);
        }
        sendResultLine(source, "configuration errors", result.configurationErrors);
        sendResultLine(source, "banned", result.banned);
        sendResultLine(source, "missing", result.missing);
        sendResultLine(source, "extra", result.extra);
        sendResultLine(source, "version mismatch", result.versionMismatches);
        sendResultLine(source, "hash mismatch", result.hashMismatches);
        return result.hasFailure() ? 0 : 1;
    }

    private static CompletableFuture<Suggestions> suggestWhitelistIds(
            CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
        CommandSourceStack source = context.getSource();
        String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
        Set<String> candidates = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        collectModIds(source.getEntity() instanceof ServerPlayer player ? player : null, candidates);
        MinecraftServer server = source.getServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                collectModIds(player, candidates);
            }
        }

        if (ManifestManager.current() != null) {
            candidates.addAll(ManifestManager.expectedClientIds(ManifestManager.current()));
        }
        List<String> banned = CheckerConfig.bannedModsLower();
        Set<String> existing = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        existing.addAll(WhitelistManager.entries());
        for (String id : candidates) {
            if (candidateAllowed(id, banned, existing) && id.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(id);
            }
        }
        for (IModInfo info : ModList.get().getMods()) {
            String id = info.getModId();
            if (candidateAllowed(id, banned, existing)
                    && id.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(id);
            }
            String fileName = modFileName(info);
            if (fileName != null
                    && candidateAllowed(id, banned, existing)
                    && fileName.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(fileName);
            }
            String displayName = info.getDisplayName();
            if (displayName != null
                    && candidateAllowed(id, banned, existing)
                    && displayName.toLowerCase(Locale.ROOT).startsWith(remaining)) {
                builder.suggest(displayName);
            }
        }
        return builder.buildFuture();
    }

    private static boolean candidateAllowed(String id, List<String> banned, Set<String> existing) {
        return !banned.contains(id) && !existing.contains(id);
    }

    private static String modFileName(IModInfo info) {
        try {
            return info.getOwningFile().getFile().getFilePath().getFileName().toString();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static String resolveWhitelistValue(String input) {
        String value = input == null ? "" : input.trim();
        if (value.isEmpty()) {
            throw new IllegalStateException("missing mod ID or jar filename");
        }
        try {
            return ManifestValidation.modId(value);
        } catch (IllegalStateException ignored) {
            // Not an exact mod id; accept a loaded jar filename or unique display name below.
        }
        String matched = null;
        for (IModInfo info : ModList.get().getMods()) {
            String id = info.getModId();
            String fileName = modFileName(info);
            boolean hit = value.equalsIgnoreCase(id)
                    || (fileName != null && value.equalsIgnoreCase(fileName))
                    || value.equalsIgnoreCase(info.getDisplayName());
            if (hit) {
                if (matched != null && !matched.equalsIgnoreCase(id)) {
                    throw new IllegalStateException("ambiguous mod name '" + value + "', use the lowercase mod ID");
                }
                matched = id;
            }
        }
        if (matched == null) {
            throw new IllegalStateException("unknown mod id or loaded jar name: " + value
                    + " (use the lowercase mod ID; extra client-only mods may not be loaded on the server)");
        }
        return matched.toLowerCase(Locale.ROOT);
    }

    private static void collectModIds(ServerPlayer player, Set<String> target) {
        if (player == null || player.connection == null) {
            return;
        }
        ConnectionData data = NetworkHooks.getConnectionData(player.connection.connection);
        if (data != null) {
            target.addAll(data.getModList());
        }
    }

    private static int whitelistAdd(CommandSourceStack source, String input) {
        try {
            String value = resolveWhitelistValue(input);
            if (CheckerConfig.bannedModsLower().contains(value)) {
                source.sendFailure(Component.literal("[ModChecker] '" + value
                        + "' is in bannedMods; whitelisting cannot override the ban."));
                return 0;
            }
            boolean added = WhitelistManager.add(value);
            if (!added) {
                source.sendFailure(Component.literal("[ModChecker] '" + value + "' is already whitelisted."));
                return 0;
            }
            String shownInput = input == null ? "" : input.trim();
            source.sendSuccess(() -> Component.literal("§a[ModChecker] added whitelist mod id: §f"
                    + value + (shownInput.equalsIgnoreCase(value) ? "" : " §7(from " + shownInput + ")")
                    + "§a (" + WhitelistManager.size() + " entries) -> §f" + WhitelistManager.path()), true);
            return 1;
        } catch (IllegalStateException e) {
            source.sendFailure(Component.literal("[ModChecker] whitelist add failed: " + e.getMessage()));
            return 0;
        } catch (Exception e) {
            source.sendFailure(Component.literal("[ModChecker] whitelist add failed: " + e.getMessage()));
            return 0;
        }
    }

    private static int whitelist(CommandSourceStack source) {
        if (WhitelistManager.loadError() != null) {
            source.sendFailure(Component.literal("[ModChecker] whitelist error: " + WhitelistManager.loadError()));
            return 0;
        }
        List<String> entries = WhitelistManager.entries();
        entries.sort(String.CASE_INSENSITIVE_ORDER);
        source.sendSuccess(() -> Component.literal("§e[ModChecker] whitelist §f" + WhitelistManager.path()
                + "§e: §f" + entries.size() + "§e entries"), false);
        int chunkSize = 12;
        for (int start = 0; start < entries.size(); start += chunkSize) {
            int end = Math.min(entries.size(), start + chunkSize);
            String line = String.join(" | ", entries.subList(start, end));
            source.sendSuccess(() -> Component.literal("§7" + line), false);
        }
        return entries.size();
    }

    private static void sendResultLine(CommandSourceStack source, String label, List<String> values) {
        if (values.isEmpty()) {
            return;
        }
        String line = "§7" + label + ": §f" + LoginChecker.formatList(values);
        source.sendSuccess(() -> Component.literal(line), false);
    }
}
