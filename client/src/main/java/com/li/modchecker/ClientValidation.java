package com.li.modchecker;

import java.util.regex.Pattern;

final class ClientValidation {
    private static final Pattern MOD_ID = Pattern.compile("[a-z][a-z0-9_]{1,63}");

    private ClientValidation() {
    }

    static String modId(String value) {
        if (value == null || !MOD_ID.matcher(value).matches()) {
            throw new IllegalStateException("invalid mod id: " + value);
        }
        return value;
    }
}
