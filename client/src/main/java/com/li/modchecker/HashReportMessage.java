package com.li.modchecker;

import net.minecraft.network.FriendlyByteBuf;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.IntSupplier;

/** Bounded, immutable report, accepted only as a response during the Forge login exchange. */
public final class HashReportMessage implements IntSupplier {
    public static final int MAX_MODS = 4096;
    public static final int MAX_REPORT_BYTES = 1024 * 1024;
    public final Map<String, ClientMod> mods;
    private int loginIndex;

    public HashReportMessage(Map<String, ClientMod> mods) {
        if (mods == null || mods.isEmpty() || mods.size() > MAX_MODS) {
            throw new IllegalArgumentException("Invalid mod count in report");
        }
        Map<String, ClientMod> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ClientMod> entry : mods.entrySet()) {
            ClientValidation.modId(entry.getKey());
            ClientMod mod = entry.getValue();
            if (mod == null || mod.size < -1) {
                throw new IllegalArgumentException("Invalid mod report entry: " + entry.getKey());
            }
            validateText(mod.name, 512);
            validateText(mod.version, 256);
            validateText(mod.sha256, 256);
            copy.put(entry.getKey(), mod);
        }
        this.mods = Collections.unmodifiableMap(copy);
    }

    private static void validateText(String value, int maxLength) {
        if (value == null || value.length() > maxLength
                || value.chars().anyMatch(c -> c < 32 || c == 127)) {
            throw new IllegalArgumentException("Invalid text in mod report");
        }
    }

    public static void encode(HashReportMessage message, FriendlyByteBuf buffer) {
        int start = buffer.writerIndex();
        buffer.writeVarInt(message.mods.size());
        for (Map.Entry<String, ClientMod> entry : message.mods.entrySet()) {
            buffer.writeUtf(entry.getKey(), 64);
            ClientMod mod = entry.getValue();
            buffer.writeUtf(mod.name, 512);
            buffer.writeUtf(mod.version, 256);
            buffer.writeUtf(mod.sha256, 256);
            buffer.writeLong(mod.size);
            if (buffer.writerIndex() - start > MAX_REPORT_BYTES) {
                throw new IllegalArgumentException("Mod report exceeds byte limit");
            }
        }
    }

    public static HashReportMessage decode(FriendlyByteBuf buffer) {
        if (buffer.readableBytes() > MAX_REPORT_BYTES) {
            throw new IllegalArgumentException("Mod report exceeds byte limit");
        }
        int count = buffer.readVarInt();
        if (count <= 0 || count > MAX_MODS) {
            throw new IllegalArgumentException("Invalid mod count in report: " + count);
        }
        Map<String, ClientMod> mods = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String id = buffer.readUtf(64);
            String name = buffer.readUtf(512);
            String version = buffer.readUtf(256);
            String sha256 = buffer.readUtf(256);
            long size = buffer.readLong();
            if (mods.putIfAbsent(id, new ClientMod(name, version, sha256, size)) != null) {
                throw new IllegalArgumentException("Duplicate mod id in report: " + id);
            }
        }
        if (buffer.isReadable()) {
            throw new IllegalArgumentException("Trailing bytes in mod report");
        }
        return new HashReportMessage(mods);
    }

    @Override
    public int getAsInt() {
        return loginIndex;
    }

    public void setLoginIndex(int loginIndex) {
        this.loginIndex = loginIndex;
    }

    public static final class ClientMod {
        public final String name;
        public final String version;
        public final String sha256;
        public final long size;

        public ClientMod(String name, String version, String sha256, long size) {
            this.name = name;
            this.version = version;
            this.sha256 = sha256;
            this.size = size;
        }
    }
}
