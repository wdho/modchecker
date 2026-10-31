package com.li.modchecker;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Structural validation only; this class never computes or compares file digests. */
final class ManifestValidation {
    private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9_]{1,63}");
    private static final Pattern CHANNEL_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final int MAX_ENTRIES = 4096;
    private static final int MAX_JSON_CHARS = 16 * 1024 * 1024;

    private ManifestValidation() {
    }

    static String modId(String value) {
        if (value == null || !MOD_ID.matcher(value).matches()) {
            throw new IllegalStateException("invalid mod id: " + value + " (use the lowercase mod id, not a display name)");
        }
        return value;
    }

    static void validate(ManifestData data) {
        if (data == null || data.format != ManifestData.CURRENT_FORMAT) {
            throw new IllegalStateException("missing or unsupported mod manifest format");
        }
        if (data.mods == null || data.mods.isEmpty() || data.mods.size() > MAX_ENTRIES) {
            throw new IllegalStateException("mod manifest must contain 1.." + MAX_ENTRIES + " entries");
        }
        if (data.channelVersions == null || data.channelVersions.size() > MAX_ENTRIES) {
            throw new IllegalStateException("missing or oversized channelVersions object");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (ManifestData.ModEntry entry : data.mods) {
            if (entry == null) {
                throw new IllegalStateException("mod manifest contains a null entry");
            }
            String id = modId(entry.id);
            if (!ids.add(id.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("duplicate mod id: " + id);
            }
            if (!"both".equals(entry.side) && !"client".equals(entry.side) && !"server".equals(entry.side)) {
                throw new IllegalStateException("invalid side for mod " + id + ": " + entry.side);
            }
            text(entry.version, 256, "version for " + id);
        }
        Set<String> channels = new LinkedHashSet<>();
        for (Map.Entry<String, String> channel : data.channelVersions.entrySet()) {
            String id = channel.getKey();
            if (id == null || id.length() > 256 || !CHANNEL_ID.matcher(id).matches()) {
                throw new IllegalStateException("invalid channel id: " + id);
            }
            if (!channels.add(id.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("duplicate channel id: " + id);
            }
            text(channel.getValue(), 256, "channel version for " + id);
        }
    }

    static void validate(HashManifestData data) {
        if (data == null || data.format != HashManifestData.CURRENT_FORMAT) {
            throw new IllegalStateException("missing or unsupported hash manifest format");
        }
        if (data.mods == null || data.mods.isEmpty() || data.mods.size() > MAX_ENTRIES) {
            throw new IllegalStateException("hash manifest must contain 1.." + MAX_ENTRIES + " entries");
        }
        Set<String> ids = new LinkedHashSet<>();
        for (Map.Entry<String, HashManifestData.ModHash> entry : data.mods.entrySet()) {
            String id = modId(entry.getKey());
            if (!ids.add(id.toLowerCase(Locale.ROOT))) {
                throw new IllegalStateException("duplicate mod id: " + id);
            }
            HashManifestData.ModHash value = entry.getValue();
            if (value == null) {
                throw new IllegalStateException("null hash manifest entry for " + id);
            }
            // Validate the data container, not a digest's syntax or its correctness.
            text(value.sha256, 256, "reported fingerprint for " + id);
            if (value.size < -1) {
                throw new IllegalStateException("invalid file size for " + id);
            }
        }
    }

    static <T> T parse(Gson gson, String json, Class<T> type) throws IOException {
        if (json.length() > MAX_JSON_CHARS) {
            throw new IllegalStateException("manifest exceeds the JSON size limit");
        }
        try (JsonReader reader = new JsonReader(new StringReader(json))) {
            reader.setLenient(false);
            JsonElement element = readValue(reader, 0);
            if (reader.peek() != JsonToken.END_DOCUMENT || !element.isJsonObject()) {
                throw new IllegalStateException("manifest must contain one JSON object");
            }
            JsonObject object = element.getAsJsonObject();
            JsonElement format = object.get("format");
            if (format == null || !format.isJsonPrimitive() || !format.getAsJsonPrimitive().isNumber()) {
                throw new IllegalStateException("manifest requires an explicit numeric format");
            }
            format.getAsBigDecimal().intValueExact();
            JsonElement mods = object.get("mods");
            if (mods == null || (type == ManifestData.class ? !mods.isJsonArray() : !mods.isJsonObject())) {
                throw new IllegalStateException("missing or invalid mods collection");
            }
            if (type == ManifestData.class && (!object.has("channelVersions")
                    || !object.get("channelVersions").isJsonObject())) {
                throw new IllegalStateException("missing or invalid channelVersions object");
            }
            return gson.fromJson(element, type);
        }
    }

    private static JsonElement readValue(JsonReader reader, int depth) throws IOException {
        if (depth > 32) {
            throw new IllegalStateException("manifest JSON nesting is too deep");
        }
        switch (reader.peek()) {
            case BEGIN_OBJECT -> {
                JsonObject object = new JsonObject();
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    if (object.has(name)) {
                        throw new IllegalStateException("duplicate JSON property: " + name);
                    }
                    object.add(name, readValue(reader, depth + 1));
                }
                reader.endObject();
                return object;
            }
            case BEGIN_ARRAY -> {
                JsonArray array = new JsonArray();
                reader.beginArray();
                while (reader.hasNext()) {
                    array.add(readValue(reader, depth + 1));
                }
                reader.endArray();
                return array;
            }
            case STRING -> { return new JsonPrimitive(reader.nextString()); }
            case NUMBER -> { return new JsonPrimitive(new BigDecimal(reader.nextString())); }
            case BOOLEAN -> { return new JsonPrimitive(reader.nextBoolean()); }
            case NULL -> { reader.nextNull(); return JsonNull.INSTANCE; }
            default -> throw new IllegalStateException("invalid manifest JSON token: " + reader.peek());
        }
    }

    private static void text(String value, int maxLength, String label) {
        if (value == null || value.isBlank() || value.length() > maxLength
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw new IllegalStateException("missing or invalid " + label);
        }
    }
}
