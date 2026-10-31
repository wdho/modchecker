package com.li.modchecker;

import com.mojang.logging.LogUtils;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerNegotiationEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.slf4j.Logger;

/** Mod Checker: mandatory protocol-2 login validation on both client and server. */
@Mod(ModCheckerMod.MOD_ID)
public final class ModCheckerMod {
    public static final String MOD_ID = "modchecker";
    public static final String REPORT_CHANNEL = MOD_ID + ":hash";
    private static final Logger LOGGER = LogUtils.getLogger();

    @SuppressWarnings("removal")
    public ModCheckerMod() {
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, CheckerConfig.SPEC);
        ModCheckerNetwork.register();

        MinecraftForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> {
            ManifestManager.load();
            HashManifestManager.load();
            WhitelistManager.load();
        });
        MinecraftForge.EVENT_BUS.addListener((PlayerNegotiationEvent event) ->
                HashVerificationManager.onNegotiation(event));
        MinecraftForge.EVENT_BUS.addListener((PlayerEvent.PlayerLoggedOutEvent event) -> {
            if (event.getEntity() instanceof ServerPlayer player) {
                HashVerificationManager.onLogout(player);
            }
        });
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ServerTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                HashVerificationManager.tick(event.getServer());
            }
        });
        MinecraftForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> HashVerificationManager.clearAll());
        MinecraftForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> CheckerCommands.register(event));
        LOGGER.info("[ModChecker] Protocol {} loaded. Mod manifest: {} | Hash manifest: {} | Whitelist: {}",
                ModCheckerNetwork.PROTOCOL, ManifestManager.path(), HashManifestManager.path(), WhitelistManager.path());
    }
}
