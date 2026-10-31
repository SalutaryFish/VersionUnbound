package net.minecraft.commands.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import java.util.Arrays;
import java.util.Collection;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;

public class ResourceLocationArgument implements ArgumentType<ResourceLocation> {
    private static final Collection<String> EXAMPLES = Arrays.asList("foo", "foo:bar", "012");
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_ADVANCEMENT = new DynamicCommandExceptionType(
        advancement -> Component.translatableEscape("advancement.advancementNotFound", advancement)
    );
    private static final DynamicCommandExceptionType ERROR_UNKNOWN_RECIPE = new DynamicCommandExceptionType(
        recipe -> Component.translatableEscape("recipe.notFound", recipe)
    );

    public static ResourceLocationArgument id() {
        return new ResourceLocationArgument();
    }

    public static AdvancementHolder getAdvancement(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        ResourceLocation resourceLocation = getId(context, name);
        AdvancementHolder advancementHolder = context.getSource().getServer().getAdvancements().get(resourceLocation);
        if (advancementHolder == null) {
            throw ERROR_UNKNOWN_ADVANCEMENT.create(resourceLocation);
        } else {
            return advancementHolder;
        }
    }

    public static RecipeHolder<?> getRecipe(CommandContext<CommandSourceStack> context, String name) throws CommandSyntaxException {
        RecipeManager recipeManager = context.getSource().getServer().getRecipeManager();
        ResourceLocation resourceLocation = getId(context, name);
        return recipeManager.byKey(resourceLocation).orElseThrow(() -> ERROR_UNKNOWN_RECIPE.create(resourceLocation));
    }

    public static ResourceLocation getId(CommandContext<CommandSourceStack> context, String name) {
        return context.getArgument(name, ResourceLocation.class);
    }

    public ResourceLocation parse(StringReader reader) throws CommandSyntaxException {
        return ResourceLocation.read(reader);
    }

    @Override
    public Collection<String> getExamples() {
        return EXAMPLES;
    }
}
