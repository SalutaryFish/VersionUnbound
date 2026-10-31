package com.mojang.blaze3d.font;

import java.util.function.Function;
import net.minecraft.client.gui.font.glyphs.BakedGlyph;
import net.minecraft.client.gui.font.glyphs.EmptyGlyph;

public interface GlyphInfo {
    float getAdvance();

    default float getAdvance(boolean bold) {
        return this.getAdvance() + (bold ? this.getBoldOffset() : 0.0F);
    }

    default float getBoldOffset() {
        return 1.0F;
    }

    default float getShadowOffset() {
        return 1.0F;
    }

    BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> var1);

    interface SpaceGlyphInfo extends GlyphInfo {
        @Override
        default BakedGlyph bake(Function<SheetGlyphInfo, BakedGlyph> function) {
            return EmptyGlyph.INSTANCE;
        }
    }
}
