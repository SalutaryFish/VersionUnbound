package net.minecraft.client.renderer.item;

import com.google.common.collect.Maps;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.component.DataComponents;
import net.minecraft.data.models.ItemModelGenerators;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.item.CrossbowItem;
import net.minecraft.world.item.ElytraItem;
import net.minecraft.world.item.FishingRodItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.item.component.ChargedProjectiles;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.LodestoneTracker;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LightBlock;

public class ItemProperties {
    private static final Map<ResourceLocation, ItemPropertyFunction> GENERIC_PROPERTIES = Maps.newHashMap();
    private static final ResourceLocation DAMAGED = ResourceLocation.withDefaultNamespace("damaged");
    private static final ResourceLocation DAMAGE = ResourceLocation.withDefaultNamespace("damage");
    private static final ClampedItemPropertyFunction PROPERTY_DAMAGED = (stack, level, entity, seed) -> stack.isDamaged() ? 1.0F : 0.0F;
    private static final ClampedItemPropertyFunction PROPERTY_DAMAGE = (stack, level, entity, seed) -> Mth.clamp(
        (float)stack.getDamageValue() / stack.getMaxDamage(), 0.0F, 1.0F
    );
    private static final Map<Item, Map<ResourceLocation, ItemPropertyFunction>> PROPERTIES = Maps.newHashMap();

    private static ClampedItemPropertyFunction registerGeneric(ResourceLocation name, ClampedItemPropertyFunction property) {
        GENERIC_PROPERTIES.put(name, property);
        return property;
    }

    private static void registerCustomModelData(ItemPropertyFunction property) {
        GENERIC_PROPERTIES.put(ResourceLocation.withDefaultNamespace("custom_model_data"), property);
    }

    private static void register(Item item, ResourceLocation name, ClampedItemPropertyFunction property) {
        PROPERTIES.computeIfAbsent(item, k -> Maps.newHashMap()).put(name, property);
    }

    @Nullable
    public static ItemPropertyFunction getProperty(ItemStack stack, ResourceLocation location) {
        if (stack.getMaxDamage() > 0) {
            if (DAMAGE.equals(location)) {
                return PROPERTY_DAMAGE;
            }

            if (DAMAGED.equals(location)) {
                return PROPERTY_DAMAGED;
            }
        }

        ItemPropertyFunction itemPropertyFunction = GENERIC_PROPERTIES.get(location);
        if (itemPropertyFunction != null) {
            return itemPropertyFunction;
        }

        Map<ResourceLocation, ItemPropertyFunction> map = PROPERTIES.get(stack.getItem());
        return map == null ? null : map.get(location);
    }

    static {
        registerGeneric(
            ResourceLocation.withDefaultNamespace("lefthanded"),
            (stack, level, entity, seed) -> entity != null && entity.getMainArm() != HumanoidArm.RIGHT ? 1.0F : 0.0F
        );
        registerGeneric(
            ResourceLocation.withDefaultNamespace("cooldown"),
            (stack, level, entity, seed) -> entity instanceof Player ? ((Player)entity).getCooldowns().getCooldownPercent(stack.getItem(), 0.0F) : 0.0F
        );
        ClampedItemPropertyFunction clampedItemPropertyFunction = (stack, level, entity, seed) -> {
            ArmorTrim armorTrim = stack.get(DataComponents.TRIM);
            return armorTrim != null ? armorTrim.material().value().itemModelIndex() : Float.NEGATIVE_INFINITY;
        };
        registerGeneric(ItemModelGenerators.TRIM_TYPE_PREDICATE_ID, clampedItemPropertyFunction);
        registerCustomModelData((stack, level, entity, seed) -> stack.getOrDefault(DataComponents.CUSTOM_MODEL_DATA, CustomModelData.DEFAULT).value());
        register(Items.BOW, ResourceLocation.withDefaultNamespace("pull"), (stack, level, entity, seed) -> {
            if (entity == null) {
                return 0.0F;
            } else {
                return entity.getUseItem() != stack ? 0.0F : (stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / 20.0F;
            }
        });
        register(
            Items.BRUSH,
            ResourceLocation.withDefaultNamespace("brushing"),
            (stack, level, entity, seed) -> entity != null && entity.getUseItem() == stack ? entity.getUseItemRemainingTicks() % 10 / 10.0F : 0.0F
        );
        register(
            Items.BOW,
            ResourceLocation.withDefaultNamespace("pulling"),
            (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F
        );
        register(Items.BUNDLE, ResourceLocation.withDefaultNamespace("filled"), (stack, level, entity, seed) -> BundleItem.getFullnessDisplay(stack));
        register(Items.CLOCK, ResourceLocation.withDefaultNamespace("time"), new ClampedItemPropertyFunction() {
            private double rotation;
            private double rota;
            private long lastUpdateTick;

            @Override
            public float unclampedCall(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity, int seed) {
                Entity entity2 = entity != null ? entity : stack.getEntityRepresentation();
                if (entity2 == null) {
                    return 0.0F;
                }

                if (level == null && entity2.level() instanceof ClientLevel) {
                    level = (ClientLevel)entity2.level();
                }

                if (level == null) {
                    return 0.0F;
                }

                double d;
                if (level.dimensionType().natural()) {
                    d = level.getTimeOfDay(1.0F);
                } else {
                    d = Math.random();
                }

                d = this.wobble(level, d);
                return (float)d;
            }

            private double wobble(Level level, double rotation) {
                if (level.getGameTime() != this.lastUpdateTick) {
                    this.lastUpdateTick = level.getGameTime();
                    double d = rotation - this.rotation;
                    d = Mth.positiveModulo(d + 0.5, 1.0) - 0.5;
                    this.rota += d * 0.1;
                    this.rota *= 0.9;
                    this.rotation = Mth.positiveModulo(this.rotation + this.rota, 1.0);
                }

                return this.rotation;
            }
        });
        register(Items.COMPASS, ResourceLocation.withDefaultNamespace("angle"), new CompassItemPropertyFunction((level, stack, entity) -> {
            LodestoneTracker lodestoneTracker = stack.get(DataComponents.LODESTONE_TRACKER);
            return lodestoneTracker != null ? lodestoneTracker.target().orElse(null) : CompassItem.getSpawnPosition(level);
        }));
        register(
            Items.RECOVERY_COMPASS,
            ResourceLocation.withDefaultNamespace("angle"),
            new CompassItemPropertyFunction((level, stack, entity) -> entity instanceof Player player ? player.getLastDeathLocation().orElse(null) : null)
        );
        register(
            Items.CROSSBOW,
            ResourceLocation.withDefaultNamespace("pull"),
            (stack, level, entity, seed) -> {
                if (entity == null) {
                    return 0.0F;
                } else {
                    return CrossbowItem.isCharged(stack)
                        ? 0.0F
                        : (float)(stack.getUseDuration(entity) - entity.getUseItemRemainingTicks()) / CrossbowItem.getChargeDuration(stack, entity);
                }
            }
        );
        register(
            Items.CROSSBOW,
            ResourceLocation.withDefaultNamespace("pulling"),
            (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack && !CrossbowItem.isCharged(stack)
                ? 1.0F
                : 0.0F
        );
        register(Items.CROSSBOW, ResourceLocation.withDefaultNamespace("charged"), (stack, level, entity, seed) -> CrossbowItem.isCharged(stack) ? 1.0F : 0.0F);
        register(Items.CROSSBOW, ResourceLocation.withDefaultNamespace("firework"), (stack, level, entity, seed) -> {
            ChargedProjectiles chargedProjectiles = stack.get(DataComponents.CHARGED_PROJECTILES);
            return chargedProjectiles != null && chargedProjectiles.contains(Items.FIREWORK_ROCKET) ? 1.0F : 0.0F;
        });
        register(Items.ELYTRA, ResourceLocation.withDefaultNamespace("broken"), (stack, level, entity, seed) -> ElytraItem.isFlyEnabled(stack) ? 0.0F : 1.0F);
        register(Items.FISHING_ROD, ResourceLocation.withDefaultNamespace("cast"), (stack, level, entity, seed) -> {
            if (entity == null) {
                return 0.0F;
            }

            boolean bl = entity.getMainHandItem() == stack;
            boolean bl2 = entity.getOffhandItem() == stack;
            if (entity.getMainHandItem().getItem() instanceof FishingRodItem) {
                bl2 = false;
            }

            return (bl || bl2) && entity instanceof Player && ((Player)entity).fishing != null ? 1.0F : 0.0F;
        });
        register(
            Items.SHIELD,
            ResourceLocation.withDefaultNamespace("blocking"),
            (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F
        );
        register(
            Items.TRIDENT,
            ResourceLocation.withDefaultNamespace("throwing"),
            (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F
        );
        register(Items.LIGHT, ResourceLocation.withDefaultNamespace("level"), (stack, level, entity, seed) -> {
            BlockItemStateProperties blockItemStateProperties = stack.getOrDefault(DataComponents.BLOCK_STATE, BlockItemStateProperties.EMPTY);
            Integer integer = blockItemStateProperties.get(LightBlock.LEVEL);
            return integer != null ? integer.intValue() / 16.0F : 1.0F;
        });
        register(
            Items.GOAT_HORN,
            ResourceLocation.withDefaultNamespace("tooting"),
            (stack, level, entity, seed) -> entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F
        );
    }
}
