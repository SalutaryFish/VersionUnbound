package net.minecraft.world.level.gameevent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.network.protocol.game.DebugPackets;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;

public class GameEventDispatcher {
    private final ServerLevel level;

    public GameEventDispatcher(ServerLevel level) {
        this.level = level;
    }

    public void post(Holder<GameEvent> gameEvent, Vec3 pos, GameEvent.Context context) {
        int i = gameEvent.value().notificationRadius();
        BlockPos blockPos = BlockPos.containing(pos);
        int j = SectionPos.blockToSectionCoord(blockPos.getX() - i);
        int k = SectionPos.blockToSectionCoord(blockPos.getY() - i);
        int l = SectionPos.blockToSectionCoord(blockPos.getZ() - i);
        int m = SectionPos.blockToSectionCoord(blockPos.getX() + i);
        int n = SectionPos.blockToSectionCoord(blockPos.getY() + i);
        int o = SectionPos.blockToSectionCoord(blockPos.getZ() + i);
        List<GameEvent.ListenerInfo> list = new ArrayList<>();
        GameEventListenerRegistry.ListenerVisitor listenerVisitor = (gameEventListener, vec32) -> {
            if (gameEventListener.getDeliveryMode() == GameEventListener.DeliveryMode.BY_DISTANCE) {
                list.add(new GameEvent.ListenerInfo(gameEvent, pos, context, gameEventListener, vec32));
            } else {
                gameEventListener.handleGameEvent(this.level, gameEvent, context, pos);
            }
        };
        boolean bl = false;

        for (int p = j; p <= m; p++) {
            for (int q = l; q <= o; q++) {
                ChunkAccess chunkAccess = this.level.getChunkSource().getChunkNow(p, q);
                if (chunkAccess != null) {
                    for (int r = k; r <= n; r++) {
                        bl |= chunkAccess.getListenerRegistry(r).visitInRangeListeners(gameEvent, pos, context, listenerVisitor);
                    }
                }
            }
        }

        if (!list.isEmpty()) {
            this.handleGameEventMessagesInQueue(list);
        }

        if (bl) {
            DebugPackets.sendGameEventInfo(this.level, gameEvent, pos);
        }
    }

    private void handleGameEventMessagesInQueue(List<GameEvent.ListenerInfo> listenerInfos) {
        Collections.sort(listenerInfos);

        for (GameEvent.ListenerInfo listenerInfo : listenerInfos) {
            GameEventListener gameEventListener = listenerInfo.recipient();
            gameEventListener.handleGameEvent(this.level, listenerInfo.gameEvent(), listenerInfo.context(), listenerInfo.source());
        }
    }
}
