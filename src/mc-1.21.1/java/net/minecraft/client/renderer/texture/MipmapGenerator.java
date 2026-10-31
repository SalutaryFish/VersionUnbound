package net.minecraft.client.renderer.texture;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.Util;

public class MipmapGenerator {
    private static final int ALPHA_CUTOUT_CUTOFF = 96;
    private static final float[] POW22 = Util.make(new float[256], pow22 -> {
        for (int i = 0; i < pow22.length; i++) {
            pow22[i] = (float)Math.pow(i / 255.0F, 2.2);
        }
    });

    private MipmapGenerator() {
    }

    public static NativeImage[] generateMipLevels(NativeImage[] images, int mipLevel) {
        if (mipLevel + 1 <= images.length) {
            return images;
        }

        NativeImage[] nativeImages = new NativeImage[mipLevel + 1];
        nativeImages[0] = images[0];
        boolean bl = hasTransparentPixel(nativeImages[0]);

        for (int i = 1; i <= mipLevel; i++) {
            if (i < images.length) {
                nativeImages[i] = images[i];
            } else {
                NativeImage nativeImage = nativeImages[i - 1];
                NativeImage nativeImage2 = new NativeImage(nativeImage.getWidth() >> 1, nativeImage.getHeight() >> 1, false);
                int j = nativeImage2.getWidth();
                int k = nativeImage2.getHeight();

                for (int l = 0; l < j; l++) {
                    for (int m = 0; m < k; m++) {
                        nativeImage2.setPixelRGBA(
                            l,
                            m,
                            alphaBlend(
                                nativeImage.getPixelRGBA(l * 2 + 0, m * 2 + 0),
                                nativeImage.getPixelRGBA(l * 2 + 1, m * 2 + 0),
                                nativeImage.getPixelRGBA(l * 2 + 0, m * 2 + 1),
                                nativeImage.getPixelRGBA(l * 2 + 1, m * 2 + 1),
                                bl
                            )
                        );
                    }
                }

                nativeImages[i] = nativeImage2;
            }
        }

        return nativeImages;
    }

    private static boolean hasTransparentPixel(NativeImage image) {
        for (int i = 0; i < image.getWidth(); i++) {
            for (int j = 0; j < image.getHeight(); j++) {
                if (image.getPixelRGBA(i, j) >> 24 == 0) {
                    return true;
                }
            }
        }

        return false;
    }

    private static int alphaBlend(int col0, int col1, int col2, int col3, boolean transparent) {
        if (transparent) {
            float f = 0.0F;
            float g = 0.0F;
            float h = 0.0F;
            float i = 0.0F;
            if (col0 >> 24 != 0) {
                f += getPow22(col0 >> 24);
                g += getPow22(col0 >> 16);
                h += getPow22(col0 >> 8);
                i += getPow22(col0 >> 0);
            }

            if (col1 >> 24 != 0) {
                f += getPow22(col1 >> 24);
                g += getPow22(col1 >> 16);
                h += getPow22(col1 >> 8);
                i += getPow22(col1 >> 0);
            }

            if (col2 >> 24 != 0) {
                f += getPow22(col2 >> 24);
                g += getPow22(col2 >> 16);
                h += getPow22(col2 >> 8);
                i += getPow22(col2 >> 0);
            }

            if (col3 >> 24 != 0) {
                f += getPow22(col3 >> 24);
                g += getPow22(col3 >> 16);
                h += getPow22(col3 >> 8);
                i += getPow22(col3 >> 0);
            }

            f /= 4.0F;
            g /= 4.0F;
            h /= 4.0F;
            i /= 4.0F;
            int j = (int)(Math.pow(f, 0.45454545454545453) * 255.0);
            int k = (int)(Math.pow(g, 0.45454545454545453) * 255.0);
            int l = (int)(Math.pow(h, 0.45454545454545453) * 255.0);
            int m = (int)(Math.pow(i, 0.45454545454545453) * 255.0);
            if (j < 96) {
                j = 0;
            }

            return j << 24 | k << 16 | l << 8 | m;
        } else {
            int n = gammaBlend(col0, col1, col2, col3, 24);
            int o = gammaBlend(col0, col1, col2, col3, 16);
            int p = gammaBlend(col0, col1, col2, col3, 8);
            int q = gammaBlend(col0, col1, col2, col3, 0);
            return n << 24 | o << 16 | p << 8 | q;
        }
    }

    private static int gammaBlend(int col0, int col1, int col2, int col3, int bitOffset) {
        float f = getPow22(col0 >> bitOffset);
        float g = getPow22(col1 >> bitOffset);
        float h = getPow22(col2 >> bitOffset);
        float i = getPow22(col3 >> bitOffset);
        float j = (float)((float)Math.pow((f + g + h + i) * 0.25, 0.45454545454545453));
        return (int)(j * 255.0);
    }

    private static float getPow22(int value) {
        return POW22[value & 0xFF];
    }
}
