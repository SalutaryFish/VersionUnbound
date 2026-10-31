package net.salutaryfish.versionunbound.testmod.hooks;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import org.jspecify.annotations.Nullable;

/** This mod's block entity, which overrides the block entity extension's methods and records when vanilla calls them. */
final class HookBlockEntities {
    private HookBlockEntities() {}

    /** "onLoad server", "onDataPacket client 42", ...: both sides in one list (a singleplayer game shares it). */
    static final List<String> HEARD = new CopyOnWriteArrayList<>();

    private static String side(@Nullable Level level) {
        return level != null && level.isClientSide() ? "client" : "server";
    }

    static final class RecorderBlock extends Block implements EntityBlock {
        RecorderBlock(Properties properties) {
            super(properties);
        }

        @Override
        public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
            return new Recorder(pos, state);
        }
    }

    static final class Recorder extends BlockEntity {
        // NeoForge's patched BlockEntity declares getPersistentData; this compiler sees only the extension interface's
        // abstract method, so the override hands the call to BlockEntity's own (Layer A's mixin adds it at run time).
        private static final MethodHandle PERSISTENT_DATA;

        static {
            try {
                PERSISTENT_DATA = MethodHandles.lookup().findSpecial(BlockEntity.class, "getPersistentData", MethodType.methodType(CompoundTag.class), Recorder.class);
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        }

        Recorder(BlockPos pos, BlockState state) {
            super(HooksMod.RECORDER_TYPE.get(), pos, state);
        }

        @Override
        public CompoundTag getPersistentData() {
            try {
                return (CompoundTag) PERSISTENT_DATA.invoke(this);
            } catch (Throwable t) {
                throw new IllegalStateException(t);
            }
        }

        @Override
        public void onLoad() {
            HEARD.add("onLoad " + side(this.level));
            super.onLoad();
        }

        @Override
        public void onChunkUnloaded() {
            HEARD.add("onChunkUnloaded " + side(this.level));
        }

        @Override
        public void onDataPacket(Connection net, ValueInput valueInput) {
            HEARD.add("onDataPacket " + side(this.level) + " " + valueInput.getIntOr("answer", 0));
            super.onDataPacket(net, valueInput);
        }

        @Override
        public void handleUpdateTag(ValueInput input) {
            HEARD.add("handleUpdateTag " + side(this.level) + " " + input.getIntOr("answer", 0));
            super.handleUpdateTag(input);
        }

        @Override
        public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("answer", 42);
            return tag;
        }

        @Override
        public @Nullable Packet<ClientGamePacketListener> getUpdatePacket() {
            return ClientboundBlockEntityDataPacket.create(this);
        }
    }
}
