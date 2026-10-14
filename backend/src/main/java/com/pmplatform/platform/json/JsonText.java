package com.pmplatform.platform.json;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds small, flat JSON objects for audit payloads. Field order is kept, so the
 * same change always produces the same text (and the same audit hash).
 */
public final class JsonText {

    private JsonText() {
    }

    public static String object(Object... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException("Expected key/value pairs");
        }
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> e : map.entrySet()) {
            if (!first) {
                out.append(',');
            }
            first = false;
            out.append('"').append(escape(e.getKey())).append("\":");
            Object v = e.getValue();
            if (v == null) {
                out.append("null");
            } else if (v instanceof Number || v instanceof Boolean) {
                out.append(v);
            } else {
                out.append('"').append(escape(v.toString())).append('"');
            }
        }
        return out.append('}').toString();
    }

    public static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.toString();
    }
}
