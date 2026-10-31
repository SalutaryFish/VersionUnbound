package net.minecraft.world.item.component;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.MapDecoder;
import com.mojang.serialization.MapEncoder;
import com.mojang.serialization.MapLike;
import io.netty.buffer.ByteBuf;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.slf4j.Logger;

public final class CustomData {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final CustomData EMPTY = new CustomData(new CompoundTag());
    public static final Codec<CustomData> CODEC = Codec.withAlternative(CompoundTag.CODEC, TagParser.AS_CODEC)
        .xmap(CustomData::new, customData -> customData.tag);
    public static final Codec<CustomData> CODEC_WITH_ID = CODEC.validate(
        customData -> customData.getUnsafe().contains("id", 8)
            ? DataResult.success(customData)
            : DataResult.error(() -> "Missing id for entity in: " + customData)
    );
    @Deprecated
    public static final StreamCodec<ByteBuf, CustomData> STREAM_CODEC = ByteBufCodecs.COMPOUND_TAG.map(CustomData::new, customData -> customData.tag);
    private final CompoundTag tag;

    private CustomData(CompoundTag tag) {
        this.tag = tag;
    }

    public static CustomData of(CompoundTag tag) {
        return new CustomData(tag.copy());
    }

    public static Predicate<ItemStack> itemMatcher(DataComponentType<CustomData> componentType, CompoundTag tag) {
        return itemStack -> {
            CustomData customData = itemStack.getOrDefault(componentType, EMPTY);
            return customData.matchedBy(tag);
        };
    }

    public boolean matchedBy(CompoundTag tag) {
        return NbtUtils.compareNbt(tag, this.tag, true);
    }

    public static void update(DataComponentType<CustomData> componentType, ItemStack stack, Consumer<CompoundTag> updater) {
        CustomData customData = stack.getOrDefault(componentType, EMPTY).update(updater);
        if (customData.tag.isEmpty()) {
            stack.remove(componentType);
        } else {
            stack.set(componentType, customData);
        }
    }

    public static void set(DataComponentType<CustomData> componentType, ItemStack stack, CompoundTag tag) {
        if (!tag.isEmpty()) {
            stack.set(componentType, of(tag));
        } else {
            stack.remove(componentType);
        }
    }

    public CustomData update(Consumer<CompoundTag> updater) {
        CompoundTag compoundTag = this.tag.copy();
        updater.accept(compoundTag);
        return new CustomData(compoundTag);
    }

    public void loadInto(Entity entity) {
        CompoundTag compoundTag = entity.saveWithoutId(new CompoundTag());
        UUID uUID = entity.getUUID();
        compoundTag.merge(this.tag);
        entity.load(compoundTag);
        entity.setUUID(uUID);
    }

    public boolean loadInto(BlockEntity blockEntity, HolderLookup.Provider levelRegistry) {
        CompoundTag compoundTag = blockEntity.saveCustomOnly(levelRegistry);
        CompoundTag compoundTag2 = compoundTag.copy();
        compoundTag.merge(this.tag);
        if (!compoundTag.equals(compoundTag2)) {
            try {
                blockEntity.loadCustomOnly(compoundTag, levelRegistry);
                blockEntity.setChanged();
                return true;
            } catch (Exception exception) {
                LOGGER.warn("Failed to apply custom data to block entity at {}", blockEntity.getBlockPos(), exception);

                try {
                    blockEntity.loadCustomOnly(compoundTag2, levelRegistry);
                } catch (Exception exception2) {
                    LOGGER.warn("Failed to rollback block entity at {} after failure", blockEntity.getBlockPos(), exception2);
                }
            }
        }

        return false;
    }

    public <T> DataResult<CustomData> update(DynamicOps<Tag> ops, MapEncoder<T> encoder, T value) {
        return encoder.encode(value, ops, ops.mapBuilder()).build(this.tag).map(tag -> new CustomData((CompoundTag)tag));
    }

    public <T> DataResult<T> read(MapDecoder<T> decoder) {
        return this.read(NbtOps.INSTANCE, decoder);
    }

    public <T> DataResult<T> read(DynamicOps<Tag> ops, MapDecoder<T> decoder) {
        MapLike<Tag> mapLike = ops.getMap(this.tag).getOrThrow();
        return decoder.decode(ops, mapLike);
    }

    public int size() {
        return this.tag.size();
    }

    public boolean isEmpty() {
        return this.tag.isEmpty();
    }

    public CompoundTag copyTag() {
        return this.tag.copy();
    }

    public boolean contains(String key) {
        return this.tag.contains(key);
    }

    @Override
    public boolean equals(Object other) {
        if (other == this) {
            return true;
        } else {
            return other instanceof CustomData customData ? this.tag.equals(customData.tag) : false;
        }
    }

    @Override
    public int hashCode() {
        return this.tag.hashCode();
    }

    @Override
    public String toString() {
        return this.tag.toString();
    }

    @Deprecated
    public CompoundTag getUnsafe() {
        return this.tag;
    }
}
