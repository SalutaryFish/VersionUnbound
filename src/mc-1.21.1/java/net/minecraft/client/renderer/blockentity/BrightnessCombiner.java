package net.minecraft.client.renderer.blockentity;

import it.unimi.dsi.fastutil.ints.Int2IntFunction;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.block.DoubleBlockCombiner;
import net.minecraft.world.level.block.entity.BlockEntity;

public class BrightnessCombiner<S extends BlockEntity> implements DoubleBlockCombiner.Combiner<S, Int2IntFunction> {
    public Int2IntFunction acceptDouble(S first, S second) {
        return packedLight -> {
            int i = LevelRenderer.getLightColor(first.getLevel(), first.getBlockPos());
            int j = LevelRenderer.getLightColor(second.getLevel(), second.getBlockPos());
            int k = LightTexture.block(i);
            int l = LightTexture.block(j);
            int m = LightTexture.sky(i);
            int n = LightTexture.sky(j);
            return LightTexture.pack(Math.max(k, l), Math.max(m, n));
        };
    }

    public Int2IntFunction acceptSingle(S single) {
        return packedLight -> packedLight;
    }

    public Int2IntFunction acceptNone() {
        return packedLight -> packedLight;
    }
}
