package net.minecraft.client.multiplayer;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Map.Entry;
import javax.annotation.Nullable;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementNode;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.advancements.AdvancementTree;
import net.minecraft.advancements.DisplayInfo;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.telemetry.WorldSessionTelemetryManager;
import net.minecraft.network.protocol.game.ClientboundUpdateAdvancementsPacket;
import net.minecraft.network.protocol.game.ServerboundSeenAdvancementsPacket;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

public class ClientAdvancements {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Minecraft minecraft;
    private final WorldSessionTelemetryManager telemetryManager;
    private final AdvancementTree tree = new AdvancementTree();
    private final Map<AdvancementHolder, AdvancementProgress> progress = new Object2ObjectOpenHashMap<>();
    @Nullable
    private ClientAdvancements.Listener listener;
    @Nullable
    private AdvancementHolder selectedTab;

    public ClientAdvancements(Minecraft minecraft, WorldSessionTelemetryManager telemetryManager) {
        this.minecraft = minecraft;
        this.telemetryManager = telemetryManager;
    }

    public void update(ClientboundUpdateAdvancementsPacket packet) {
        if (packet.shouldReset()) {
            this.tree.clear();
            this.progress.clear();
        }

        this.tree.remove(packet.getRemoved());
        this.tree.addAll(packet.getAdded());

        for (Entry<ResourceLocation, AdvancementProgress> entry : packet.getProgress().entrySet()) {
            AdvancementNode advancementNode = this.tree.get(entry.getKey());
            if (advancementNode != null) {
                AdvancementProgress advancementProgress = entry.getValue();
                advancementProgress.update(advancementNode.advancement().requirements());
                this.progress.put(advancementNode.holder(), advancementProgress);
                if (this.listener != null) {
                    this.listener.onUpdateAdvancementProgress(advancementNode, advancementProgress);
                }

                if (!packet.shouldReset() && advancementProgress.isDone()) {
                    if (this.minecraft.level != null) {
                        this.telemetryManager.onAdvancementDone(this.minecraft.level, advancementNode.holder());
                    }

                    Optional<DisplayInfo> optional = advancementNode.advancement().display();
                    if (optional.isPresent() && optional.get().shouldShowToast()) {
                        this.minecraft.getToasts().addToast(new AdvancementToast(advancementNode.holder()));
                    }
                }
            } else {
                LOGGER.warn("Server informed client about progress for unknown advancement {}", entry.getKey());
            }
        }
    }

    public AdvancementTree getTree() {
        return this.tree;
    }

    public void setSelectedTab(@Nullable AdvancementHolder advancement, boolean tellServer) {
        ClientPacketListener clientPacketListener = this.minecraft.getConnection();
        if (clientPacketListener != null && advancement != null && tellServer) {
            clientPacketListener.send(ServerboundSeenAdvancementsPacket.openedTab(advancement));
        }

        if (this.selectedTab != advancement) {
            this.selectedTab = advancement;
            if (this.listener != null) {
                this.listener.onSelectedTabChanged(advancement);
            }
        }
    }

    public void setListener(@Nullable ClientAdvancements.Listener listener) {
        this.listener = listener;
        this.tree.setListener(listener);
        if (listener != null) {
            this.progress.forEach((holder, progress) -> {
                AdvancementNode advancementNode = this.tree.get(holder);
                if (advancementNode != null) {
                    listener.onUpdateAdvancementProgress(advancementNode, progress);
                }
            });
            listener.onSelectedTabChanged(this.selectedTab);
        }
    }

    @Nullable
    public AdvancementHolder get(ResourceLocation id) {
        AdvancementNode advancementNode = this.tree.get(id);
        return advancementNode != null ? advancementNode.holder() : null;
    }

    public interface Listener extends AdvancementTree.Listener {
        void onUpdateAdvancementProgress(AdvancementNode var1, AdvancementProgress var2);

        void onSelectedTabChanged(@Nullable AdvancementHolder var1);
    }
}
