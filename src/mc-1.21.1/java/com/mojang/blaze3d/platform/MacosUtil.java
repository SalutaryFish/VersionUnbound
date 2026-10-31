package com.mojang.blaze3d.platform;

import ca.weblite.objc.Client;
import ca.weblite.objc.NSObject;
import com.sun.jna.Pointer;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;
import java.util.Optional;
import net.minecraft.server.packs.resources.IoSupplier;
import org.lwjgl.glfw.GLFWNativeCocoa;

public class MacosUtil {
    private static final int NS_RESIZABLE_WINDOW_MASK = 8;
    private static final int NS_FULL_SCREEN_WINDOW_MASK = 16384;

    public static void exitNativeFullscreen(long windowId) {
        getNsWindow(windowId).filter(MacosUtil::isInNativeFullscreen).ifPresent(MacosUtil::toggleNativeFullscreen);
    }

    public static void clearResizableBit(long windowId) {
        getNsWindow(windowId).ifPresent(nSObject -> {
            long l = getStyleMask(nSObject);
            nSObject.send("setStyleMask:", l & -9L);
        });
    }

    private static Optional<NSObject> getNsWindow(long windowId) {
        long l = GLFWNativeCocoa.glfwGetCocoaWindow(windowId);
        return l != 0L ? Optional.of(new NSObject(new Pointer(l))) : Optional.empty();
    }

    private static boolean isInNativeFullscreen(NSObject nsWindow) {
        return (getStyleMask(nsWindow) & 16384L) != 0L;
    }

    private static long getStyleMask(NSObject nsWindow) {
        return (Long)nsWindow.sendRaw("styleMask");
    }

    private static void toggleNativeFullscreen(NSObject nsWindow) {
        nsWindow.send("toggleFullScreen:", Pointer.NULL);
    }

    public static void loadIcon(IoSupplier<InputStream> iconStreamSupplier) throws IOException {
        try (InputStream inputStream = iconStreamSupplier.get()) {
            String string = Base64.getEncoder().encodeToString(inputStream.readAllBytes());
            Client client = Client.getInstance();
            Object object = client.sendProxy("NSData", "alloc").send("initWithBase64Encoding:", string);
            Object object2 = client.sendProxy("NSImage", "alloc").send("initWithData:", object);
            client.sendProxy("NSApplication", "sharedApplication").send("setApplicationIconImage:", object2);
        }
    }
}
