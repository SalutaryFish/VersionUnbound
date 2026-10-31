package net.minecraft.client.resources.language;

import java.util.IllegalFormatException;
import net.minecraft.locale.Language;

public class I18n {
    private static volatile Language language = Language.getInstance();

    private I18n() {
    }

    static void setLanguage(Language language) {
        I18n.language = language;
    }

    /**
     * Translates the given string and then formats it. Equivalent to {@code String.format(translate(key), parameters)}.
     */
    public static String get(String translateKey, Object... parameters) {
        String string = language.getOrDefault(translateKey);

        try {
            return String.format(string, parameters);
        } catch (IllegalFormatException illegalFormatException) {
            return "Format error: " + string;
        }
    }

    public static boolean exists(String key) {
        return language.has(key);
    }
}
