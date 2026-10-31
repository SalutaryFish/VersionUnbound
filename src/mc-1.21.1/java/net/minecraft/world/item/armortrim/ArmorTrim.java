package net.minecraft.world.item.armortrim;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipProvider;

public class ArmorTrim implements TooltipProvider {
    public static final Codec<ArmorTrim> CODEC = RecordCodecBuilder.create(
        instance -> instance.group(
                TrimMaterial.CODEC.fieldOf("material").forGetter(ArmorTrim::material),
                TrimPattern.CODEC.fieldOf("pattern").forGetter(ArmorTrim::pattern),
                Codec.BOOL.optionalFieldOf("show_in_tooltip", true).forGetter(armorTrim -> armorTrim.showInTooltip)
            )
            .apply(instance, ArmorTrim::new)
    );
    public static final StreamCodec<RegistryFriendlyByteBuf, ArmorTrim> STREAM_CODEC = StreamCodec.composite(
        TrimMaterial.STREAM_CODEC,
        ArmorTrim::material,
        TrimPattern.STREAM_CODEC,
        ArmorTrim::pattern,
        ByteBufCodecs.BOOL,
        armorTrim -> armorTrim.showInTooltip,
        ArmorTrim::new
    );
    private static final Component UPGRADE_TITLE = Component.translatable(
            Util.makeDescriptionId("item", ResourceLocation.withDefaultNamespace("smithing_template.upgrade"))
        )
        .withStyle(ChatFormatting.GRAY);
    private final Holder<TrimMaterial> material;
    private final Holder<TrimPattern> pattern;
    private final boolean showInTooltip;
    private final Function<Holder<ArmorMaterial>, ResourceLocation> innerTexture;
    private final Function<Holder<ArmorMaterial>, ResourceLocation> outerTexture;

    private ArmorTrim(
        Holder<TrimMaterial> material,
        Holder<TrimPattern> pattern,
        boolean showInTooltip,
        Function<Holder<ArmorMaterial>, ResourceLocation> innerTexture,
        Function<Holder<ArmorMaterial>, ResourceLocation> outerTexture
    ) {
        this.material = material;
        this.pattern = pattern;
        this.showInTooltip = showInTooltip;
        this.innerTexture = innerTexture;
        this.outerTexture = outerTexture;
    }

    public ArmorTrim(Holder<TrimMaterial> material, Holder<TrimPattern> pattern, boolean showInTooltip) {
        this.material = material;
        this.pattern = pattern;
        this.innerTexture = Util.memoize(holder3 -> {
            ResourceLocation resourceLocation = pattern.value().assetId();
            String string = getColorPaletteSuffix(material, holder3);
            return resourceLocation.withPath(string2 -> "trims/models/armor/" + string2 + "_leggings_" + string);
        });
        this.outerTexture = Util.memoize(holder3 -> {
            ResourceLocation resourceLocation = pattern.value().assetId();
            String string = getColorPaletteSuffix(material, holder3);
            return resourceLocation.withPath(string2 -> "trims/models/armor/" + string2 + "_" + string);
        });
        this.showInTooltip = showInTooltip;
    }

    public ArmorTrim(Holder<TrimMaterial> material, Holder<TrimPattern> pattern) {
        this(material, pattern, true);
    }

    private static String getColorPaletteSuffix(Holder<TrimMaterial> trimMaterial, Holder<ArmorMaterial> armorMaterial) {
        Map<Holder<ArmorMaterial>, String> map = trimMaterial.value().overrideArmorMaterials();
        String string = map.get(armorMaterial);
        return string != null ? string : trimMaterial.value().assetName();
    }

    public boolean hasPatternAndMaterial(Holder<TrimPattern> pattern, Holder<TrimMaterial> material) {
        return pattern.equals(this.pattern) && material.equals(this.material);
    }

    public Holder<TrimPattern> pattern() {
        return this.pattern;
    }

    public Holder<TrimMaterial> material() {
        return this.material;
    }

    public ResourceLocation innerTexture(Holder<ArmorMaterial> armorMaterial) {
        return this.innerTexture.apply(armorMaterial);
    }

    public ResourceLocation outerTexture(Holder<ArmorMaterial> armorMaterial) {
        return this.outerTexture.apply(armorMaterial);
    }

    @Override
    public boolean equals(Object other) {
        return !(other instanceof ArmorTrim armorTrim)
            ? false
            : this.showInTooltip == armorTrim.showInTooltip && this.pattern.equals(armorTrim.pattern) && this.material.equals(armorTrim.material);
    }

    @Override
    public int hashCode() {
        int i = this.material.hashCode();
        i = 31 * i + this.pattern.hashCode();
        return 31 * i + (this.showInTooltip ? 1 : 0);
    }

    @Override
    public void addToTooltip(Item.TooltipContext context, Consumer<Component> tooltipAdder, TooltipFlag tooltipFlag) {
        if (this.showInTooltip) {
            tooltipAdder.accept(UPGRADE_TITLE);
            tooltipAdder.accept(CommonComponents.space().append(this.pattern.value().copyWithStyle(this.material)));
            tooltipAdder.accept(CommonComponents.space().append(this.material.value().description()));
        }
    }

    public ArmorTrim withTooltip(boolean showInTooltip) {
        return new ArmorTrim(this.material, this.pattern, showInTooltip, this.innerTexture, this.outerTexture);
    }
}
