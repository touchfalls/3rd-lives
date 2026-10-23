// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.lifesmp.core.config.LifeConfig;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * Adapter parsing config.json for the Fabric server environment into typed {@link LifeConfig}.
 */
public final class FabricConfigAdapter {

    private FabricConfigAdapter() {}

    /**
     * Loads {@link LifeConfig} from a given JSON file path.
     * If the file does not exist, copies the default bundled config.json first.
     *
     * @param configPath path to config.json
     * @return loaded LifeConfig
     */
    @NotNull
    public static LifeConfig loadOrCreate(@NotNull Path configPath) {
        try {
            if (!Files.exists(configPath)) {
                if (configPath.getParent() != null) {
                    Files.createDirectories(configPath.getParent());
                }
                try (InputStream in = FabricConfigAdapter.class.getClassLoader().getResourceAsStream("config.json")) {
                    if (in != null) {
                        Files.copy(in, configPath);
                    }
                }
            }

            try (Reader reader = Files.newBufferedReader(configPath, StandardCharsets.UTF_8)) {
                return parse(reader);
            }
        } catch (Exception e) {
            return LifeConfig.createDefault();
        }
    }

    /**
     * Parses {@link LifeConfig} from a JSON reader.
     *
     * @param reader json reader
     * @return LifeConfig instance
     */
    @NotNull
    public static LifeConfig parse(@NotNull Reader reader) {
        JsonObject root = JsonParser.parseReader(reader).getAsJsonObject();
        LifeConfig defaults = LifeConfig.createDefault(getString(root, LifeConfig.KEY_LANGUAGE, null));

        String prefix = getString(root, LifeConfig.KEY_PREFIX, defaults.getPrefix());
        int maxLives = getInt(root, LifeConfig.KEY_MAX_LIVES, defaults.getMaxLives());
        double banHours = getDouble(root, LifeConfig.KEY_BAN_HOURS, defaults.getBanHours());
        int restoredLivesAfterBan = getInt(root, LifeConfig.KEY_RESTORED_LIVES_AFTER_BAN, defaults.getRestoredLivesAfterBan());
        double regenHours = getDouble(root, LifeConfig.KEY_REGEN_HOURS_PER_LIFE, defaults.getRegenHoursPerLife());
        double giftRegenHours = getDouble(root, LifeConfig.KEY_GIFT_REGEN_HOURS_PER_LIFE, defaults.getGiftRegenHoursPerLife());
        double afkMinutes = getDouble(root, LifeConfig.KEY_AFK_MINUTES, defaults.getAfkMinutes());
        long retaliationWindow = getLong(root, LifeConfig.KEY_RETALIATION_WINDOW_SECONDS, defaults.getRetaliationWindowSeconds());
        long giftTimeout = getLong(root, LifeConfig.KEY_GIFT_REQUEST_TIMEOUT_SECONDS, defaults.getGiftRequestTimeoutSeconds());
        double autosaveMinutes = getDouble(root, LifeConfig.KEY_AUTOSAVE_MINUTES, defaults.getAutosaveMinutes());

        boolean deadTabEnabled = defaults.isDeadTabEnabled();
        String deadTabFormat = defaults.getDeadTabFormat();
        String deadTabColor = defaults.getDeadTabColor();
        boolean deadTabShowTime = defaults.isDeadTabShowTime();
        int deadTabMaxEntries = defaults.getDeadTabMaxEntries();
        boolean deadTabSortLast = defaults.isDeadTabSortLast();

        if (root.has("dead-tab") && root.get("dead-tab").isJsonObject()) {
            JsonObject dt = root.getAsJsonObject("dead-tab");
            if (dt.has("enabled") && !dt.get("enabled").isJsonNull()) deadTabEnabled = dt.get("enabled").getAsBoolean();
            if (dt.has("format") && !dt.get("format").isJsonNull()) deadTabFormat = dt.get("format").getAsString();
            if (dt.has("color") && !dt.get("color").isJsonNull()) deadTabColor = dt.get("color").getAsString();
            if (dt.has("show-time") && !dt.get("show-time").isJsonNull()) deadTabShowTime = dt.get("show-time").getAsBoolean();
            if (dt.has("max-entries") && !dt.get("max-entries").isJsonNull()) deadTabMaxEntries = dt.get("max-entries").getAsInt();
            if (dt.has("sort-last") && !dt.get("sort-last").isJsonNull()) deadTabSortLast = dt.get("sort-last").getAsBoolean();
        } else {
            if (root.has(LifeConfig.KEY_DEAD_TAB_ENABLED)) deadTabEnabled = root.get(LifeConfig.KEY_DEAD_TAB_ENABLED).getAsBoolean();
            if (root.has(LifeConfig.KEY_DEAD_TAB_FORMAT)) deadTabFormat = root.get(LifeConfig.KEY_DEAD_TAB_FORMAT).getAsString();
            if (root.has(LifeConfig.KEY_DEAD_TAB_COLOR)) deadTabColor = root.get(LifeConfig.KEY_DEAD_TAB_COLOR).getAsString();
            if (root.has(LifeConfig.KEY_DEAD_TAB_SHOW_TIME)) deadTabShowTime = root.get(LifeConfig.KEY_DEAD_TAB_SHOW_TIME).getAsBoolean();
            if (root.has(LifeConfig.KEY_DEAD_TAB_MAX_ENTRIES)) deadTabMaxEntries = root.get(LifeConfig.KEY_DEAD_TAB_MAX_ENTRIES).getAsInt();
            if (root.has(LifeConfig.KEY_DEAD_TAB_SORT_LAST)) deadTabSortLast = root.get(LifeConfig.KEY_DEAD_TAB_SORT_LAST).getAsBoolean();
        }

        Map<Integer, String> colors = new HashMap<>(defaults.getLifeColors());
        if (root.has(LifeConfig.KEY_COLORS) && root.get(LifeConfig.KEY_COLORS).isJsonObject()) {
            JsonObject colorsObj = root.getAsJsonObject(LifeConfig.KEY_COLORS);
            for (Map.Entry<String, JsonElement> entry : colorsObj.entrySet()) {
                try {
                    int lives = Integer.parseInt(entry.getKey());
                    colors.put(lives, entry.getValue().getAsString());
                } catch (NumberFormatException ignored) {}
            }
        }

        Map<String, String> messages = new HashMap<>(defaults.getMessages());
        if (root.has(LifeConfig.KEY_MESSAGES) && root.get(LifeConfig.KEY_MESSAGES).isJsonObject()) {
            JsonObject msgsObj = root.getAsJsonObject(LifeConfig.KEY_MESSAGES);
            for (Map.Entry<String, JsonElement> entry : msgsObj.entrySet()) {
                messages.put(entry.getKey(), entry.getValue().getAsString());
            }
        }

        return new LifeConfig(
                prefix,
                maxLives,
                banHours,
                restoredLivesAfterBan,
                regenHours,
                giftRegenHours,
                afkMinutes,
                retaliationWindow,
                giftTimeout,
                autosaveMinutes,
                deadTabEnabled,
                deadTabFormat,
                deadTabColor,
                deadTabShowTime,
                deadTabMaxEntries,
                deadTabSortLast,
                colors,
                messages
        );
    }

    private static String getString(JsonObject obj, String key, String def) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsString() : def;
    }

    private static int getInt(JsonObject obj, String key, int def) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsInt() : def;
    }

    private static double getDouble(JsonObject obj, String key, double def) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsDouble() : def;
    }

    private static long getLong(JsonObject obj, String key, long def) {
        return (obj.has(key) && !obj.get(key).isJsonNull()) ? obj.get(key).getAsLong() : def;
    }
}
