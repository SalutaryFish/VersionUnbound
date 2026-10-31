package net.minecraft.client.gui.font;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.font.GlyphInfo;
import com.mojang.blaze3d.font.GlyphProvider;
import com.mojang.blaze3d.font.SheetGlyphInfo;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.SpecialGlyphs;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

public class FontSet implements AutoCloseable {
    private static final RandomSource RANDOM = RandomSource.create();
    private static final float LARGE_FORWARD_ADVANCE = 32.0F;
    private final TextureManager textureManager;
    private final ResourceLocation name;
    private BakedGlyph missingGlyph;
    private BakedGlyph whiteGlyph;
    private List<GlyphProvider.Conditional> allProviders = List.of();
    private List<GlyphProvider> activeProviders = List.of();
    private final CodepointMap<BakedGlyph> glyphs = new CodepointMap<>(BakedGlyph[]::new, BakedGlyph[][]::new);
    private final CodepointMap<FontSet.GlyphInfoFilter> glyphInfos = new CodepointMap<>(FontSet.GlyphInfoFilter[]::new, FontSet.GlyphInfoFilter[][]::new);
    private final Int2ObjectMap<IntList> glyphsByWidth = new Int2ObjectOpenHashMap<>();
    private final List<FontTexture> textures = Lists.newArrayList();

    public FontSet(TextureManager textureManager, ResourceLocation name) {
        this.textureManager = textureManager;
        this.name = name;
    }

    public void reload(List<GlyphProvider.Conditional> allProviders, Set<FontOption> options) {
        this.allProviders = allProviders;
        this.reload(options);
    }

    public void reload(Set<FontOption> options) {
        this.activeProviders = List.of();
        this.resetTextures();
        this.activeProviders = this.selectProviders(this.allProviders, options);
    }

    private void resetTextures() {
        this.closeTextures();
        this.glyphs.clear();
        this.glyphInfos.clear();
        this.glyphsByWidth.clear();
        this.missingGlyph = SpecialGlyphs.MISSING.bake(this::stitch);
        this.whiteGlyph = SpecialGlyphs.WHITE.bake(this::stitch);
    }

    private List<GlyphProvider> selectProviders(List<GlyphProvider.Conditional> providers, Set<FontOption> options) {
        IntSet intSet = new IntOpenHashSet();
        List<GlyphProvider> list = new ArrayList<>();

        for (GlyphProvider.Conditional conditional : providers) {
            if (conditional.filter().apply(options)) {
                list.add(conditional.provider());
                intSet.addAll(conditional.provider().getSupportedGlyphs());
            }
        }

        Set<GlyphProvider> set = Sets.newHashSet();
        intSet.forEach(i -> {
            for (GlyphProvider glyphProvider : list) {
                GlyphInfo glyphInfo = glyphProvider.getGlyph(i);
                if (glyphInfo != null) {
                    set.add(glyphProvider);
                    if (glyphInfo != SpecialGlyphs.MISSING) {
                        this.glyphsByWidth.computeIfAbsent(Mth.ceil(glyphInfo.getAdvance(false)), ix -> new IntArrayList()).add(i);
                    }
                    break;
                }
            }
        });
        return list.stream().filter(set::contains).toList();
    }

    @Override
    public void close() {
        this.closeTextures();
    }

    private void closeTextures() {
        for (FontTexture fontTexture : this.textures) {
            fontTexture.close();
        }

        this.textures.clear();
    }

    private static boolean hasFishyAdvance(GlyphInfo glyph) {
        float f = glyph.getAdvance(false);
        if (!(f < 0.0F) && !(f > 32.0F)) {
            float g = glyph.getAdvance(true);
            return g < 0.0F || g > 32.0F;
        } else {
            return true;
        }
    }

    private FontSet.GlyphInfoFilter computeGlyphInfo(int character) {
        GlyphInfo glyphInfo = null;

        for (GlyphProvider glyphProvider : this.activeProviders) {
            GlyphInfo glyphInfo2 = glyphProvider.getGlyph(character);
            if (glyphInfo2 != null) {
                if (glyphInfo == null) {
                    glyphInfo = glyphInfo2;
                }

                if (!hasFishyAdvance(glyphInfo2)) {
                    return new FontSet.GlyphInfoFilter(glyphInfo, glyphInfo2);
                }
            }
        }

        return glyphInfo != null ? new FontSet.GlyphInfoFilter(glyphInfo, SpecialGlyphs.MISSING) : FontSet.GlyphInfoFilter.MISSING;
    }

    public GlyphInfo getGlyphInfo(int character, boolean filterFishyGlyphs) {
        return this.glyphInfos.computeIfAbsent(character, this::computeGlyphInfo).select(filterFishyGlyphs);
    }

    private BakedGlyph computeBakedGlyph(int character) {
        for (GlyphProvider glyphProvider : this.activeProviders) {
            GlyphInfo glyphInfo = glyphProvider.getGlyph(character);
            if (glyphInfo != null) {
                return glyphInfo.bake(this::stitch);
            }
        }

        return this.missingGlyph;
    }

    public BakedGlyph getGlyph(int character) {
        return this.glyphs.computeIfAbsent(character, this::computeBakedGlyph);
    }

    private BakedGlyph stitch(SheetGlyphInfo glyphInfo) {
        for (FontTexture fontTexture : this.textures) {
            BakedGlyph bakedGlyph = fontTexture.add(glyphInfo);
            if (bakedGlyph != null) {
                return bakedGlyph;
            }
        }

        ResourceLocation resourceLocation = this.name.withSuffix("/" + this.textures.size());
        boolean bl = glyphInfo.isColored();
        GlyphRenderTypes glyphRenderTypes = bl
            ? GlyphRenderTypes.createForColorTexture(resourceLocation)
            : GlyphRenderTypes.createForIntensityTexture(resourceLocation);
        FontTexture fontTexture2 = new FontTexture(glyphRenderTypes, bl);
        this.textures.add(fontTexture2);
        this.textureManager.register(resourceLocation, fontTexture2);
        BakedGlyph bakedGlyph2 = fontTexture2.add(glyphInfo);
        return bakedGlyph2 == null ? this.missingGlyph : bakedGlyph2;
    }

    public BakedGlyph getRandomGlyph(GlyphInfo glyph) {
        IntList intList = this.glyphsByWidth.get(Mth.ceil(glyph.getAdvance(false)));
        return intList != null && !intList.isEmpty() ? this.getGlyph(intList.getInt(RANDOM.nextInt(intList.size()))) : this.missingGlyph;
    }

    public ResourceLocation name() {
        return this.name;
    }

    public BakedGlyph whiteGlyph() {
        return this.whiteGlyph;
    }

    record GlyphInfoFilter(GlyphInfo glyphInfo, GlyphInfo glyphInfoNotFishy) {
        static final FontSet.GlyphInfoFilter MISSING = new FontSet.GlyphInfoFilter(SpecialGlyphs.MISSING, SpecialGlyphs.MISSING);

        GlyphInfo select(boolean filterFishyGlyphs) {
            return filterFishyGlyphs ? this.glyphInfoNotFishy : this.glyphInfo;
        }
    }
}
