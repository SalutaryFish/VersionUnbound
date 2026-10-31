package net.minecraft.client.resources.language;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.minecraft.client.resources.metadata.language.LanguageMetadataSection;
import net.minecraft.locale.Language;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import org.slf4j.Logger;

public class LanguageManager implements ResourceManagerReloadListener {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final LanguageInfo DEFAULT_LANGUAGE = new LanguageInfo("US", "English", false);
    private Map<String, LanguageInfo> languages = ImmutableMap.of("en_us", DEFAULT_LANGUAGE);
    private String currentCode;
    private final Consumer<ClientLanguage> reloadCallback;

    public LanguageManager(String currentCode, Consumer<ClientLanguage> reloadFallback) {
        this.currentCode = currentCode;
        this.reloadCallback = reloadFallback;
    }

    private static Map<String, LanguageInfo> extractLanguages(Stream<PackResources> packResources) {
        Map<String, LanguageInfo> map = Maps.newHashMap();
        packResources.forEach(resource -> {
            try {
                LanguageMetadataSection languageMetadataSection = resource.getMetadataSection(LanguageMetadataSection.TYPE);
                if (languageMetadataSection != null) {
                    languageMetadataSection.languages().forEach(map::putIfAbsent);
                }
            } catch (RuntimeException | IOException exception) {
                LOGGER.warn("Unable to parse language metadata section of resourcepack: {}", resource.packId(), exception);
            }
        });
        return ImmutableMap.copyOf(map);
    }

    @Override
    public void onResourceManagerReload(ResourceManager resourceManager) {
        this.languages = extractLanguages(resourceManager.listPacks());
        List<String> list = new ArrayList<>(2);
        boolean bl = DEFAULT_LANGUAGE.bidirectional();
        list.add("en_us");
        if (!this.currentCode.equals("en_us")) {
            LanguageInfo languageInfo = this.languages.get(this.currentCode);
            if (languageInfo != null) {
                list.add(this.currentCode);
                bl = languageInfo.bidirectional();
            }
        }

        ClientLanguage clientLanguage = ClientLanguage.loadFrom(resourceManager, list, bl);
        I18n.setLanguage(clientLanguage);
        Language.inject(clientLanguage);
        this.reloadCallback.accept(clientLanguage);
    }

    public void setSelected(String selected) {
        this.currentCode = selected;
    }

    public String getSelected() {
        return this.currentCode;
    }

    public SortedMap<String, LanguageInfo> getLanguages() {
        return new TreeMap<>(this.languages);
    }

    @Nullable
    public LanguageInfo getLanguage(String code) {
        return this.languages.get(code);
    }
}
