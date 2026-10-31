package net.minecraft.client.renderer.texture;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class Stitcher<T extends Stitcher.Entry> {
    private static final Comparator<Stitcher.Holder<?>> HOLDER_COMPARATOR = Comparator.<Stitcher.Holder<?>, Integer>comparing(holder -> -holder.height)
        .thenComparing(holder -> -holder.width)
        .thenComparing(holder -> holder.entry.name());
    private final int mipLevel;
    private final List<Stitcher.Holder<T>> texturesToBeStitched = new ArrayList<>();
    private final List<Stitcher.Region<T>> storage = new ArrayList<>();
    private int storageX;
    private int storageY;
    private final int maxWidth;
    private final int maxHeight;

    public Stitcher(int maxWidth, int maxHeight, int mipLevel) {
        this.mipLevel = mipLevel;
        this.maxWidth = maxWidth;
        this.maxHeight = maxHeight;
    }

    public int getWidth() {
        return this.storageX;
    }

    public int getHeight() {
        return this.storageY;
    }

    public void registerSprite(T stitcherEntry) {
        Stitcher.Holder<T> holder = new Stitcher.Holder<>(stitcherEntry, this.mipLevel);
        this.texturesToBeStitched.add(holder);
    }

    public void stitch() {
        List<Stitcher.Holder<T>> list = new ArrayList<>(this.texturesToBeStitched);
        list.sort(HOLDER_COMPARATOR);

        for (Stitcher.Holder<T> holder : list) {
            if (!this.addToStorage(holder)) {
                throw new StitcherException(holder.entry, list.stream().map(stitcherHolder -> stitcherHolder.entry).collect(ImmutableList.toImmutableList()));
            }
        }
    }

    public void gatherSprites(Stitcher.SpriteLoader<T> loader) {
        for (Stitcher.Region<T> region : this.storage) {
            region.walk(loader);
        }
    }

    static int smallestFittingMinTexel(int dimension, int mipLevel) {
        return (dimension >> mipLevel) + ((dimension & (1 << mipLevel) - 1) == 0 ? 0 : 1) << mipLevel;
    }

    /**
     * Attempts to find space for specified {@code holder}.
     *
     * @return {@code true} if there was space; {@code false} otherwise
     */
    private boolean addToStorage(Stitcher.Holder<T> holder) {
        for (Stitcher.Region<T> region : this.storage) {
            if (region.add(holder)) {
                return true;
            }
        }

        return this.expand(holder);
    }

    /**
     * Attempts to expand stitched texture in order to make space for specified {@code holder}.
     *
     * @return {@code true} if there was enough space to expand the texture; {@code false} otherwise
     */
    private boolean expand(Stitcher.Holder<T> holder) {
        int i = Mth.smallestEncompassingPowerOfTwo(this.storageX);
        int j = Mth.smallestEncompassingPowerOfTwo(this.storageY);
        int k = Mth.smallestEncompassingPowerOfTwo(this.storageX + holder.width);
        int l = Mth.smallestEncompassingPowerOfTwo(this.storageY + holder.height);
        boolean bl = k <= this.maxWidth;
        boolean bl2 = l <= this.maxHeight;
        if (!bl && !bl2) {
            return false;
        }

        boolean bl3 = bl && i != k;
        boolean bl4 = bl2 && j != l;
        boolean bl5;
        if (bl3 ^ bl4) {
            bl5 = bl3;
        } else {
            bl5 = bl && i <= j;
        }

        Stitcher.Region<T> region;
        if (bl5) {
            if (this.storageY == 0) {
                this.storageY = l;
            }

            region = new Stitcher.Region<>(this.storageX, 0, k - this.storageX, this.storageY);
            this.storageX = k;
        } else {
            region = new Stitcher.Region<>(0, this.storageY, this.storageX, l - this.storageY);
            this.storageY = l;
        }

        region.add(holder);
        this.storage.add(region);
        return true;
    }

    public interface Entry {
        int width();

        int height();

        ResourceLocation name();
    }

    record Holder<T extends Stitcher.Entry>(T entry, int width, int height) {
        public Holder(T entry, int mipLevel) {
            this(entry, Stitcher.smallestFittingMinTexel(entry.width(), mipLevel), Stitcher.smallestFittingMinTexel(entry.height(), mipLevel));
        }
    }

    public static class Region<T extends Stitcher.Entry> {
        private final int originX;
        private final int originY;
        private final int width;
        private final int height;
        @Nullable
        private List<Stitcher.Region<T>> subSlots;
        @Nullable
        private Stitcher.Holder<T> holder;

        public Region(int originX, int originY, int width, int height) {
            this.originX = originX;
            this.originY = originY;
            this.width = width;
            this.height = height;
        }

        public int getX() {
            return this.originX;
        }

        public int getY() {
            return this.originY;
        }

        public boolean add(Stitcher.Holder<T> holder) {
            if (this.holder != null) {
                return false;
            }

            int i = holder.width;
            int j = holder.height;
            if (i <= this.width && j <= this.height) {
                if (i == this.width && j == this.height) {
                    this.holder = holder;
                    return true;
                }

                if (this.subSlots == null) {
                    this.subSlots = new ArrayList<>(1);
                    this.subSlots.add(new Stitcher.Region<>(this.originX, this.originY, i, j));
                    int k = this.width - i;
                    int l = this.height - j;
                    if (l > 0 && k > 0) {
                        int m = Math.max(this.height, k);
                        int n = Math.max(this.width, l);
                        if (m >= n) {
                            this.subSlots.add(new Stitcher.Region<>(this.originX, this.originY + j, i, l));
                            this.subSlots.add(new Stitcher.Region<>(this.originX + i, this.originY, k, this.height));
                        } else {
                            this.subSlots.add(new Stitcher.Region<>(this.originX + i, this.originY, k, j));
                            this.subSlots.add(new Stitcher.Region<>(this.originX, this.originY + j, this.width, l));
                        }
                    } else if (k == 0) {
                        this.subSlots.add(new Stitcher.Region<>(this.originX, this.originY + j, i, l));
                    } else if (l == 0) {
                        this.subSlots.add(new Stitcher.Region<>(this.originX + i, this.originY, k, j));
                    }
                }

                for (Stitcher.Region<T> region : this.subSlots) {
                    if (region.add(holder)) {
                        return true;
                    }
                }

                return false;
            } else {
                return false;
            }
        }

        public void walk(Stitcher.SpriteLoader<T> spriteLoader) {
            if (this.holder != null) {
                spriteLoader.load(this.holder.entry, this.getX(), this.getY());
            } else if (this.subSlots != null) {
                for (Stitcher.Region<T> region : this.subSlots) {
                    region.walk(spriteLoader);
                }
            }
        }

        @Override
        public String toString() {
            return "Slot{originX="
                + this.originX
                + ", originY="
                + this.originY
                + ", width="
                + this.width
                + ", height="
                + this.height
                + ", texture="
                + this.holder
                + ", subSlots="
                + this.subSlots
                + "}";
        }
    }

    public interface SpriteLoader<T extends Stitcher.Entry> {
        void load(T var1, int var2, int var3);
    }
}
