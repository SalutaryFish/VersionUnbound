package net.minecraft.util.datafix.fixes;

import com.mojang.datafixers.DSL;
import com.mojang.datafixers.DataFix;
import com.mojang.datafixers.DataFixUtils;
import com.mojang.datafixers.OpticFinder;
import com.mojang.datafixers.TypeRewriteRule;
import com.mojang.datafixers.Typed;
import com.mojang.datafixers.schemas.Schema;
import com.mojang.datafixers.types.Type;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Dynamic;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.util.datafix.schemas.NamespacedSchema;

public class ItemSpawnEggFix extends DataFix {
    private static final String[] ID_TO_ENTITY = DataFixUtils.make(new String[256], array -> {
        array[1] = "Item";
        array[2] = "XPOrb";
        array[7] = "ThrownEgg";
        array[8] = "LeashKnot";
        array[9] = "Painting";
        array[10] = "Arrow";
        array[11] = "Snowball";
        array[12] = "Fireball";
        array[13] = "SmallFireball";
        array[14] = "ThrownEnderpearl";
        array[15] = "EyeOfEnderSignal";
        array[16] = "ThrownPotion";
        array[17] = "ThrownExpBottle";
        array[18] = "ItemFrame";
        array[19] = "WitherSkull";
        array[20] = "PrimedTnt";
        array[21] = "FallingSand";
        array[22] = "FireworksRocketEntity";
        array[23] = "TippedArrow";
        array[24] = "SpectralArrow";
        array[25] = "ShulkerBullet";
        array[26] = "DragonFireball";
        array[30] = "ArmorStand";
        array[41] = "Boat";
        array[42] = "MinecartRideable";
        array[43] = "MinecartChest";
        array[44] = "MinecartFurnace";
        array[45] = "MinecartTNT";
        array[46] = "MinecartHopper";
        array[47] = "MinecartSpawner";
        array[40] = "MinecartCommandBlock";
        array[50] = "Creeper";
        array[51] = "Skeleton";
        array[52] = "Spider";
        array[53] = "Giant";
        array[54] = "Zombie";
        array[55] = "Slime";
        array[56] = "Ghast";
        array[57] = "PigZombie";
        array[58] = "Enderman";
        array[59] = "CaveSpider";
        array[60] = "Silverfish";
        array[61] = "Blaze";
        array[62] = "LavaSlime";
        array[63] = "EnderDragon";
        array[64] = "WitherBoss";
        array[65] = "Bat";
        array[66] = "Witch";
        array[67] = "Endermite";
        array[68] = "Guardian";
        array[69] = "Shulker";
        array[90] = "Pig";
        array[91] = "Sheep";
        array[92] = "Cow";
        array[93] = "Chicken";
        array[94] = "Squid";
        array[95] = "Wolf";
        array[96] = "MushroomCow";
        array[97] = "SnowMan";
        array[98] = "Ozelot";
        array[99] = "VillagerGolem";
        array[100] = "EntityHorse";
        array[101] = "Rabbit";
        array[120] = "Villager";
        array[200] = "EnderCrystal";
    });

    public ItemSpawnEggFix(Schema outputSchema, boolean changesType) {
        super(outputSchema, changesType);
    }

    @Override
    public TypeRewriteRule makeRule() {
        Schema schema = this.getInputSchema();
        Type<?> type = schema.getType(References.ITEM_STACK);
        OpticFinder<Pair<String, String>> opticFinder = DSL.fieldFinder("id", DSL.named(References.ITEM_NAME.typeName(), NamespacedSchema.namespacedString()));
        OpticFinder<String> opticFinder2 = DSL.fieldFinder("id", DSL.string());
        OpticFinder<?> opticFinder3 = type.findField("tag");
        OpticFinder<?> opticFinder4 = opticFinder3.type().findField("EntityTag");
        OpticFinder<?> opticFinder5 = DSL.typeFinder(schema.getTypeRaw(References.ENTITY));
        Type<?> type2 = this.getOutputSchema().getTypeRaw(References.ENTITY);
        return this.fixTypeEverywhereTyped("ItemSpawnEggFix", type, typed -> {
            Optional<Pair<String, String>> optional = typed.getOptional(opticFinder);
            if (optional.isPresent() && Objects.equals(optional.get().getSecond(), "minecraft:spawn_egg")) {
                Dynamic<?> dynamic = typed.get(DSL.remainderFinder());
                short s = dynamic.get("Damage").asShort((short)0);
                Optional<? extends Typed<?>> optional2 = typed.getOptionalTyped(opticFinder3);
                Optional<? extends Typed<?>> optional3 = optional2.flatMap(typedx -> typedx.getOptionalTyped(opticFinder4));
                Optional<? extends Typed<?>> optional4 = optional3.flatMap(typedx -> typedx.getOptionalTyped(opticFinder5));
                Optional<String> optional5 = optional4.flatMap(typedx -> typedx.getOptional(opticFinder2));
                Typed<?> typed2 = typed;
                String string = ID_TO_ENTITY[s & 0xFF];
                if (string != null && (optional5.isEmpty() || !Objects.equals(optional5.get(), string))) {
                    Typed<?> typed3 = typed.getOrCreateTyped(opticFinder3);
                    Typed<?> typed4 = typed3.getOrCreateTyped(opticFinder4);
                    Typed<?> typed5 = typed4.getOrCreateTyped(opticFinder5);
                    Dynamic<?> dynamic2 = dynamic;
                    Typed<?> typed6 = Util.writeAndReadTypedOrThrow(typed5, type2, dynamic2x -> dynamic2x.set("id", dynamic2.createString(string)));
                    typed2 = typed2.set(opticFinder3, typed3.set(opticFinder4, typed4.set(opticFinder5, typed6)));
                }

                if (s != 0) {
                    dynamic = dynamic.set("Damage", dynamic.createShort((short)0));
                    typed2 = typed2.set(DSL.remainderFinder(), dynamic);
                }

                return typed2;
            } else {
                return typed;
            }
        });
    }
}
