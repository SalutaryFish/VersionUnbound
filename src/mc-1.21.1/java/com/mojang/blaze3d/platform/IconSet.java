package com.mojang.blaze3d.platform;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.IoSupplier;
import org.apache.commons.lang3.ArrayUtils;

public enum IconSet {
    RELEASE("icons"),
    SNAPSHOT("icons", "snapshot");

    private final String[] path;

    IconSet(final String... path) {
        this.path = path;
    }

    public List<IoSupplier<InputStream>> getStandardIcons(PackResources resources) throws IOException {
        return List.of(
            this.getFile(resources, "icon_16x16.png"),
            this.getFile(resources, "icon_32x32.png"),
            this.getFile(resources, "icon_48x48.png"),
            this.getFile(resources, "icon_128x128.png"),
            this.getFile(resources, "icon_256x256.png")
        );
    }

    public IoSupplier<InputStream> getMacIcon(PackResources resources) throws IOException {
        return this.getFile(resources, "minecraft.icns");
    }

    private IoSupplier<InputStream> getFile(PackResources resources, String filename) throws IOException {
        String[] strings = ArrayUtils.add(this.path, filename);
        IoSupplier<InputStream> ioSupplier = resources.getRootResource(strings);
        if (ioSupplier == null) {
            throw new FileNotFoundException(String.join("/", strings));
        } else {
            return ioSupplier;
        }
    }
}
