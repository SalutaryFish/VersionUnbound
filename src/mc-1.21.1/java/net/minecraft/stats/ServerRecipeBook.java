package net.minecraft.stats;

import com.google.common.collect.Lists;
import com.mojang.logging.LogUtils;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import net.minecraft.ResourceLocationException;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.game.ClientboundRecipePacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import org.slf4j.Logger;

public class ServerRecipeBook extends RecipeBook {
    public static final String RECIPE_BOOK_TAG = "recipeBook";
    private static final Logger LOGGER = LogUtils.getLogger();

    public int addRecipes(Collection<RecipeHolder<?>> recipes, ServerPlayer player) {
        List<ResourceLocation> list = Lists.newArrayList();
        int i = 0;

        for (RecipeHolder<?> recipeHolder : recipes) {
            ResourceLocation resourceLocation = recipeHolder.id();
            if (!this.known.contains(resourceLocation) && !recipeHolder.value().isSpecial()) {
                this.add(resourceLocation);
                this.addHighlight(resourceLocation);
                list.add(resourceLocation);
                CriteriaTriggers.RECIPE_UNLOCKED.trigger(player, recipeHolder);
                i++;
            }
        }

        if (list.size() > 0) {
            this.sendRecipes(ClientboundRecipePacket.State.ADD, player, list);
        }

        return i;
    }

    public int removeRecipes(Collection<RecipeHolder<?>> recipes, ServerPlayer player) {
        List<ResourceLocation> list = Lists.newArrayList();
        int i = 0;

        for (RecipeHolder<?> recipeHolder : recipes) {
            ResourceLocation resourceLocation = recipeHolder.id();
            if (this.known.contains(resourceLocation)) {
                this.remove(resourceLocation);
                list.add(resourceLocation);
                i++;
            }
        }

        this.sendRecipes(ClientboundRecipePacket.State.REMOVE, player, list);
        return i;
    }

    private void sendRecipes(ClientboundRecipePacket.State state, ServerPlayer player, List<ResourceLocation> recipes) {
        player.connection.send(new ClientboundRecipePacket(state, recipes, Collections.emptyList(), this.getBookSettings()));
    }

    public CompoundTag toNbt() {
        CompoundTag compoundTag = new CompoundTag();
        this.getBookSettings().write(compoundTag);
        ListTag listTag = new ListTag();

        for (ResourceLocation resourceLocation : this.known) {
            listTag.add(StringTag.valueOf(resourceLocation.toString()));
        }

        compoundTag.put("recipes", listTag);
        ListTag listTag2 = new ListTag();

        for (ResourceLocation resourceLocation2 : this.highlight) {
            listTag2.add(StringTag.valueOf(resourceLocation2.toString()));
        }

        compoundTag.put("toBeDisplayed", listTag2);
        return compoundTag;
    }

    public void fromNbt(CompoundTag tag, RecipeManager recipeManager) {
        this.setBookSettings(RecipeBookSettings.read(tag));
        ListTag listTag = tag.getList("recipes", 8);
        this.loadRecipes(listTag, this::add, recipeManager);
        ListTag listTag2 = tag.getList("toBeDisplayed", 8);
        this.loadRecipes(listTag2, this::addHighlight, recipeManager);
    }

    private void loadRecipes(ListTag tags, Consumer<RecipeHolder<?>> recipeConsumer, RecipeManager recipeManager) {
        for (int i = 0; i < tags.size(); i++) {
            String string = tags.getString(i);

            try {
                ResourceLocation resourceLocation = ResourceLocation.parse(string);
                Optional<RecipeHolder<?>> optional = recipeManager.byKey(resourceLocation);
                if (optional.isEmpty()) {
                    LOGGER.error("Tried to load unrecognized recipe: {} removed now.", resourceLocation);
                } else {
                    recipeConsumer.accept(optional.get());
                }
            } catch (ResourceLocationException resourceLocationException) {
                LOGGER.error("Tried to load improperly formatted recipe: {} removed now.", string);
            }
        }
    }

    public void sendInitialRecipeBook(ServerPlayer player) {
        player.connection.send(new ClientboundRecipePacket(ClientboundRecipePacket.State.INIT, this.known, this.highlight, this.getBookSettings()));
    }
}
