package net.minecraft.world.inventory;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import it.unimi.dsi.fastutil.ints.IntLists;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;

public class SlotRanges {
    private static final List<SlotRange> SLOTS = Util.make(new ArrayList<>(), list -> {
        addSingleSlot(list, "contents", 0);
        addSlotRange(list, "container.", 0, 54);
        addSlotRange(list, "hotbar.", 0, 9);
        addSlotRange(list, "inventory.", 9, 27);
        addSlotRange(list, "enderchest.", 200, 27);
        addSlotRange(list, "villager.", 300, 8);
        addSlotRange(list, "horse.", 500, 15);
        int i = EquipmentSlot.MAINHAND.getIndex(98);
        int j = EquipmentSlot.OFFHAND.getIndex(98);
        addSingleSlot(list, "weapon", i);
        addSingleSlot(list, "weapon.mainhand", i);
        addSingleSlot(list, "weapon.offhand", j);
        addSlots(list, "weapon.*", i, j);
        i = EquipmentSlot.HEAD.getIndex(100);
        j = EquipmentSlot.CHEST.getIndex(100);
        int k = EquipmentSlot.LEGS.getIndex(100);
        int l = EquipmentSlot.FEET.getIndex(100);
        int m = EquipmentSlot.BODY.getIndex(105);
        addSingleSlot(list, "armor.head", i);
        addSingleSlot(list, "armor.chest", j);
        addSingleSlot(list, "armor.legs", k);
        addSingleSlot(list, "armor.feet", l);
        addSingleSlot(list, "armor.body", m);
        addSlots(list, "armor.*", i, j, k, l, m);
        addSingleSlot(list, "horse.saddle", 400);
        addSingleSlot(list, "horse.chest", 499);
        addSingleSlot(list, "player.cursor", 499);
        addSlotRange(list, "player.crafting.", 500, 4);
    });
    public static final Codec<SlotRange> CODEC = StringRepresentable.fromValues(() -> SLOTS.toArray(new SlotRange[0]));
    private static final Function<String, SlotRange> NAME_LOOKUP = StringRepresentable.createNameLookup(SLOTS.toArray(new SlotRange[0]), string -> string);

    private static SlotRange create(String name, int value) {
        return SlotRange.of(name, IntLists.singleton(value));
    }

    private static SlotRange create(String name, IntList values) {
        return SlotRange.of(name, IntLists.unmodifiable(values));
    }

    private static SlotRange create(String name, int... values) {
        return SlotRange.of(name, IntList.of(values));
    }

    private static void addSingleSlot(List<SlotRange> list, String name, int value) {
        list.add(create(name, value));
    }

    private static void addSlotRange(List<SlotRange> list, String prefix, int startValue, int size) {
        IntList intList = new IntArrayList(size);

        for (int i = 0; i < size; i++) {
            int j = startValue + i;
            list.add(create(prefix + i, j));
            intList.add(j);
        }

        list.add(create(prefix + "*", intList));
    }

    private static void addSlots(List<SlotRange> list, String name, int... values) {
        list.add(create(name, values));
    }

    @Nullable
    public static SlotRange nameToIds(String name) {
        return NAME_LOOKUP.apply(name);
    }

    public static Stream<String> allNames() {
        return SLOTS.stream().map(StringRepresentable::getSerializedName);
    }

    public static Stream<String> singleSlotNames() {
        return SLOTS.stream().filter(slotRange -> slotRange.size() == 1).map(StringRepresentable::getSerializedName);
    }
}
