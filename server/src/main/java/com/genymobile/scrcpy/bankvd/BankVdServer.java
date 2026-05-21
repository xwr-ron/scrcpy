package com.genymobile.scrcpy.bankvd;

import com.genymobile.scrcpy.AndroidVersions;
import com.genymobile.scrcpy.device.Device;
import com.genymobile.scrcpy.wrappers.ServiceManager;

import android.os.Build;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public final class BankVdServer {
    private BankVdServer() {
        // not instantiable
    }

    public static void main(String[] args) {
        int status = 0;

        try {
            run(BankVdOptions.parse(args));
        } catch (Throwable t) {
            JsonLog.event("fatal", "error", t.toString());
            status = 1;
        } finally {
            System.exit(status);
        }
    }

    private static void run(BankVdOptions options) throws Exception {
        if (Build.VERSION.SDK_INT < AndroidVersions.API_29_ANDROID_10) {
            throw new IllegalStateException("Hidden virtual display requires Android 10+");
        }

        JsonLog.event(
                "server_started",
                "android", Build.VERSION.RELEASE,
                "sdk", String.valueOf(Build.VERSION.SDK_INT),
                "manufacturer", Build.MANUFACTURER,
                "model", Build.MODEL,
                "package", options.packageName,
                "width", String.valueOf(options.width),
                "height", String.valueOf(options.height),
                "dpi", String.valueOf(options.dpi)
        );

        String displayName = options.name + ":" + options.packageName;

        try (HeldVirtualDisplay held = HeldVirtualDisplay.create(
                displayName,
                options.width,
                options.height,
                options.dpi,
                options.systemDecorations
        )) {
            int displayId = held.getDisplayId();

            JsonLog.event(
                    "virtual_display_created",
                    "displayId", String.valueOf(displayId),
                    "name", displayName,
                    "width", String.valueOf(options.width),
                    "height", String.valueOf(options.height),
                    "dpi", String.valueOf(options.dpi)
            );

            trySetImePolicy(displayId, options.displayImePolicy);

            Device.startApp(options.packageName, displayId, options.forceStop);

            JsonLog.event(
                    "app_started",
                    "package", options.packageName,
                    "displayId", String.valueOf(displayId),
                    "forceStop", String.valueOf(options.forceStop)
            );

            JsonLog.writeStatusFile(
                    options.statusFile,
                    "ready",
                    "package", options.packageName,
                    "displayId", String.valueOf(displayId),
                    "pid", currentPid(),
                    "width", String.valueOf(options.width),
                    "height", String.valueOf(options.height),
                    "dpi", String.valueOf(options.dpi)
            );

            CountDownLatch latch = new CountDownLatch(1);

            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                JsonLog.event("shutdown_requested", "displayId", String.valueOf(displayId));
                latch.countDown();
            }, "bank-vd-shutdown"));

            if (options.lifetimeSeconds > 0) {
                latch.await(options.lifetimeSeconds, TimeUnit.SECONDS);
            } else if (options.keepAlive) {
                latch.await();
            }

            JsonLog.event("releasing", "displayId", String.valueOf(displayId));
        }

        JsonLog.event("released", "package", options.packageName);
    }

    private static void trySetImePolicy(int displayId, int policy) {
        try {
            ServiceManager.getWindowManager().setDisplayImePolicy(displayId, policy);
            JsonLog.event("ime_policy_set", "displayId", String.valueOf(displayId), "policy", String.valueOf(policy));
        } catch (Throwable t) {
            JsonLog.event("ime_policy_failed", "displayId", String.valueOf(displayId), "error", t.toString());
        }
    }

    private static String currentPid() {
        try {
            return String.valueOf(android.os.Process.myPid());
        } catch (Throwable ignored) {
            return "0";
        }
    }
}
