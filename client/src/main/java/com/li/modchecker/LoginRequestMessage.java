package com.li.modchecker;

import net.minecraft.network.FriendlyByteBuf;

import java.util.function.IntSupplier;

/** A Forge-indexed login request. The report is returned through this request's dispatcher. */
public final class LoginRequestMessage implements IntSupplier {
    private int loginIndex;

    public LoginRequestMessage() {
    }

    public static void encode(LoginRequestMessage message, FriendlyByteBuf buffer) {
    }

    public static LoginRequestMessage decode(FriendlyByteBuf buffer) {
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("Unexpected data in Mod Checker login request");
        }
        return new LoginRequestMessage();
    }

    @Override
    public int getAsInt() {
        return loginIndex;
    }

    public void setLoginIndex(int loginIndex) {
        this.loginIndex = loginIndex;
    }
}
