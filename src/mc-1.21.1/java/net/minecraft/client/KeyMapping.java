package net.minecraft.client;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.mojang.blaze3d.platform.InputConstants;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import net.minecraft.Util;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

public class KeyMapping implements Comparable<KeyMapping> {
    private static final Map<String, KeyMapping> ALL = Maps.newHashMap();
    private static final Map<InputConstants.Key, KeyMapping> MAP = Maps.newHashMap();
    private static final Set<String> CATEGORIES = Sets.newHashSet();
    public static final String CATEGORY_MOVEMENT = "key.categories.movement";
    public static final String CATEGORY_MISC = "key.categories.misc";
    public static final String CATEGORY_MULTIPLAYER = "key.categories.multiplayer";
    public static final String CATEGORY_GAMEPLAY = "key.categories.gameplay";
    public static final String CATEGORY_INVENTORY = "key.categories.inventory";
    public static final String CATEGORY_INTERFACE = "key.categories.ui";
    public static final String CATEGORY_CREATIVE = "key.categories.creative";
    private static final Map<String, Integer> CATEGORY_SORT_ORDER = Util.make(Maps.newHashMap(), map -> {
        map.put("key.categories.movement", 1);
        map.put("key.categories.gameplay", 2);
        map.put("key.categories.inventory", 3);
        map.put("key.categories.creative", 4);
        map.put("key.categories.multiplayer", 5);
        map.put("key.categories.ui", 6);
        map.put("key.categories.misc", 7);
    });
    private final String name;
    private final InputConstants.Key defaultKey;
    private final String category;
    private InputConstants.Key key;
    private boolean isDown;
    private int clickCount;

    public static void click(InputConstants.Key key) {
        KeyMapping keyMapping = MAP.get(key);
        if (keyMapping != null) {
            keyMapping.clickCount++;
        }
    }

    public static void set(InputConstants.Key key, boolean held) {
        KeyMapping keyMapping = MAP.get(key);
        if (keyMapping != null) {
            keyMapping.setDown(held);
        }
    }

    public static void setAll() {
        for (KeyMapping keyMapping : ALL.values()) {
            if (keyMapping.key.getType() == InputConstants.Type.KEYSYM && keyMapping.key.getValue() != InputConstants.UNKNOWN.getValue()) {
                keyMapping.setDown(InputConstants.isKeyDown(Minecraft.getInstance().getWindow().getWindow(), keyMapping.key.getValue()));
            }
        }
    }

    public static void releaseAll() {
        for (KeyMapping keyMapping : ALL.values()) {
            keyMapping.release();
        }
    }

    public static void resetToggleKeys() {
        for (KeyMapping keyMapping : ALL.values()) {
            if (keyMapping instanceof ToggleKeyMapping toggleKeyMapping) {
                toggleKeyMapping.reset();
            }
        }
    }

    public static void resetMapping() {
        MAP.clear();

        for (KeyMapping keyMapping : ALL.values()) {
            MAP.put(keyMapping.key, keyMapping);
        }
    }

    public KeyMapping(String name, int keyCode, String category) {
        this(name, InputConstants.Type.KEYSYM, keyCode, category);
    }

    public KeyMapping(String name, InputConstants.Type type, int keyCode, String category) {
        this.name = name;
        this.key = type.getOrCreate(keyCode);
        this.defaultKey = this.key;
        this.category = category;
        ALL.put(name, this);
        MAP.put(this.key, this);
        CATEGORIES.add(category);
    }

    public boolean isDown() {
        return this.isDown;
    }

    public String getCategory() {
        return this.category;
    }

    public boolean consumeClick() {
        if (this.clickCount == 0) {
            return false;
        }

        this.clickCount--;
        return true;
    }

    private void release() {
        this.clickCount = 0;
        this.setDown(false);
    }

    public String getName() {
        return this.name;
    }

    public InputConstants.Key getDefaultKey() {
        return this.defaultKey;
    }

    /**
     * Binds a new KeyCode to this
     */
    public void setKey(InputConstants.Key key) {
        this.key = key;
    }

    public int compareTo(KeyMapping keyMapping) {
        return this.category.equals(keyMapping.category)
            ? I18n.get(this.name).compareTo(I18n.get(keyMapping.name))
            : CATEGORY_SORT_ORDER.get(this.category).compareTo(CATEGORY_SORT_ORDER.get(keyMapping.category));
    }

    /**
     * Returns a supplier which gets a keybind's current binding (eg, <code>key.forward</code> returns <samp>W</samp> by default), or the keybind's name if no such keybind exists (eg, <code>key.invalid</code> returns <samp>key.invalid</samp>)
     */
    public static Supplier<Component> createNameSupplier(String key) {
        KeyMapping keyMapping = ALL.get(key);
        return keyMapping == null ? () -> Component.translatable(key) : keyMapping::getTranslatedKeyMessage;
    }

    /**
     * Returns {@code true} if the supplied {@code KeyMapping} conflicts with this
     */
    public boolean same(KeyMapping binding) {
        return this.key.equals(binding.key);
    }

    public boolean isUnbound() {
        return this.key.equals(InputConstants.UNKNOWN);
    }

    public boolean matches(int keysym, int scancode) {
        return keysym == InputConstants.UNKNOWN.getValue()
            ? this.key.getType() == InputConstants.Type.SCANCODE && this.key.getValue() == scancode
            : this.key.getType() == InputConstants.Type.KEYSYM && this.key.getValue() == keysym;
    }

    /**
     * Returns {@code true} if the {@code KeyMapping} is set to a mouse key and the key matches.
     */
    public boolean matchesMouse(int key) {
        return this.key.getType() == InputConstants.Type.MOUSE && this.key.getValue() == key;
    }

    public Component getTranslatedKeyMessage() {
        return this.key.getDisplayName();
    }

    public boolean isDefault() {
        return this.key.equals(this.defaultKey);
    }

    public String saveString() {
        return this.key.getName();
    }

    public void setDown(boolean value) {
        this.isDown = value;
    }
}
