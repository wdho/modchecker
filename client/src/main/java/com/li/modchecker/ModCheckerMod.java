package com.li.modchecker;

import com.mojang.logging.LogUtils;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Minimal client companion: computes and sends the login report only. */
@Mod(ModCheckerMod.MOD_ID)
public final class ModCheckerMod {
    public static final String MOD_ID = "modchecker";
    public static final String REPORT_CHANNEL = MOD_ID + ":hash";
    private static final Logger LOGGER = LogUtils.getLogger();

    public ModCheckerMod() {
        ModCheckerNetwork.register();
        LOGGER.info("[ModChecker] Client companion loaded (protocol {}); no server commands/manifests are present.", ModCheckerNetwork.PROTOCOL);
    }
}
