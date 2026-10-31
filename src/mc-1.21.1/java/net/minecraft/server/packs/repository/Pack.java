package net.minecraft.server.packs.repository;

import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FeatureFlagsMetadataSection;
import net.minecraft.server.packs.OverlayMetadataSection;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.util.InclusiveRange;
import net.minecraft.world.flag.FeatureFlagSet;
import org.slf4j.Logger;

public class Pack {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final PackLocationInfo location;
    private final Pack.ResourcesSupplier resources;
    private final Pack.Metadata metadata;
    private final PackSelectionConfig selectionConfig;

    @Nullable
    public static Pack readMetaAndCreate(PackLocationInfo location, Pack.ResourcesSupplier resources, PackType packType, PackSelectionConfig selectionConfig) {
        int i = SharedConstants.getCurrentVersion().getPackVersion(packType);
        Pack.Metadata metadata = readPackMetadata(location, resources, i);
        return metadata != null ? new Pack(location, resources, metadata, selectionConfig) : null;
    }

    public Pack(PackLocationInfo location, Pack.ResourcesSupplier resources, Pack.Metadata metadata, PackSelectionConfig selectionConfig) {
        this.location = location;
        this.resources = resources;
        this.metadata = metadata;
        this.selectionConfig = selectionConfig;
    }

    @Nullable
    public static Pack.Metadata readPackMetadata(PackLocationInfo location, Pack.ResourcesSupplier resources, int version) {
        try (PackResources packResources = resources.openPrimary(location)) {
            PackMetadataSection packMetadataSection = packResources.getMetadataSection(PackMetadataSection.TYPE);
            if (packMetadataSection == null) {
                LOGGER.warn("Missing metadata in pack {}", location.id());
                return null;
            } else {
                FeatureFlagsMetadataSection featureFlagsMetadataSection = packResources.getMetadataSection(FeatureFlagsMetadataSection.TYPE);
                FeatureFlagSet featureFlagSet = featureFlagsMetadataSection != null ? featureFlagsMetadataSection.flags() : FeatureFlagSet.of();
                InclusiveRange<Integer> inclusiveRange = getDeclaredPackVersions(location.id(), packMetadataSection);
                PackCompatibility packCompatibility = PackCompatibility.forVersion(inclusiveRange, version);
                OverlayMetadataSection overlayMetadataSection = packResources.getMetadataSection(OverlayMetadataSection.TYPE);
                List<String> list = overlayMetadataSection != null ? overlayMetadataSection.overlaysForVersion(version) : List.of();
                return new Pack.Metadata(packMetadataSection.description(), packCompatibility, featureFlagSet, list);
            }
        } catch (Exception exception) {
            LOGGER.warn("Failed to read pack {} metadata", location.id(), exception);
            return null;
        }
    }

    private static InclusiveRange<Integer> getDeclaredPackVersions(String id, PackMetadataSection metadata) {
        int i = metadata.packFormat();
        if (metadata.supportedFormats().isEmpty()) {
            return new InclusiveRange<>(i);
        } else {
            InclusiveRange<Integer> inclusiveRange = metadata.supportedFormats().get();
            if (!inclusiveRange.isValueInRange(i)) {
                LOGGER.warn("Pack {} declared support for versions {} but declared main format is {}, defaulting to {}", id, inclusiveRange, i, i);
                return new InclusiveRange<>(i);
            } else {
                return inclusiveRange;
            }
        }
    }

    public PackLocationInfo location() {
        return this.location;
    }

    public Component getTitle() {
        return this.location.title();
    }

    public Component getDescription() {
        return this.metadata.description();
    }

    /**
     * @param green used to indicate either a successful operation or datapack enabled
     *              status
     */
    public Component getChatLink(boolean green) {
        return this.location.createChatLink(green, this.metadata.description);
    }

    public PackCompatibility getCompatibility() {
        return this.metadata.compatibility();
    }

    public FeatureFlagSet getRequestedFeatures() {
        return this.metadata.requestedFeatures();
    }

    public PackResources open() {
        return this.resources.openFull(this.location, this.metadata);
    }

    public String getId() {
        return this.location.id();
    }

    public PackSelectionConfig selectionConfig() {
        return this.selectionConfig;
    }

    public boolean isRequired() {
        return this.selectionConfig.required();
    }

    public boolean isFixedPosition() {
        return this.selectionConfig.fixedPosition();
    }

    public Pack.Position getDefaultPosition() {
        return this.selectionConfig.defaultPosition();
    }

    public PackSource getPackSource() {
        return this.location.source();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            return !(other instanceof Pack pack) ? false : this.location.equals(pack.location);
        }
    }

    @Override
    public int hashCode() {
        return this.location.hashCode();
    }

    public record Metadata(Component description, PackCompatibility compatibility, FeatureFlagSet requestedFeatures, List<String> overlays) {
    }

    public enum Position {
        TOP,
        BOTTOM;

        public <T> int insert(List<T> list, T element, Function<T, PackSelectionConfig> packFactory, boolean flipPosition) {
            Pack.Position position = flipPosition ? this.opposite() : this;
            if (position == BOTTOM) {
                int i;
                for (i = 0; i < list.size(); i++) {
                    PackSelectionConfig packSelectionConfig = packFactory.apply(list.get(i));
                    if (!packSelectionConfig.fixedPosition() || packSelectionConfig.defaultPosition() != this) {
                        break;
                    }
                }

                list.add(i, element);
                return i;
            } else {
                int i;
                for (i = list.size() - 1; i >= 0; i--) {
                    PackSelectionConfig packSelectionConfig = packFactory.apply(list.get(i));
                    if (!packSelectionConfig.fixedPosition() || packSelectionConfig.defaultPosition() != this) {
                        break;
                    }
                }

                list.add(i + 1, element);
                return i + 1;
            }
        }

        public Pack.Position opposite() {
            return this == TOP ? BOTTOM : TOP;
        }
    }

    public interface ResourcesSupplier {
        PackResources openPrimary(PackLocationInfo var1);

        PackResources openFull(PackLocationInfo var1, Pack.Metadata var2);
    }
}
