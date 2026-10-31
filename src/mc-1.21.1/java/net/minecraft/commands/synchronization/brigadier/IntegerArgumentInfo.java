package net.minecraft.commands.synchronization.brigadier;

import com.google.gson.JsonObject;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.synchronization.ArgumentTypeInfo;
import net.minecraft.commands.synchronization.ArgumentUtils;
import net.minecraft.network.FriendlyByteBuf;

public class IntegerArgumentInfo implements ArgumentTypeInfo<IntegerArgumentType, IntegerArgumentInfo.Template> {
    public void serializeToNetwork(IntegerArgumentInfo.Template template, FriendlyByteBuf buffer) {
        boolean bl = template.min != Integer.MIN_VALUE;
        boolean bl2 = template.max != Integer.MAX_VALUE;
        buffer.writeByte(ArgumentUtils.createNumberFlags(bl, bl2));
        if (bl) {
            buffer.writeInt(template.min);
        }

        if (bl2) {
            buffer.writeInt(template.max);
        }
    }

    public IntegerArgumentInfo.Template deserializeFromNetwork(FriendlyByteBuf buffer) {
        byte b = buffer.readByte();
        int i = ArgumentUtils.numberHasMin(b) ? buffer.readInt() : Integer.MIN_VALUE;
        int j = ArgumentUtils.numberHasMax(b) ? buffer.readInt() : Integer.MAX_VALUE;
        return new IntegerArgumentInfo.Template(i, j);
    }

    public void serializeToJson(IntegerArgumentInfo.Template template, JsonObject json) {
        if (template.min != Integer.MIN_VALUE) {
            json.addProperty("min", template.min);
        }

        if (template.max != Integer.MAX_VALUE) {
            json.addProperty("max", template.max);
        }
    }

    public IntegerArgumentInfo.Template unpack(IntegerArgumentType argument) {
        return new IntegerArgumentInfo.Template(argument.getMinimum(), argument.getMaximum());
    }

    public final class Template implements ArgumentTypeInfo.Template<IntegerArgumentType> {
        final int min;
        final int max;

        Template(final int min, final int max) {
            this.min = min;
            this.max = max;
        }

        public IntegerArgumentType instantiate(CommandBuildContext context) {
            return IntegerArgumentType.integer(this.min, this.max);
        }

        @Override
        public ArgumentTypeInfo<IntegerArgumentType, ?> type() {
            return IntegerArgumentInfo.this;
        }
    }
}
