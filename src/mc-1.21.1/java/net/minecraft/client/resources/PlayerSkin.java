package net.minecraft.client.resources;

import javax.annotation.Nullable;
import net.minecraft.resources.ResourceLocation;

public record PlayerSkin(
    ResourceLocation texture,
    @Nullable String textureUrl,
    @Nullable ResourceLocation capeTexture,
    @Nullable ResourceLocation elytraTexture,
    PlayerSkin.Model model,
    boolean secure
) {
    public enum Model {
        SLIM("slim"),
        WIDE("default");

        private final String id;

        Model(final String id) {
            this.id = id;
        }

        public static PlayerSkin.Model byName(@Nullable String name) {
            if (name == null) {
                return WIDE;
            }

            return switch (name) {
                case "slim" -> SLIM;
                default -> WIDE;
            };
        }

        public String id() {
            return this.id;
        }
    }
}
