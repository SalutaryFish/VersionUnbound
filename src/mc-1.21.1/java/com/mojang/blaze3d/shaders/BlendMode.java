package com.mojang.blaze3d.shaders;

import com.mojang.blaze3d.systems.RenderSystem;
import java.util.Locale;
import javax.annotation.Nullable;

public class BlendMode {
    @Nullable
    private static BlendMode lastApplied;
    private final int srcColorFactor;
    private final int srcAlphaFactor;
    private final int dstColorFactor;
    private final int dstAlphaFactor;
    private final int blendFunc;
    private final boolean separateBlend;
    private final boolean opaque;

    private BlendMode(boolean separateBlend, boolean opaque, int srcColorFactor, int dstColorFactor, int srcAlphaFactor, int dstAlphaFactor, int blendFunc) {
        this.separateBlend = separateBlend;
        this.srcColorFactor = srcColorFactor;
        this.dstColorFactor = dstColorFactor;
        this.srcAlphaFactor = srcAlphaFactor;
        this.dstAlphaFactor = dstAlphaFactor;
        this.opaque = opaque;
        this.blendFunc = blendFunc;
    }

    public BlendMode() {
        this(false, true, 1, 0, 1, 0, 32774);
    }

    public BlendMode(int srcFactor, int dstFactor, int blendFunc) {
        this(false, false, srcFactor, dstFactor, srcFactor, dstFactor, blendFunc);
    }

    public BlendMode(int srcColorFactor, int dstColorFactor, int srcAlphaFactor, int dstAlphaFactor, int blendFunc) {
        this(true, false, srcColorFactor, dstColorFactor, srcAlphaFactor, dstAlphaFactor, blendFunc);
    }

    public void apply() {
        if (!this.equals(lastApplied)) {
            if (lastApplied == null || this.opaque != lastApplied.isOpaque()) {
                lastApplied = this;
                if (this.opaque) {
                    RenderSystem.disableBlend();
                    return;
                }

                RenderSystem.enableBlend();
            }

            RenderSystem.blendEquation(this.blendFunc);
            if (this.separateBlend) {
                RenderSystem.blendFuncSeparate(this.srcColorFactor, this.dstColorFactor, this.srcAlphaFactor, this.dstAlphaFactor);
            } else {
                RenderSystem.blendFunc(this.srcColorFactor, this.dstColorFactor);
            }
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else if (!(other instanceof BlendMode blendMode)) {
            return false;
        } else if (this.blendFunc != blendMode.blendFunc) {
            return false;
        } else if (this.dstAlphaFactor != blendMode.dstAlphaFactor) {
            return false;
        } else if (this.dstColorFactor != blendMode.dstColorFactor) {
            return false;
        } else if (this.opaque != blendMode.opaque) {
            return false;
        } else if (this.separateBlend != blendMode.separateBlend) {
            return false;
        } else {
            return this.srcAlphaFactor != blendMode.srcAlphaFactor ? false : this.srcColorFactor == blendMode.srcColorFactor;
        }
    }

    @Override
    public int hashCode() {
        int i = this.srcColorFactor;
        i = 31 * i + this.srcAlphaFactor;
        i = 31 * i + this.dstColorFactor;
        i = 31 * i + this.dstAlphaFactor;
        i = 31 * i + this.blendFunc;
        i = 31 * i + (this.separateBlend ? 1 : 0);
        return 31 * i + (this.opaque ? 1 : 0);
    }

    public boolean isOpaque() {
        return this.opaque;
    }

    /**
     * Converts a blend function name to an id, returning add (32774) if not recognized.
     */
    public static int stringToBlendFunc(String funcName) {
        String string = funcName.trim().toLowerCase(Locale.ROOT);
        if ("add".equals(string)) {
            return 32774;
        } else if ("subtract".equals(string)) {
            return 32778;
        } else if ("reversesubtract".equals(string)) {
            return 32779;
        } else if ("reverse_subtract".equals(string)) {
            return 32779;
        } else if ("min".equals(string)) {
            return 32775;
        } else {
            return "max".equals(string) ? 32776 : 32774;
        }
    }

    public static int stringToBlendFactor(String factorName) {
        String string = factorName.trim().toLowerCase(Locale.ROOT);
        string = string.replaceAll("_", "");
        string = string.replaceAll("one", "1");
        string = string.replaceAll("zero", "0");
        string = string.replaceAll("minus", "-");
        if ("0".equals(string)) {
            return 0;
        } else if ("1".equals(string)) {
            return 1;
        } else if ("srccolor".equals(string)) {
            return 768;
        } else if ("1-srccolor".equals(string)) {
            return 769;
        } else if ("dstcolor".equals(string)) {
            return 774;
        } else if ("1-dstcolor".equals(string)) {
            return 775;
        } else if ("srcalpha".equals(string)) {
            return 770;
        } else if ("1-srcalpha".equals(string)) {
            return 771;
        } else if ("dstalpha".equals(string)) {
            return 772;
        } else {
            return "1-dstalpha".equals(string) ? 773 : -1;
        }
    }
}
