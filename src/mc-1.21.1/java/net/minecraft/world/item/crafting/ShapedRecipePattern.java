package net.minecraft.world.item.crafting;

import com.google.common.annotations.VisibleForTesting;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.chars.CharArraySet;
import it.unimi.dsi.fastutil.chars.CharSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.core.NonNullList;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

public final class ShapedRecipePattern {
    private static final int MAX_SIZE = 3;
    public static final MapCodec<ShapedRecipePattern> MAP_CODEC = ShapedRecipePattern.Data.MAP_CODEC
        .flatXmap(
            ShapedRecipePattern::unpack,
            pattern -> pattern.data.map(DataResult::success).orElseGet(() -> DataResult.error(() -> "Cannot encode unpacked recipe"))
        );
    public static final StreamCodec<RegistryFriendlyByteBuf, ShapedRecipePattern> STREAM_CODEC = StreamCodec.ofMember(
        ShapedRecipePattern::toNetwork, ShapedRecipePattern::fromNetwork
    );
    private final int width;
    private final int height;
    private final NonNullList<Ingredient> ingredients;
    private final Optional<ShapedRecipePattern.Data> data;
    private final int ingredientCount;
    private final boolean symmetrical;

    public ShapedRecipePattern(int width, int height, NonNullList<Ingredient> ingredients, Optional<ShapedRecipePattern.Data> data) {
        this.width = width;
        this.height = height;
        this.ingredients = ingredients;
        this.data = data;
        int i = 0;

        for (Ingredient ingredient : ingredients) {
            if (!ingredient.isEmpty()) {
                i++;
            }
        }

        this.ingredientCount = i;
        this.symmetrical = Util.isSymmetrical(width, height, ingredients);
    }

    public static ShapedRecipePattern of(Map<Character, Ingredient> key, String... pattern) {
        return of(key, List.of(pattern));
    }

    public static ShapedRecipePattern of(Map<Character, Ingredient> key, List<String> pattern) {
        ShapedRecipePattern.Data data = new ShapedRecipePattern.Data(key, pattern);
        return unpack(data).getOrThrow();
    }

    private static DataResult<ShapedRecipePattern> unpack(ShapedRecipePattern.Data data) {
        String[] strings = shrink(data.pattern);
        int i = strings[0].length();
        int j = strings.length;
        NonNullList<Ingredient> nonNullList = NonNullList.withSize(i * j, Ingredient.EMPTY);
        CharSet charSet = new CharArraySet(data.key.keySet());

        for (int k = 0; k < strings.length; k++) {
            String string = strings[k];

            for (int l = 0; l < string.length(); l++) {
                char c = string.charAt(l);
                Ingredient ingredient = c == ' ' ? Ingredient.EMPTY : data.key.get(c);
                if (ingredient == null) {
                    return DataResult.error(() -> "Pattern references symbol '" + c + "' but it's not defined in the key");
                }

                charSet.remove(c);
                nonNullList.set(l + i * k, ingredient);
            }
        }

        return !charSet.isEmpty()
            ? DataResult.error(() -> "Key defines symbols that aren't used in pattern: " + charSet)
            : DataResult.success(new ShapedRecipePattern(i, j, nonNullList, Optional.of(data)));
    }

    @VisibleForTesting
    static String[] shrink(List<String> pattern) {
        int i = Integer.MAX_VALUE;
        int j = 0;
        int k = 0;
        int l = 0;

        for (int m = 0; m < pattern.size(); m++) {
            String string = pattern.get(m);
            i = Math.min(i, firstNonSpace(string));
            int n = lastNonSpace(string);
            j = Math.max(j, n);
            if (n < 0) {
                if (k == m) {
                    k++;
                }

                l++;
            } else {
                l = 0;
            }
        }

        if (pattern.size() == l) {
            return new String[0];
        }

        String[] strings = new String[pattern.size() - l - k];

        for (int o = 0; o < strings.length; o++) {
            strings[o] = pattern.get(o + k).substring(i, j + 1);
        }

        return strings;
    }

    private static int firstNonSpace(String row) {
        int i = 0;

        while (i < row.length() && row.charAt(i) == ' ') {
            i++;
        }

        return i;
    }

    private static int lastNonSpace(String row) {
        int i = row.length() - 1;

        while (i >= 0 && row.charAt(i) == ' ') {
            i--;
        }

        return i;
    }

    public boolean matches(CraftingInput input) {
        if (input.ingredientCount() != this.ingredientCount) {
            return false;
        }

        if (input.width() == this.width && input.height() == this.height) {
            if (!this.symmetrical && this.matches(input, true)) {
                return true;
            }

            if (this.matches(input, false)) {
                return true;
            }
        }

        return false;
    }

    private boolean matches(CraftingInput input, boolean symmetrical) {
        for (int i = 0; i < this.height; i++) {
            for (int j = 0; j < this.width; j++) {
                Ingredient ingredient;
                if (symmetrical) {
                    ingredient = this.ingredients.get(this.width - j - 1 + i * this.width);
                } else {
                    ingredient = this.ingredients.get(j + i * this.width);
                }

                ItemStack itemStack = input.getItem(j, i);
                if (!ingredient.test(itemStack)) {
                    return false;
                }
            }
        }

        return true;
    }

    private void toNetwork(RegistryFriendlyByteBuf buffer) {
        buffer.writeVarInt(this.width);
        buffer.writeVarInt(this.height);

        for (Ingredient ingredient : this.ingredients) {
            Ingredient.CONTENTS_STREAM_CODEC.encode(buffer, ingredient);
        }
    }

    private static ShapedRecipePattern fromNetwork(RegistryFriendlyByteBuf buffer) {
        int i = buffer.readVarInt();
        int j = buffer.readVarInt();
        NonNullList<Ingredient> nonNullList = NonNullList.withSize(i * j, Ingredient.EMPTY);
        nonNullList.replaceAll(ingredient -> Ingredient.CONTENTS_STREAM_CODEC.decode(buffer));
        return new ShapedRecipePattern(i, j, nonNullList, Optional.empty());
    }

    public int width() {
        return this.width;
    }

    public int height() {
        return this.height;
    }

    public NonNullList<Ingredient> ingredients() {
        return this.ingredients;
    }

    public record Data(Map<Character, Ingredient> key, List<String> pattern) {
        private static final Codec<List<String>> PATTERN_CODEC = Codec.STRING.listOf().comapFlatMap(patternEntry -> {
            if (patternEntry.size() > 3) {
                return DataResult.error(() -> "Invalid pattern: too many rows, 3 is maximum");
            }

            if (patternEntry.isEmpty()) {
                return DataResult.error(() -> "Invalid pattern: empty pattern not allowed");
            }

            int i = patternEntry.getFirst().length();

            for (String string : patternEntry) {
                if (string.length() > 3) {
                    return DataResult.error(() -> "Invalid pattern: too many columns, 3 is maximum");
                }

                if (i != string.length()) {
                    return DataResult.error(() -> "Invalid pattern: each row must be the same width");
                }
            }

            return DataResult.success(patternEntry);
        }, Function.identity());
        private static final Codec<Character> SYMBOL_CODEC = Codec.STRING.comapFlatMap(symbol -> {
            if (symbol.length() != 1) {
                return DataResult.error(() -> "Invalid key entry: '" + symbol + "' is an invalid symbol (must be 1 character only).");
            } else {
                return " ".equals(symbol) ? DataResult.error(() -> "Invalid key entry: ' ' is a reserved symbol.") : DataResult.success(symbol.charAt(0));
            }
        }, String::valueOf);
        public static final MapCodec<ShapedRecipePattern.Data> MAP_CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    ExtraCodecs.strictUnboundedMap(SYMBOL_CODEC, Ingredient.CODEC_NONEMPTY).fieldOf("key").forGetter(data -> data.key),
                    PATTERN_CODEC.fieldOf("pattern").forGetter(data -> data.pattern)
                )
                .apply(instance, ShapedRecipePattern.Data::new)
        );
    }
}
