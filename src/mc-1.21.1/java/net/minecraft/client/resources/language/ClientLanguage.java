package net.minecraft.client.resources.language;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.FormattedCharSequence;
import org.slf4j.Logger;

public class ClientLanguage extends Language {
    private static final Logger LOGGER = LogUtils.getLogger();
    private final Map<String, String> storage;
    private final boolean defaultRightToLeft;

    private ClientLanguage(Map<String, String> storage, boolean defaultRightToLeft) {
        this.storage = storage;
        this.defaultRightToLeft = defaultRightToLeft;
    }

    public static ClientLanguage loadFrom(ResourceManager resourceManager, List<String> filenames, boolean defaultRightToLeft) {
        Map<String, String> map = Maps.newHashMap();

        for (String string : filenames) {
            String string2 = String.format(Locale.ROOT, "lang/%s.json", string);

            for (String string3 : resourceManager.getNamespaces()) {
                try {
                    ResourceLocation resourceLocation = ResourceLocation.fromNamespaceAndPath(string3, string2);
                    appendFrom(string, resourceManager.getResourceStack(resourceLocation), map);
                } catch (Exception exception) {
                    LOGGER.warn("Skipped language file: {}:{} ({})", string3, string2, exception.toString());
                }
            }
        }

        return new ClientLanguage(ImmutableMap.copyOf(map), defaultRightToLeft);
    }

    private static void appendFrom(String languageName, List<Resource> resources, Map<String, String> destinationMap) {
        for (Resource resource : resources) {
            try (InputStream inputStream = resource.open()) {
                Language.loadFromJson(inputStream, destinationMap::put);
            } catch (IOException iOException) {
                LOGGER.warn("Failed to load translations for {} from pack {}", languageName, resource.sourcePackId(), iOException);
            }
        }
    }

    @Override
    public String getOrDefault(String key, String defaultValue) {
        return this.storage.getOrDefault(key, defaultValue);
    }

    @Override
    public boolean has(String id) {
        return this.storage.containsKey(id);
    }

    @Override
    public boolean isDefaultRightToLeft() {
        return this.defaultRightToLeft;
    }

    @Override
    public FormattedCharSequence getVisualOrder(FormattedText text) {
        return FormattedBidiReorder.reorder(text, this.defaultRightToLeft);
    }
}
