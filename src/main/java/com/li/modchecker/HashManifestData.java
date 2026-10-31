package com.li.modchecker;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Data model of {@code config/modchecker-hashes.json}.
 */
public final class HashManifestData {

    public static final int CURRENT_FORMAT = 1;

    public int format = CURRENT_FORMAT;
    public String generatedAt;
    public String generatedBy;
    public Map<String, ModHash> mods = new LinkedHashMap<>();

    public static final class ModHash {
        public String sha256;
        public long size = -1;
        public String name;
        public String version;

        public ModHash() {
        }

        public ModHash(String sha256, long size, String name, String version) {
            this.sha256 = sha256;
            this.size = size;
            this.name = name;
            this.version = version;
        }
    }
}
