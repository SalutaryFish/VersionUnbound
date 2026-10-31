package net.minecraft.client.renderer.debug;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Position;
import net.minecraft.network.protocol.common.custom.BrainDebugPayload;
import net.minecraft.network.protocol.game.DebugEntityNameGenerator;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.slf4j.Logger;

public class BrainDebugRenderer implements DebugRenderer.SimpleDebugRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final boolean SHOW_NAME_FOR_ALL = true;
    private static final boolean SHOW_PROFESSION_FOR_ALL = false;
    private static final boolean SHOW_BEHAVIORS_FOR_ALL = false;
    private static final boolean SHOW_ACTIVITIES_FOR_ALL = false;
    private static final boolean SHOW_INVENTORY_FOR_ALL = false;
    private static final boolean SHOW_GOSSIPS_FOR_ALL = false;
    private static final boolean SHOW_PATH_FOR_ALL = false;
    private static final boolean SHOW_HEALTH_FOR_ALL = false;
    private static final boolean SHOW_WANTS_GOLEM_FOR_ALL = true;
    private static final boolean SHOW_ANGER_LEVEL_FOR_ALL = false;
    private static final boolean SHOW_NAME_FOR_SELECTED = true;
    private static final boolean SHOW_PROFESSION_FOR_SELECTED = true;
    private static final boolean SHOW_BEHAVIORS_FOR_SELECTED = true;
    private static final boolean SHOW_ACTIVITIES_FOR_SELECTED = true;
    private static final boolean SHOW_MEMORIES_FOR_SELECTED = true;
    private static final boolean SHOW_INVENTORY_FOR_SELECTED = true;
    private static final boolean SHOW_GOSSIPS_FOR_SELECTED = true;
    private static final boolean SHOW_PATH_FOR_SELECTED = true;
    private static final boolean SHOW_HEALTH_FOR_SELECTED = true;
    private static final boolean SHOW_WANTS_GOLEM_FOR_SELECTED = true;
    private static final boolean SHOW_ANGER_LEVEL_FOR_SELECTED = true;
    private static final boolean SHOW_POI_INFO = true;
    private static final int MAX_RENDER_DIST_FOR_BRAIN_INFO = 30;
    private static final int MAX_RENDER_DIST_FOR_POI_INFO = 30;
    private static final int MAX_TARGETING_DIST = 8;
    private static final float TEXT_SCALE = 0.02F;
    private static final int WHITE = -1;
    private static final int YELLOW = -256;
    private static final int CYAN = -16711681;
    private static final int GREEN = -16711936;
    private static final int GRAY = -3355444;
    private static final int PINK = -98404;
    private static final int RED = -65536;
    private static final int ORANGE = -23296;
    private final Minecraft minecraft;
    private final Map<BlockPos, BrainDebugRenderer.PoiInfo> pois = Maps.newHashMap();
    private final Map<UUID, BrainDebugPayload.BrainDump> brainDumpsPerEntity = Maps.newHashMap();
    @Nullable
    private UUID lastLookedAtUuid;

    public BrainDebugRenderer(Minecraft minecraft) {
        this.minecraft = minecraft;
    }

    @Override
    public void clear() {
        this.pois.clear();
        this.brainDumpsPerEntity.clear();
        this.lastLookedAtUuid = null;
    }

    public void addPoi(BrainDebugRenderer.PoiInfo poiInfo) {
        this.pois.put(poiInfo.pos, poiInfo);
    }

    public void removePoi(BlockPos pos) {
        this.pois.remove(pos);
    }

    public void setFreeTicketCount(BlockPos pos, int freeTicketCount) {
        BrainDebugRenderer.PoiInfo poiInfo = this.pois.get(pos);
        if (poiInfo == null) {
            LOGGER.warn("Strange, setFreeTicketCount was called for an unknown POI: {}", pos);
        } else {
            poiInfo.freeTicketCount = freeTicketCount;
        }
    }

    public void addOrUpdateBrainDump(BrainDebugPayload.BrainDump brainDump) {
        this.brainDumpsPerEntity.put(brainDump.uuid(), brainDump);
    }

    public void removeBrainDump(int id) {
        this.brainDumpsPerEntity.values().removeIf(dump -> dump.id() == id);
    }

    @Override
    public void render(PoseStack poseStack, MultiBufferSource bufferSource, double camX, double camY, double camZ) {
        this.clearRemovedEntities();
        this.doRender(poseStack, bufferSource, camX, camY, camZ);
        if (!this.minecraft.player.isSpectator()) {
            this.updateLastLookedAtUuid();
        }
    }

    private void clearRemovedEntities() {
        this.brainDumpsPerEntity.entrySet().removeIf(brainDump -> {
            Entity entity = this.minecraft.level.getEntity(brainDump.getValue().id());
            return entity == null || entity.isRemoved();
        });
    }

    private void doRender(PoseStack poseStack, MultiBufferSource buffer, double x, double y, double z) {
        BlockPos blockPos = BlockPos.containing(x, y, z);
        this.brainDumpsPerEntity.values().forEach(dump -> {
            if (this.isPlayerCloseEnoughToMob(dump)) {
                this.renderBrainInfo(poseStack, buffer, dump, x, y, z);
            }
        });

        for (BlockPos blockPos2 : this.pois.keySet()) {
            if (blockPos.closerThan(blockPos2, 30.0)) {
                highlightPoi(poseStack, buffer, blockPos2);
            }
        }

        this.pois.values().forEach(poi -> {
            if (blockPos.closerThan(poi.pos, 30.0)) {
                this.renderPoiInfo(poseStack, buffer, poi);
            }
        });
        this.getGhostPois().forEach((blockPosx, poiName) -> {
            if (blockPos.closerThan(blockPosx, 30.0)) {
                this.renderGhostPoi(poseStack, buffer, blockPosx, (List<String>)poiName);
            }
        });
    }

    private static void highlightPoi(PoseStack poseStack, MultiBufferSource buffer, BlockPos pos) {
        float f = 0.05F;
        DebugRenderer.renderFilledBox(poseStack, buffer, pos, 0.05F, 0.2F, 0.2F, 1.0F, 0.3F);
    }

    private void renderGhostPoi(PoseStack poseStack, MultiBufferSource buffer, BlockPos pos, List<String> poiName) {
        float f = 0.05F;
        DebugRenderer.renderFilledBox(poseStack, buffer, pos, 0.05F, 0.2F, 0.2F, 1.0F, 0.3F);
        renderTextOverPos(poseStack, buffer, poiName + "", pos, 0, -256);
        renderTextOverPos(poseStack, buffer, "Ghost POI", pos, 1, -65536);
    }

    private void renderPoiInfo(PoseStack poseStack, MultiBufferSource buffer, BrainDebugRenderer.PoiInfo poiInfo) {
        int i = 0;
        Set<String> set = this.getTicketHolderNames(poiInfo);
        if (set.size() < 4) {
            renderTextOverPoi(poseStack, buffer, "Owners: " + set, poiInfo, i, -256);
        } else {
            renderTextOverPoi(poseStack, buffer, set.size() + " ticket holders", poiInfo, i, -256);
        }

        i++;
        Set<String> set2 = this.getPotentialTicketHolderNames(poiInfo);
        if (set2.size() < 4) {
            renderTextOverPoi(poseStack, buffer, "Candidates: " + set2, poiInfo, i, -23296);
        } else {
            renderTextOverPoi(poseStack, buffer, set2.size() + " potential owners", poiInfo, i, -23296);
        }

        renderTextOverPoi(poseStack, buffer, "Free tickets: " + poiInfo.freeTicketCount, poiInfo, ++i, -256);
        renderTextOverPoi(poseStack, buffer, poiInfo.type, poiInfo, ++i, -1);
    }

    private void renderPath(PoseStack poseStack, MultiBufferSource buffer, BrainDebugPayload.BrainDump brainDump, double x, double y, double z) {
        if (brainDump.path() != null) {
            PathfindingRenderer.renderPath(poseStack, buffer, brainDump.path(), 0.5F, false, false, x, y, z);
        }
    }

    private void renderBrainInfo(PoseStack poseStack, MultiBufferSource buffer, BrainDebugPayload.BrainDump brainDump, double x, double y, double z) {
        boolean bl = this.isMobSelected(brainDump);
        int i = 0;
        renderTextOverMob(poseStack, buffer, brainDump.pos(), i, brainDump.name(), -1, 0.03F);
        i++;
        if (bl) {
            renderTextOverMob(poseStack, buffer, brainDump.pos(), i, brainDump.profession() + " " + brainDump.xp() + " xp", -1, 0.02F);
            i++;
        }

        if (bl) {
            int j = brainDump.health() < brainDump.maxHealth() ? -23296 : -1;
            renderTextOverMob(
                poseStack,
                buffer,
                brainDump.pos(),
                i,
                "health: " + String.format(Locale.ROOT, "%.1f", brainDump.health()) + " / " + String.format(Locale.ROOT, "%.1f", brainDump.maxHealth()),
                j,
                0.02F
            );
            i++;
        }

        if (bl && !brainDump.inventory().equals("")) {
            renderTextOverMob(poseStack, buffer, brainDump.pos(), i, brainDump.inventory(), -98404, 0.02F);
            i++;
        }

        if (bl) {
            for (String string : brainDump.behaviors()) {
                renderTextOverMob(poseStack, buffer, brainDump.pos(), i, string, -16711681, 0.02F);
                i++;
            }
        }

        if (bl) {
            for (String string : brainDump.activities()) {
                renderTextOverMob(poseStack, buffer, brainDump.pos(), i, string, -16711936, 0.02F);
                i++;
            }
        }

        if (brainDump.wantsGolem()) {
            renderTextOverMob(poseStack, buffer, brainDump.pos(), i, "Wants Golem", -23296, 0.02F);
            i++;
        }

        if (bl && brainDump.angerLevel() != -1) {
            renderTextOverMob(poseStack, buffer, brainDump.pos(), i, "Anger Level: " + brainDump.angerLevel(), -98404, 0.02F);
            i++;
        }

        if (bl) {
            for (String string : brainDump.gossips()) {
                if (string.startsWith(brainDump.name())) {
                    renderTextOverMob(poseStack, buffer, brainDump.pos(), i, string, -1, 0.02F);
                } else {
                    renderTextOverMob(poseStack, buffer, brainDump.pos(), i, string, -23296, 0.02F);
                }

                i++;
            }
        }

        if (bl) {
            for (String string : Lists.reverse(brainDump.memories())) {
                renderTextOverMob(poseStack, buffer, brainDump.pos(), i, string, -3355444, 0.02F);
                i++;
            }
        }

        if (bl) {
            this.renderPath(poseStack, buffer, brainDump, x, y, z);
        }
    }

    private static void renderTextOverPoi(PoseStack poseStack, MultiBufferSource buffer, String text, BrainDebugRenderer.PoiInfo poiInfo, int layer, int color) {
        renderTextOverPos(poseStack, buffer, text, poiInfo.pos, layer, color);
    }

    private static void renderTextOverPos(PoseStack poseStack, MultiBufferSource buffer, String text, BlockPos pos, int layer, int color) {
        double d = 1.3;
        double e = 0.2;
        double f = pos.getX() + 0.5;
        double g = pos.getY() + 1.3 + layer * 0.2;
        double h = pos.getZ() + 0.5;
        DebugRenderer.renderFloatingText(poseStack, buffer, text, f, g, h, color, 0.02F, true, 0.0F, true);
    }

    private static void renderTextOverMob(PoseStack poseStack, MultiBufferSource buffer, Position pos, int layer, String text, int color, float scale) {
        double d = 2.4;
        double e = 0.25;
        BlockPos blockPos = BlockPos.containing(pos);
        double f = blockPos.getX() + 0.5;
        double g = pos.y() + 2.4 + layer * 0.25;
        double h = blockPos.getZ() + 0.5;
        float i = 0.5F;
        DebugRenderer.renderFloatingText(poseStack, buffer, text, f, g, h, color, scale, false, 0.5F, true);
    }

    private Set<String> getTicketHolderNames(BrainDebugRenderer.PoiInfo poiInfo) {
        return this.getTicketHolders(poiInfo.pos).stream().map(DebugEntityNameGenerator::getEntityName).collect(Collectors.toSet());
    }

    private Set<String> getPotentialTicketHolderNames(BrainDebugRenderer.PoiInfo poiInfo) {
        return this.getPotentialTicketHolders(poiInfo.pos).stream().map(DebugEntityNameGenerator::getEntityName).collect(Collectors.toSet());
    }

    private boolean isMobSelected(BrainDebugPayload.BrainDump brainDump) {
        return Objects.equals(this.lastLookedAtUuid, brainDump.uuid());
    }

    private boolean isPlayerCloseEnoughToMob(BrainDebugPayload.BrainDump brainDump) {
        Player player = this.minecraft.player;
        BlockPos blockPos = BlockPos.containing(player.getX(), brainDump.pos().y(), player.getZ());
        BlockPos blockPos2 = BlockPos.containing(brainDump.pos());
        return blockPos.closerThan(blockPos2, 30.0);
    }

    private Collection<UUID> getTicketHolders(BlockPos pos) {
        return this.brainDumpsPerEntity.values().stream().filter(dump -> dump.hasPoi(pos)).map(BrainDebugPayload.BrainDump::uuid).collect(Collectors.toSet());
    }

    private Collection<UUID> getPotentialTicketHolders(BlockPos pos) {
        return this.brainDumpsPerEntity
            .values()
            .stream()
            .filter(drump -> drump.hasPotentialPoi(pos))
            .map(BrainDebugPayload.BrainDump::uuid)
            .collect(Collectors.toSet());
    }

    private Map<BlockPos, List<String>> getGhostPois() {
        Map<BlockPos, List<String>> map = Maps.newHashMap();

        for (BrainDebugPayload.BrainDump brainDump : this.brainDumpsPerEntity.values()) {
            for (BlockPos blockPos : Iterables.concat(brainDump.pois(), brainDump.potentialPois())) {
                if (!this.pois.containsKey(blockPos)) {
                    map.computeIfAbsent(blockPos, pos -> Lists.newArrayList()).add(brainDump.name());
                }
            }
        }

        return map;
    }

    private void updateLastLookedAtUuid() {
        DebugRenderer.getTargetedEntity(this.minecraft.getCameraEntity(), 8).ifPresent(targetedEntity -> this.lastLookedAtUuid = targetedEntity.getUUID());
    }

    public static class PoiInfo {
        public final BlockPos pos;
        public final String type;
        public int freeTicketCount;

        public PoiInfo(BlockPos pos, String type, int freeTicketCount) {
            this.pos = pos;
            this.type = type;
            this.freeTicketCount = freeTicketCount;
        }
    }
}
