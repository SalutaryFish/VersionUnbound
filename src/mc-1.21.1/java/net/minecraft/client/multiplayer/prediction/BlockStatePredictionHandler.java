package net.minecraft.client.multiplayer.prediction;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap.Entry;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

public class BlockStatePredictionHandler implements AutoCloseable {
    private final Long2ObjectOpenHashMap<BlockStatePredictionHandler.ServerVerifiedState> serverVerifiedStates = new Long2ObjectOpenHashMap<>();
    private int currentSequenceNr;
    private boolean isPredicting;

    public void retainKnownServerState(BlockPos pos, BlockState state, LocalPlayer player) {
        this.serverVerifiedStates
            .compute(
                pos.asLong(),
                (long_, serverVerifiedState) -> serverVerifiedState != null
                    ? serverVerifiedState.setSequence(this.currentSequenceNr)
                    : new BlockStatePredictionHandler.ServerVerifiedState(this.currentSequenceNr, state, player.position())
            );
    }

    public boolean updateKnownServerState(BlockPos pos, BlockState state) {
        BlockStatePredictionHandler.ServerVerifiedState serverVerifiedState = this.serverVerifiedStates.get(pos.asLong());
        if (serverVerifiedState == null) {
            return false;
        }

        serverVerifiedState.setBlockState(state);
        return true;
    }

    public void endPredictionsUpTo(int sequence, ClientLevel level) {
        ObjectIterator<Entry<BlockStatePredictionHandler.ServerVerifiedState>> objectIterator = this.serverVerifiedStates.long2ObjectEntrySet().iterator();

        while (objectIterator.hasNext()) {
            Entry<BlockStatePredictionHandler.ServerVerifiedState> entry = objectIterator.next();
            BlockStatePredictionHandler.ServerVerifiedState serverVerifiedState = entry.getValue();
            if (serverVerifiedState.sequence <= sequence) {
                BlockPos blockPos = BlockPos.of(entry.getLongKey());
                objectIterator.remove();
                level.syncBlockState(blockPos, serverVerifiedState.blockState, serverVerifiedState.playerPos);
            }
        }
    }

    public BlockStatePredictionHandler startPredicting() {
        this.currentSequenceNr++;
        this.isPredicting = true;
        return this;
    }

    @Override
    public void close() {
        this.isPredicting = false;
    }

    public int currentSequence() {
        return this.currentSequenceNr;
    }

    public boolean isPredicting() {
        return this.isPredicting;
    }

    static class ServerVerifiedState {
        final Vec3 playerPos;
        int sequence;
        BlockState blockState;

        ServerVerifiedState(int sequence, BlockState blockState, Vec3 playerPos) {
            this.sequence = sequence;
            this.blockState = blockState;
            this.playerPos = playerPos;
        }

        BlockStatePredictionHandler.ServerVerifiedState setSequence(int sequence) {
            this.sequence = sequence;
            return this;
        }

        void setBlockState(BlockState blockState) {
            this.blockState = blockState;
        }
    }
}
