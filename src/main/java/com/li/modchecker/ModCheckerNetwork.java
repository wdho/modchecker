package com.li.modchecker;

import io.netty.util.AttributeKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/** Protocol 2 is a mandatory, login-only exchange. Both endpoints must be updated together. */
@SuppressWarnings("removal")
public final class ModCheckerNetwork {
    public static final ResourceLocation CHANNEL_NAME = new ResourceLocation(ModCheckerMod.REPORT_CHANNEL);
    public static final String PROTOCOL = "2";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            CHANNEL_NAME, () -> PROTOCOL, PROTOCOL::equals, PROTOCOL::equals);
    private static boolean registered;
    private static final AttributeKey<Boolean> RESPONSE_QUEUED = AttributeKey.valueOf("modchecker:response_queued");

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
        CHANNEL.messageBuilder(HashReportMessage.class, 0, NetworkDirection.LOGIN_TO_SERVER)
                .loginIndex(HashReportMessage::getAsInt, HashReportMessage::setLoginIndex)
                .encoder(HashReportMessage::encode)
                .decoder(HashReportMessage::decode)
                .consumerNetworkThread((message, supplier) -> {
                    NetworkEvent.Context context = supplier.get();
                    context.setPacketHandled(true);
                    if (context.getNetworkManager().channel().attr(RESPONSE_QUEUED).getAndSet(Boolean.TRUE) != null) {
                        HashVerificationManager.reject(context.getNetworkManager(), "[ModChecker] Duplicate login report");
                        return;
                    }
                    context.enqueueWork(() -> HashVerificationManager.onReport(message, supplier));
                })
                .add();
        registered = true;
    }
}
