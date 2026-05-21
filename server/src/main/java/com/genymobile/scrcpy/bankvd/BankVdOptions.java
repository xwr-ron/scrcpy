package com.genymobile.scrcpy.bankvd;

import com.genymobile.scrcpy.wrappers.WindowManager;

import java.util.Locale;

final class BankVdOptions {
    static final int DEFAULT_WIDTH = 1080;
    static final int DEFAULT_HEIGHT = 2460;
    static final int DEFAULT_DPI = 300;

    String packageName;
    String name = "bank-vd";
    String statusFile = "/sdcard/Tasker/bank-vd/status.json";

    int width = DEFAULT_WIDTH;
    int height = DEFAULT_HEIGHT;
    int dpi = DEFAULT_DPI;
    int lifetimeSeconds;
    int displayImePolicy = WindowManager.DISPLAY_IME_POLICY_LOCAL;

    boolean forceStop = true;
    boolean keepAlive = true;
    boolean systemDecorations;

    private BankVdOptions() {
        // use parse()
    }

    static BankVdOptions parse(String[] args) {
        BankVdOptions options = new BankVdOptions();

        for (String arg : args) {
            int idx = arg.indexOf('=');

            if (idx == -1) {
                throw new IllegalArgumentException("Invalid argument, expected key=value: " + arg);
            }

            String key = arg.substring(0, idx);
            String value = arg.substring(idx + 1);

            switch (key) {
                case "package":
                case "package_name":
                    options.packageName = value;
                    break;

                case "name":
                    options.name = value;
                    break;

                case "width":
                    options.width = parsePositiveInt(key, value);
                    break;

                case "height":
                    options.height = parsePositiveInt(key, value);
                    break;

                case "dpi":
                    options.dpi = parsePositiveInt(key, value);
                    break;

                case "new_display":
                    parseNewDisplay(value, options);
                    break;

                case "force_stop":
                    options.forceStop = Boolean.parseBoolean(value);
                    break;

                case "keep_alive":
                    options.keepAlive = Boolean.parseBoolean(value);
                    break;

                case "lifetime_sec":
                    options.lifetimeSeconds = Integer.parseInt(value);
                    break;

                case "status_file":
                    options.statusFile = value;
                    break;

                case "vd_system_decorations":
                case "system_decorations":
                    options.systemDecorations = Boolean.parseBoolean(value);
                    break;

                case "display_ime_policy":
                    options.displayImePolicy = parseDisplayImePolicy(value);
                    break;

                default:
                    throw new IllegalArgumentException("Unknown argument: " + key);
            }
        }

        if (options.packageName == null || options.packageName.isEmpty()) {
            throw new IllegalArgumentException("Missing required argument: package=<android.package.name>");
        }

        return options;
    }

    private static int parsePositiveInt(String key, String value) {
        int parsed = Integer.parseInt(value);

        if (parsed <= 0) {
            throw new IllegalArgumentException(key + " must be positive: " + value);
        }

        return parsed;
    }

    private static void parseNewDisplay(String value, BankVdOptions options) {
        // Compatible with scrcpy notation: "1080x2460/300".
        String[] parts = value.split("/", -1);

        if (parts.length >= 1 && !parts[0].isEmpty()) {
            String[] size = parts[0].split("x", -1);

            if (size.length != 2) {
                throw new IllegalArgumentException("Invalid new_display size, expected WIDTHxHEIGHT/DPI: " + value);
            }

            options.width = parsePositiveInt("width", size[0]);
            options.height = parsePositiveInt("height", size[1]);
        }

        if (parts.length >= 2 && !parts[1].isEmpty()) {
            options.dpi = parsePositiveInt("dpi", parts[1]);
        }
    }

    private static int parseDisplayImePolicy(String value) {
        switch (value.toLowerCase(Locale.ENGLISH)) {
            case "local":
                return WindowManager.DISPLAY_IME_POLICY_LOCAL;
            case "fallback":
                return WindowManager.DISPLAY_IME_POLICY_FALLBACK_DISPLAY;
            case "hide":
                return WindowManager.DISPLAY_IME_POLICY_HIDE;
            default:
                throw new IllegalArgumentException("Invalid display_ime_policy: " + value);
        }
    }
}
