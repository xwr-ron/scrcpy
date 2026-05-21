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
        HandlerThread thread = new HandlerThread("bank-vd-image-drain");
        thread.start();

        ImageReader reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 3);
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

        VirtualDisplay vd = ServiceManager.getDisplayManager()
                .createNewVirtualDisplay(name, width, height, dpi, surface, flags);

        if (vd == null || vd.getDisplay() == null) {
            surface.release();
            reader.close();
            thread.quitSafely();
            throw new IllegalStateException("Could not create virtual display");
        }

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

    private static int buildVirtualDisplayFlags(boolean systemDecorations) {
        int flags = 0;

        flags |= reflectDisplayManagerFlag("VIRTUAL_DISPLAY_FLAG_PUBLIC");
        flags |= reflectDisplayManagerFlag("VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY");
        flags |= reflectDisplayManagerFlag("VIRTUAL_DISPLAY_FLAG_PRESENTATION");
        flags |= reflectDisplayManagerFlag("VIRTUAL_DISPLAY_FLAG_SUPPORTS_TOUCH");

        if (systemDecorations) {
            flags |= reflectDisplayManagerFlag("VIRTUAL_DISPLAY_FLAG_SHOULD_SHOW_SYSTEM_DECORATIONS");
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
