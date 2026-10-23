// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.paper.config;

import io.github.lifesmp.core.config.LifeConfig;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Adapter reading Bukkit/Paper {@link FileConfiguration} and creating a typed {@link LifeConfig}.
 */
public final class PaperConfigAdapter {

    private PaperConfigAdapter() {}

    /**
     * Extracts {@link LifeConfig} from a Paper YAML configuration.
     *
     * @param config Paper YAML configuration
     * @return initialized LifeConfig
     */
    @NotNull
    public static LifeConfig load(@NotNull FileConfiguration config) {
        LifeConfig defaults = LifeConfig.createDefault(config.getString(LifeConfig.KEY_LANGUAGE));

        String prefix = config.getString(LifeConfig.KEY_PREFIX, defaults.getPrefix());
        int maxLives = config.getInt(LifeConfig.KEY_MAX_LIVES, defaults.getMaxLives());
        double banHours = config.getDouble(LifeConfig.KEY_BAN_HOURS, defaults.getBanHours());
        int restoredLivesAfterBan = config.getInt(LifeConfig.KEY_RESTORED_LIVES_AFTER_BAN, defaults.getRestoredLivesAfterBan());
        double regenHours = config.getDouble(LifeConfig.KEY_REGEN_HOURS_PER_LIFE, defaults.getRegenHoursPerLife());
        double giftRegenHours = config.getDouble(LifeConfig.KEY_GIFT_REGEN_HOURS_PER_LIFE, defaults.getGiftRegenHoursPerLife());
        double afkMinutes = config.getDouble(LifeConfig.KEY_AFK_MINUTES, defaults.getAfkMinutes());
        long retaliationWindow = config.getLong(LifeConfig.KEY_RETALIATION_WINDOW_SECONDS, defaults.getRetaliationWindowSeconds());
        long giftTimeout = config.getLong(LifeConfig.KEY_GIFT_REQUEST_TIMEOUT_SECONDS, defaults.getGiftRequestTimeoutSeconds());
        double autosaveMinutes = config.getDouble(LifeConfig.KEY_AUTOSAVE_MINUTES, defaults.getAutosaveMinutes());

        boolean deadTabEnabled = config.getBoolean(LifeConfig.KEY_DEAD_TAB_ENABLED, defaults.isDeadTabEnabled());
        String deadTabFormat = config.getString(LifeConfig.KEY_DEAD_TAB_FORMAT, defaults.getDeadTabFormat());
        String deadTabColor = config.getString(LifeConfig.KEY_DEAD_TAB_COLOR, defaults.getDeadTabColor());
        boolean deadTabShowTime = config.getBoolean(LifeConfig.KEY_DEAD_TAB_SHOW_TIME, defaults.isDeadTabShowTime());
        int deadTabMaxEntries = config.getInt(LifeConfig.KEY_DEAD_TAB_MAX_ENTRIES, defaults.getDeadTabMaxEntries());
        boolean deadTabSortLast = config.getBoolean(LifeConfig.KEY_DEAD_TAB_SORT_LAST, defaults.isDeadTabSortLast());

        Map<Integer, String> colors = new HashMap<>(defaults.getLifeColors());
        ConfigurationSection colorsSection = config.getConfigurationSection(LifeConfig.KEY_COLORS);
        if (colorsSection != null) {
            for (String key : colorsSection.getKeys(false)) {
                try {
                    int lives = Integer.parseInt(key);
                    colors.put(lives, colorsSection.getString(key));
                } catch (NumberFormatException ignored) {}
            }
        }

        Map<String, String> messages = new HashMap<>(defaults.getMessages());
        ConfigurationSection messagesSection = config.getConfigurationSection(LifeConfig.KEY_MESSAGES);
        if (messagesSection != null) {
            for (String key : messagesSection.getKeys(true)) {
                if (!messagesSection.isConfigurationSection(key)) {
                    messages.put(key, messagesSection.getString(key));
                }
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
}
