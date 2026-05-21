package com.genymobile.scrcpy.bankvd;

import com.genymobile.scrcpy.wrappers.ServiceManager;

import android.graphics.PixelFormat;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

final class HeldVirtualDisplay implements AutoCloseable {
    private final ImageReader imageReader;
    private final Surface surface;
    private final HandlerThread imageDrainThread;
    private final VirtualDisplay virtualDisplay;

    private HeldVirtualDisplay(ImageReader imageReader, Surface surface, HandlerThread imageDrainThread, VirtualDisplay virtualDisplay) {
        this.imageReader = imageReader;
        this.surface = surface;
        this.imageDrainThread = imageDrainThread;
        this.virtualDisplay = virtualDisplay;
    }

    static HeldVirtualDisplay create(String name, int width, int height, int dpi, boolean systemDecorations) throws Exception {
        JsonLog.event(
                "vd_create_begin",
                "name", name,
                "width", String.valueOf(width),
                "height", String.valueOf(height),
                "dpi", String.valueOf(dpi),
                "systemDecorations", String.valueOf(systemDecorations)
        );

        HandlerThread thread = new HandlerThread("bank-vd-image-drain");
        thread.start();

        JsonLog.event("vd_image_thread_started");

        ImageReader reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);

        JsonLog.event("vd_image_reader_created");

        Handler handler = new Handler(thread.getLooper());

        reader.setOnImageAvailableListener(ir -> {
            Image image = null;

            try {
                image = ir.acquireLatestImage();
            } catch (Throwable ignored) {
                // Keep the display alive even if frame draining fails once.
            } finally {
                if (image != null) {
                    image.close();
                }
            }
        }, handler);

        Surface surface = reader.getSurface();
        int flags = buildVirtualDisplayFlags(systemDecorations);

        JsonLog.event(
                "vd_create_call",
                "flags", String.valueOf(flags)
        );

        VirtualDisplay vd;

        try {
            vd = ServiceManager.getDisplayManager()
                    .createNewVirtualDisplay(name, width, height, dpi, surface, flags);
        } catch (Throwable t) {
            JsonLog.event(
                    "vd_create_failed",
                    "error", t.toString(),
                    "stack", JsonLog.stackTrace(t)
            );

            surface.release();
            reader.close();
            thread.quitSafely();

            throw t;
        }

        if (vd == null || vd.getDisplay() == null) {
            surface.release();
            reader.close();
            thread.quitSafely();
            throw new IllegalStateException("Could not create virtual display");
        }

        JsonLog.event(
                "vd_create_success",
                "displayId", String.valueOf(vd.getDisplay().getDisplayId())
        );

        return new HeldVirtualDisplay(reader, surface, thread, vd);
    }

    int getDisplayId() {
        return virtualDisplay.getDisplay().getDisplayId();
    }

    @Override
    public void close() {
        try {
            virtualDisplay.release();
        } catch (Throwable ignored) {
            // best effort cleanup
        }

        try {
            surface.release();
        } catch (Throwable ignored) {
            // best effort cleanup
        }

        try {
            imageReader.close();
        } catch (Throwable ignored) {
            // best effort cleanup
        }

        imageDrainThread.quitSafely();
    }

	// region Flags
    private static final int VIRTUAL_DISPLAY_FLAG_PUBLIC = 1;
    private static final int VIRTUAL_DISPLAY_FLAG_PRESENTATION = 1 << 1;
    private static final int VIRTUAL_DISPLAY_FLAG_SECURE = 1 << 2;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY = 1 << 3;
    private static final int VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD = 1 << 5;
    private static final int VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH = 1 << 6;
    private static final int VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL = 1 << 8;
    private static final int VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS = 1 << 9;
    private static final int VIRTUAL_DISPLAY_FLAG_TRUSTED = 1 << 10;
    private static final int VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP = 1 << 11;
    private static final int VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED = 1 << 12;


    private static int buildVirtualDisplayFlags(boolean systemDecorations) {
        int flags = 0;

        flags |= VIRTUAL_DISPLAY_FLAG_PUBLIC;
        flags |= VIRTUAL_DISPLAY_FLAG_PRESENTATION;
        flags |= VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY;
        flags |= VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH;
        flags |= VIRTUAL_DISPLAY_FLAG_DESTROY_CONTENT_ON_REMOVAL;

        // BankVD must run on a hidden secondary display while the physical
        // device can stay locked/off. Without these flags, some Android/OEM
        // builds attach the keyguard surface to the virtual display too.
		//
		// Helps on some OEM builds where secondary displays still get
		// keyguard-related surfaces while the physical device is locked.
        flags |= VIRTUAL_DISPLAY_FLAG_TRUSTED;
        flags |= VIRTUAL_DISPLAY_FLAG_OWN_DISPLAY_GROUP;
        flags |= VIRTUAL_DISPLAY_FLAG_ALWAYS_UNLOCKED;
        flags |= VIRTUAL_DISPLAY_FLAG_CAN_SHOW_WITH_INSECURE_KEYGUARD;

        if (systemDecorations) {
            flags |= VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS;
        }

        return flags;
    }

    private static int reflectDisplayManagerFlag(String name) {
        try {
            return android.hardware.display.DisplayManager.class.getField(name).getInt(null);
        } catch (Throwable ignored) {
            return 0;
        }
    }
}
