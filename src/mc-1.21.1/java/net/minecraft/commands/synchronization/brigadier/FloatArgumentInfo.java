package net.minecraft.commands.synchronization.brigadier;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.FloatArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.network.FriendlyByteBuf;

public class FloatArgumentInfo implements ArgumentTypeInfo<FloatArgumentType, FloatArgumentInfo.Template> {
    public void serializeToNetwork(FloatArgumentInfo.Template template, FriendlyByteBuf buffer) {
        boolean bl = template.min != -Float.MAX_VALUE;
        boolean bl2 = template.max != Float.MAX_VALUE;
        buffer.writeByte(ArgumentUtils.createNumberFlags(bl, bl2));
        if (bl) {
            buffer.writeFloat(template.min);
        }

        if (bl2) {
            buffer.writeFloat(template.max);
        }
    }

    public FloatArgumentInfo.Template deserializeFromNetwork(FriendlyByteBuf buffer) {
        byte b = buffer.readByte();
        float f = ArgumentUtils.numberHasMin(b) ? buffer.readFloat() : -Float.MAX_VALUE;
        float g = ArgumentUtils.numberHasMax(b) ? buffer.readFloat() : Float.MAX_VALUE;
        return new FloatArgumentInfo.Template(f, g);
    }

    public void serializeToJson(FloatArgumentInfo.Template template, JsonObject json) {
        if (template.min != -Float.MAX_VALUE) {
            json.addProperty("min", template.min);
        }

        if (template.max != Float.MAX_VALUE) {
            json.addProperty("max", template.max);
        }
    }

    public FloatArgumentInfo.Template unpack(FloatArgumentType argument) {
        return new FloatArgumentInfo.Template(argument.getMinimum(), argument.getMaximum());
    }

    public final class Template implements ArgumentTypeInfo.Template<FloatArgumentType> {
        final float min;
        final float max;

        Template(final float min, final float max) {
            this.min = min;
            this.max = max;
        }

        public FloatArgumentType instantiate(CommandBuildContext context) {
            return FloatArgumentType.floatArg(this.min, this.max);
        }

        @Override
        public ArgumentTypeInfo<FloatArgumentType, ?> type() {
            return FloatArgumentInfo.this;
        }
    }
}
