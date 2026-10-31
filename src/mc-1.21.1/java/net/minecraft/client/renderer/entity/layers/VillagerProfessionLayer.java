package net.minecraft.client.renderer.entity.layers;

import com.mojang.blaze3d.vertex.PoseStack;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.io.IOException;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.VillagerHeadModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.resources.metadata.animation.VillagerMetaDataSection;
import net.minecraft.core.DefaultedRegistry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.npc.VillagerData;
import net.minecraft.world.entity.npc.VillagerDataHolder;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;

public class VillagerProfessionLayer<T extends LivingEntity & VillagerDataHolder, M extends EntityModel<T> & VillagerHeadModel> extends RenderLayer<T, M> {
    private static final Int2ObjectMap<ResourceLocation> LEVEL_LOCATIONS = Util.make(new Int2ObjectOpenHashMap<>(), map -> {
        map.put(1, ResourceLocation.withDefaultNamespace("stone"));
        map.put(2, ResourceLocation.withDefaultNamespace("iron"));
        map.put(3, ResourceLocation.withDefaultNamespace("gold"));
        map.put(4, ResourceLocation.withDefaultNamespace("emerald"));
        map.put(5, ResourceLocation.withDefaultNamespace("diamond"));
    });
    private final Object2ObjectMap<VillagerType, VillagerMetaDataSection.Hat> typeHatCache = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<VillagerProfession, VillagerMetaDataSection.Hat> professionHatCache = new Object2ObjectOpenHashMap<>();
    private final ResourceManager resourceManager;
    private final String path;

    public VillagerProfessionLayer(RenderLayerParent<T, M> renderer, ResourceManager resourceManager, String path) {
        super(renderer);
        this.resourceManager = resourceManager;
        this.path = path;
    }

    public void render(
        PoseStack poseStack,
        MultiBufferSource buffer,
        int packedLight,
        T livingEntity,
        float limbSwing,
        float limbSwingAmount,
        float partialTicks,
        float ageInTicks,
        float netHeadYaw,
        float headPitch
    ) {
        if (!livingEntity.isInvisible()) {
            VillagerData villagerData = livingEntity.getVillagerData();
            VillagerType villagerType = villagerData.getType();
            VillagerProfession villagerProfession = villagerData.getProfession();
            VillagerMetaDataSection.Hat hat = this.getHatData(this.typeHatCache, "type", BuiltInRegistries.VILLAGER_TYPE, villagerType);
            VillagerMetaDataSection.Hat hat2 = this.getHatData(this.professionHatCache, "profession", BuiltInRegistries.VILLAGER_PROFESSION, villagerProfession);
            M entityModel = this.getParentModel();
            entityModel.hatVisible(
                hat2 == VillagerMetaDataSection.Hat.NONE || hat2 == VillagerMetaDataSection.Hat.PARTIAL && hat != VillagerMetaDataSection.Hat.FULL
            );
            ResourceLocation resourceLocation = this.getResourceLocation("type", BuiltInRegistries.VILLAGER_TYPE.getKey(villagerType));
            renderColoredCutoutModel(entityModel, resourceLocation, poseStack, buffer, packedLight, livingEntity, -1);
            entityModel.hatVisible(true);
            if (villagerProfession != VillagerProfession.NONE && !livingEntity.isBaby()) {
                ResourceLocation resourceLocation2 = this.getResourceLocation("profession", BuiltInRegistries.VILLAGER_PROFESSION.getKey(villagerProfession));
                renderColoredCutoutModel(entityModel, resourceLocation2, poseStack, buffer, packedLight, livingEntity, -1);
                if (villagerProfession != VillagerProfession.NITWIT) {
                    ResourceLocation resourceLocation3 = this.getResourceLocation(
                        "profession_level", LEVEL_LOCATIONS.get(Mth.clamp(villagerData.getLevel(), 1, LEVEL_LOCATIONS.size()))
                    );
                    renderColoredCutoutModel(entityModel, resourceLocation3, poseStack, buffer, packedLight, livingEntity, -1);
                }
            }
        }
    }

    private ResourceLocation getResourceLocation(String folder, ResourceLocation location) {
        return location.withPath(string2 -> "textures/entity/" + this.path + "/" + folder + "/" + string2 + ".png");
    }

    public <K> VillagerMetaDataSection.Hat getHatData(
        Object2ObjectMap<K, VillagerMetaDataSection.Hat> cache, String folder, DefaultedRegistry<K> villagerTypeRegistry, K key
    ) {
        return cache.computeIfAbsent(
            key, object2 -> this.resourceManager.getResource(this.getResourceLocation(folder, villagerTypeRegistry.getKey(key))).flatMap(resource -> {
                try {
                    return resource.metadata().getSection(VillagerMetaDataSection.SERIALIZER).map(VillagerMetaDataSection::getHat);
                } catch (IOException iOException) {
                    return Optional.empty();
                }
            }).orElse(VillagerMetaDataSection.Hat.NONE)
        );
    }
}
