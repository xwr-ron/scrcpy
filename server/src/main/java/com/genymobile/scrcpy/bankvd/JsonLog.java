package com.genymobile.scrcpy.bankvd;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

final class JsonLog {
    private JsonLog() {
        // not instantiable
    }

    static void event(String event, String... kv) {
        String line = build(event, kv);
        System.out.println(line);
        System.out.flush();
    }

    static void writeStatusFile(String path, String event, String... kv) throws IOException {
        if (path == null || path.isEmpty()) {
            return;
        }

        File file = new File(path);
        File parent = file.getParentFile();

        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Could not create status directory: " + parent);
        }

        try (FileOutputStream out = new FileOutputStream(file, false)) {
            out.write(build(event, kv).getBytes(StandardCharsets.UTF_8));
            out.write('\n');
        }
    }

    static String build(String event, String... kv) {
        StringBuilder sb = new StringBuilder(128);

        sb.append('{');
        appendField(sb, "event", event);

        for (int i = 0; i + 1 < kv.length; i += 2) {
            sb.append(',');
            appendField(sb, kv[i], kv[i + 1]);
        }

        sb.append('}');
        return sb.toString();
    }

    private static void appendField(StringBuilder sb, String key, String value) {
        sb.append('"').append(escape(key)).append('"').append(':');

        if (value == null) {
            sb.append("null");
            return;
        }

        if (isJsonNumber(value) || "true".equals(value) || "false".equals(value)) {
            sb.append(value);
            return;
        }

        sb.append('"').append(escape(value)).append('"');
    }

    private static boolean isJsonNumber(String value) {
        if (value.isEmpty()) {
            return false;
        }

        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);

            if (!(c == '-' || c == '.' || (c >= '0' && c <= '9'))) {
                return false;
            }
        }

        return true;
    }

    private static String escape(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16);

        for (int i = 0; i < value.length(); ++i) {
            char c = value.charAt(i);

            switch (c) {
                case '\\':
                    sb.append("\\\\");
                    break;
                case '"':
                    sb.append("\\\"");
                    break;
                case '\n':
                    sb.append("\\n");
                    break;
                case '\r':
                    sb.append("\\r");
                    break;
                case '\t':
                    sb.append("\\t");
                    break;
                default:
                    sb.append(c);
                    break;
            }
        }

        return sb.toString();
    }
}
