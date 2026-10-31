package com.li.modchecker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Data model of {@code config/modchecker-manifest.json}.
 */
public final class ManifestData {

    public static final int CURRENT_FORMAT = 1;

    public int format = CURRENT_FORMAT;
    public String generatedAt;
    public String generatedBy;
    public List<ModEntry> mods = new ArrayList<>();
    public Map<String, String> channelVersions = new LinkedHashMap<>();

    public static final class ModEntry {
        public String id;
        public String version;
        public String side;

        public ModEntry() {
        }

        public ModEntry(String id, String version, String side) {
            this.id = id;
            this.version = version;
            this.side = side;
        }
    }
}
