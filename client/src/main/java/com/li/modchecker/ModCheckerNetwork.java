package com.li.modchecker;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Client companion: receives the login request and replies with the report. */
@SuppressWarnings("removal")
public final class ModCheckerNetwork {
    public static final ResourceLocation CHANNEL_NAME = new ResourceLocation(ModCheckerMod.REPORT_CHANNEL);
    public static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_NAME, () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;

    private ModCheckerNetwork() {
    }

    public static synchronized void register() {
        if (registered) {
            return;
        }
        CHANNEL.messageBuilder(LoginRequestMessage.class, 1, NetworkDirection.LOGIN_TO_CLIENT)
                .loginIndex(LoginRequestMessage::getAsInt, LoginRequestMessage::setLoginIndex)
                .encoder(LoginRequestMessage::encode)
                .decoder(LoginRequestMessage::decode)
                .markAsLoginPacket()
                .consumerNetworkThread(ClientHashReporter::onRequest)
                .add();
        // Registered so SimpleChannel.reply can encode the report; no client-side consumer is needed.
        CHANNEL.messageBuilder(HashReportMessage.class, 0, NetworkDirection.LOGIN_TO_SERVER)
                .loginIndex(HashReportMessage::getAsInt, HashReportMessage::setLoginIndex)
                .encoder(HashReportMessage::encode)
                .decoder(HashReportMessage::decode)
                .add();
        registered = true;
    }
}
