// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.config;

import io.github.lifesmp.core.util.FormatUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Configuration model for Life SMP.
 * Holds all gameplay parameters, timers (internally computed in seconds),
 * color definitions, prefix, and MiniMessage strings.
 */
public final class LifeConfig {

    public static final String KEY_LANGUAGE = "language";
    public static final String KEY_PREFIX = "prefix";
    public static final String KEY_MAX_LIVES = "max-lives";
    public static final String KEY_BAN_HOURS = "ban-hours";
    public static final String KEY_RESTORED_LIVES_AFTER_BAN = "restored-lives-after-ban";
    public static final String KEY_REGEN_HOURS_PER_LIFE = "regen-hours-per-life";
    public static final String KEY_GIFT_REGEN_HOURS_PER_LIFE = "gift-regen-hours-per-life";
    public static final String KEY_AFK_MINUTES = "afk-minutes";
    public static final String KEY_RETALIATION_WINDOW_SECONDS = "retaliation-window-seconds";
    public static final String KEY_GIFT_REQUEST_TIMEOUT_SECONDS = "gift-request-timeout-seconds";
    public static final String KEY_AUTOSAVE_MINUTES = "autosave-minutes";
    public static final String KEY_COLORS = "colors";
    public static final String KEY_MESSAGES = "messages";

    public static final String KEY_DEAD_TAB_ENABLED = "dead-tab.enabled";
    public static final String KEY_DEAD_TAB_FORMAT = "dead-tab.format";
    public static final String KEY_DEAD_TAB_COLOR = "dead-tab.color";
    public static final String KEY_DEAD_TAB_SHOW_TIME = "dead-tab.show-time";
    public static final String KEY_DEAD_TAB_MAX_ENTRIES = "dead-tab.max-entries";
    public static final String KEY_DEAD_TAB_SORT_LAST = "dead-tab.sort-last";

    public static final String DEFAULT_PREFIX = "<dark_gray>[<gradient:#55FF55:#FFAA00>Life</gradient>]</dark_gray> ";
    public static final boolean DEFAULT_DEAD_TAB_ENABLED = true;
    public static final String DEFAULT_DEAD_TAB_FORMAT = "<gray>☠ {player}</gray> <dark_gray>[DEAD]</dark_gray>";
    public static final String DEFAULT_DEAD_TAB_COLOR = "#AAAAAA";
    public static final boolean DEFAULT_DEAD_TAB_SHOW_TIME = false;
    public static final int DEFAULT_DEAD_TAB_MAX_ENTRIES = 50;
    public static final boolean DEFAULT_DEAD_TAB_SORT_LAST = true;

    private final String prefix;
    private final int maxLives;
    private final double banHours;
    private final int restoredLivesAfterBan;
    private final double regenHoursPerLife;
    private final double giftRegenHoursPerLife;
    private final double afkMinutes;
    private final long retaliationWindowSeconds;
    private final long giftRequestTimeoutSeconds;
    private final double autosaveMinutes;

    private final boolean deadTabEnabled;
    private final String deadTabFormat;
    private final String deadTabColor;
    private final boolean deadTabShowTime;
    private final int deadTabMaxEntries;
    private final boolean deadTabSortLast;

    private final Map<Integer, String> lifeColors;
    private final Map<String, String> messages;
    private final FormatUtils.TimeUnits timeUnits;

    public LifeConfig(
            int maxLives,
            double banHours,
            int restoredLivesAfterBan,
            double regenHoursPerLife,
            double giftRegenHoursPerLife,
            double afkMinutes,
            long retaliationWindowSeconds,
            long giftRequestTimeoutSeconds,
            double autosaveMinutes,
            @NotNull Map<Integer, String> lifeColors,
            @NotNull Map<String, String> messages
    ) {
        this(
                DEFAULT_PREFIX,
                maxLives,
                banHours,
                restoredLivesAfterBan,
                regenHoursPerLife,
                giftRegenHoursPerLife,
                afkMinutes,
                retaliationWindowSeconds,
                giftRequestTimeoutSeconds,
                autosaveMinutes,
                DEFAULT_DEAD_TAB_ENABLED,
                DEFAULT_DEAD_TAB_FORMAT,
                DEFAULT_DEAD_TAB_COLOR,
                DEFAULT_DEAD_TAB_SHOW_TIME,
                DEFAULT_DEAD_TAB_MAX_ENTRIES,
                DEFAULT_DEAD_TAB_SORT_LAST,
                lifeColors,
                messages
        );
    }

    public LifeConfig(
            @NotNull String prefix,
            int maxLives,
            double banHours,
            int restoredLivesAfterBan,
            double regenHoursPerLife,
            double giftRegenHoursPerLife,
            double afkMinutes,
            long retaliationWindowSeconds,
            long giftRequestTimeoutSeconds,
            double autosaveMinutes,
            @NotNull Map<Integer, String> lifeColors,
            @NotNull Map<String, String> messages
    ) {
        this(
                prefix,
                maxLives,
                banHours,
                restoredLivesAfterBan,
                regenHoursPerLife,
                giftRegenHoursPerLife,
                afkMinutes,
                retaliationWindowSeconds,
                giftRequestTimeoutSeconds,
                autosaveMinutes,
                DEFAULT_DEAD_TAB_ENABLED,
                DEFAULT_DEAD_TAB_FORMAT,
                DEFAULT_DEAD_TAB_COLOR,
                DEFAULT_DEAD_TAB_SHOW_TIME,
                DEFAULT_DEAD_TAB_MAX_ENTRIES,
                DEFAULT_DEAD_TAB_SORT_LAST,
                lifeColors,
                messages
        );
    }

    public LifeConfig(
            @NotNull String prefix,
            int maxLives,
            double banHours,
            int restoredLivesAfterBan,
            double regenHoursPerLife,
            double giftRegenHoursPerLife,
            double afkMinutes,
            long retaliationWindowSeconds,
            long giftRequestTimeoutSeconds,
            double autosaveMinutes,
            boolean deadTabEnabled,
            @NotNull String deadTabFormat,
            @NotNull String deadTabColor,
            boolean deadTabShowTime,
            int deadTabMaxEntries,
            boolean deadTabSortLast,
            @NotNull Map<Integer, String> lifeColors,
            @NotNull Map<String, String> messages
    ) {
        this.prefix = Objects.requireNonNull(prefix, "prefix cannot be null");
        this.maxLives = Math.max(1, maxLives);
        this.banHours = Math.max(0.0, banHours);
        this.restoredLivesAfterBan = Math.max(1, restoredLivesAfterBan);
        this.regenHoursPerLife = Math.max(0.001, regenHoursPerLife);
        this.giftRegenHoursPerLife = Math.max(0.001, giftRegenHoursPerLife);
        this.afkMinutes = Math.max(0.1, afkMinutes);
        this.retaliationWindowSeconds = Math.max(1L, retaliationWindowSeconds);
        this.giftRequestTimeoutSeconds = Math.max(1L, giftRequestTimeoutSeconds);
        this.autosaveMinutes = Math.max(0.1, autosaveMinutes);

        this.deadTabEnabled = deadTabEnabled;
        this.deadTabFormat = Objects.requireNonNull(deadTabFormat, "deadTabFormat cannot be null");
        this.deadTabColor = Objects.requireNonNull(deadTabColor, "deadTabColor cannot be null");
        this.deadTabShowTime = deadTabShowTime;
        this.deadTabMaxEntries = Math.max(1, deadTabMaxEntries);
        this.deadTabSortLast = deadTabSortLast;

        this.lifeColors = Collections.unmodifiableMap(new HashMap<>(lifeColors));
        this.messages = Collections.unmodifiableMap(new HashMap<>(messages));
        this.timeUnits = new FormatUtils.TimeUnits(
                this.messages.getOrDefault("time.day", FormatUtils.TimeUnits.ENGLISH.day()),
                this.messages.getOrDefault("time.hour", FormatUtils.TimeUnits.ENGLISH.hour()),
                this.messages.getOrDefault("time.minute", FormatUtils.TimeUnits.ENGLISH.minute()),
                this.messages.getOrDefault("time.second", FormatUtils.TimeUnits.ENGLISH.second())
        );
    }

    /**
     * Creates a default configuration with standard rules and English messages.
     */
    public static LifeConfig createDefault() {
        return createDefault(MessageBundle.DEFAULT_LANGUAGE);
    }

    /**
     * Creates a default configuration with standard rules and the built-in messages of a language.
     *
     * @param language language code from {@link #KEY_LANGUAGE}; unsupported values fall back to English
     */
    public static LifeConfig createDefault(@Nullable String language) {
        Map<Integer, String> colors = new HashMap<>();
        colors.put(3, "#55FF55");
        colors.put(2, "#FFAA00");
        colors.put(1, "#FF5555");
        colors.put(0, "#AAAAAA");

        return new LifeConfig(
                DEFAULT_PREFIX,
                3,
                24.0,
                3,
                10.0,
                2.5,
                180.0,
                30L,
                60L,
                5.0,
                colors,
                MessageBundle.load(language)
        );
    }

    @NotNull
    public String getPrefix() {
        return prefix;
    }

    public int getMaxLives() {
        return maxLives;
    }

    public double getBanHours() {
        return banHours;
    }

    public long getBanSeconds() {
        return Math.round(banHours * 3600.0);
    }

    public int getRestoredLivesAfterBan() {
        return restoredLivesAfterBan;
    }

    public double getRegenHoursPerLife() {
        return regenHoursPerLife;
    }

    public long getRegenSecondsPerLife() {
        return Math.round(regenHoursPerLife * 3600.0);
    }

    public double getGiftRegenHoursPerLife() {
        return giftRegenHoursPerLife;
    }

    public long getGiftRegenSecondsPerLife() {
        return Math.round(giftRegenHoursPerLife * 3600.0);
    }

    public double getAfkMinutes() {
        return afkMinutes;
    }

    public long getAfkSeconds() {
        return Math.round(afkMinutes * 60.0);
    }

    public long getRetaliationWindowSeconds() {
        return retaliationWindowSeconds;
    }

    public long getGiftRequestTimeoutSeconds() {
        return giftRequestTimeoutSeconds;
    }

    public double getAutosaveMinutes() {
        return autosaveMinutes;
    }

    public long getAutosaveSeconds() {
        return Math.round(autosaveMinutes * 60.0);
    }

    public boolean isDeadTabEnabled() {
        return deadTabEnabled;
    }

    @NotNull
    public String getDeadTabFormat() {
        return deadTabFormat;
    }

    @NotNull
    public String getDeadTabColor() {
        return deadTabColor;
    }

    public boolean isDeadTabShowTime() {
        return deadTabShowTime;
    }

    public int getDeadTabMaxEntries() {
        return deadTabMaxEntries;
    }

    public boolean isDeadTabSortLast() {
        return deadTabSortLast;
    }

    @NotNull
    public Map<Integer, String> getLifeColors() {
        return lifeColors;
    }

    @NotNull
    public String getColorHex(int lives) {
        return lifeColors.getOrDefault(lives, "#FFFFFF");
    }

    @NotNull
    public Map<String, String> getMessages() {
        return messages;
    }

    @NotNull
    public FormatUtils.TimeUnits getTimeUnits() {
        return timeUnits;
    }

    /**
     * Formats a duration with the time unit suffixes of the configured language.
     */
    @NotNull
    public String formatDuration(@NotNull Duration duration) {
        return FormatUtils.formatDuration(duration, timeUnits);
    }

    /**
     * Formats a duration as hours and minutes with the time unit suffixes of the configured language.
     */
    @NotNull
    public String formatDurationHoursMinutes(@NotNull Duration duration) {
        return FormatUtils.formatDurationHoursMinutes(duration, timeUnits);
    }

    @NotNull
    public String getRawMessage(@NotNull String key) {
        return messages.getOrDefault(key, key);
    }

    /**
     * Formats a raw message template by substituting placeholder tokens and {prefix}.
     * Tokens can be specified as &lt;key&gt; or {key}.
     *
     * @param key          message key
     * @param placeholders map of token to value substitutions
     * @return formatted message string
     */
    @NotNull
    public String formatMessage(@NotNull String key, @NotNull Map<String, String> placeholders) {
        String template = getRawMessage(key);
        if (template.contains("{prefix}")) {
            template = template.replace("{prefix}", prefix);
        }
        if (placeholders.isEmpty()) {
            return template;
        }
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            String val = entry.getValue() != null ? entry.getValue() : "";
            template = template.replace("<" + entry.getKey() + ">", val)
                               .replace("{" + entry.getKey() + "}", val);
        }
        return template;
    }

    /**
     * Formats a message that has no placeholders other than {prefix}.
     */
    @NotNull
    public String formatMessage(@NotNull String key) {
        return formatMessage(key, Map.of());
    }

    /**
     * Helper overload to format a message with a single placeholder.
     */
    @NotNull
    public String formatMessage(@NotNull String key, @NotNull String placeholderKey, @NotNull String placeholderValue) {
        return formatMessage(key, Map.of(placeholderKey, placeholderValue));
    }
}
