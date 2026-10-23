// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.core.config;

import org.jetbrains.annotations.NotNull;

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
    }

    /**
     * Creates a default configuration with standard rules.
     */
    public static LifeConfig createDefault() {
        Map<Integer, String> colors = new HashMap<>();
        colors.put(3, "#55FF55");
        colors.put(2, "#FFAA00");
        colors.put(1, "#FF5555");
        colors.put(0, "#AAAAAA");

        Map<String, String> msgs = new HashMap<>();
        msgs.put("pvp.red-cannot-attack-red", "{prefix}<red>Вы не можете нападать, пока у вас 1 жизнь</red>");
        msgs.put("pvp.cannot-attack-red", "{prefix}<red>Вы не можете нападать на игрока с 1 жизнью</red>");
        msgs.put("death.ban-kick", "<red><bold>☠ ВЫ ПОТЕРЯЛИ ВСЕ ЖИЗНИ!</bold></red>\n\n<gray>Причина:</gray> <white>Исчерпан лимит жизней</white>\n<gray>Срок бана:</gray> <yellow>{time}</yellow>\n\n<dark_gray>Подробнее: обратитесь к администрации сервера</dark_gray>");
        msgs.put("death.ban-join", "<red><bold>Вы забанены за потерю всех жизней!</bold></red>\n\n<gray>До окончания бана осталось:</gray> <yellow>{time}</yellow>\n<dark_gray>Ожидайте окончания срока или помилования администратором.</dark_gray>");
        msgs.put("death.life-lost", "<dark_gray>▪ <red>☠ Вы погибли.</red> <gray>Осталось жизней:</gray> {hearts} <gray>({lives}/{max})</gray>");
        msgs.put("death.last-life-warning", "<red><bold>Это ваша последняя жизнь!</bold></red> <gray>Следующая смерть приведёт к бану на {time}.</gray>");
        msgs.put("death.arena-death", "{prefix}<gold>Вы погибли на арене! Жизнь сохранена.</gold>");
        msgs.put("regen.life-gained", "{prefix}<green>Вы восстановили жизнь! Текущие жизни: {lives}</green>");
        msgs.put("gift.sent", "{prefix}<green>Вы отправили запрос на передачу жизни игроку <yellow>{player}</yellow>. Ожидание подтверждения ({time}).</green>");
        msgs.put("gift.received", "{prefix}<gold>Игрок <yellow>{player}</yellow> хочет подарить вам жизнь! Введите <click:run_command:'/life accept'><green>/life accept</green></click> в течение {time}.</gold>");
        msgs.put("gift.accepted-donor", "{prefix}<green>Игрок <yellow>{player}</yellow> принял ваш подарок! У вас активирована ускоренная регенерация.</green>");
        msgs.put("gift.accepted-recipient", "{prefix}<green>Вы приняли жизнь от <yellow>{player}</yellow>! Ваши жизни: {lives}.</green>");
        msgs.put("gift.expired", "{prefix}<red>Запрос на передачу жизни истек или был отменен.</red>");
        msgs.put("gift.cannot-gift-self", "{prefix}<red>Вы не можете подарить жизнь самому себе!</red>");
        msgs.put("gift.donor-last-life", "{prefix}<red>Вы не можете подарить последнюю жизнь! У вас должно остаться минимум 2 жизни.</red>");
        msgs.put("gift.recipient-max-lives", "{prefix}<red>У игрока уже максимальное количество жизней!</red>");
        msgs.put("gift.no-pending", "{prefix}<red>У вас нет активных запросов на подарок жизни.</red>");
        msgs.put("info.header", "<dark_gray>──────</dark_gray> {color}<bold>Life SMP</bold></color> <dark_gray>──────</dark_gray>");
        msgs.put("info.lives", "<gray>Жизни:</gray> {hearts} <gray>({lives}/{max})</gray>");
        msgs.put("info.next-life", "<gray>До следующей жизни:</gray> <yellow>{time}</yellow>");
        msgs.put("info.full-lives", "<gray>До следующей жизни:</gray> <green>Жизни полные</green>");
        msgs.put("info.mode-gift", "<gray>Ускоренная регенерация:</gray> <green>активна</green> <gray>(интервал {time})</gray>");
        msgs.put("info.mode-normal", "<gray>Обычная регенерация (интервал {time})</gray>");
        msgs.put("info.afk-paused", "<yellow>Таймер на паузе: AFK</yellow>");
        msgs.put("info.footer", "<dark_gray>────────────────────────</dark_gray>");
        msgs.put("admin.reload-success", "{prefix}<green>Конфигурация Life SMP успешно перезагружена!</green>");
        msgs.put("admin.set-lives", "{prefix}<green>Количество жизней игрока <yellow>{player}</yellow> установлено на {lives}.</green>");
        msgs.put("admin.pardon-success", "{prefix}<green>Игрок <yellow>{player}</yellow> помилован, у него 1 жизнь.</green>");
        msgs.put("admin.pardon-not-banned", "{prefix}<red>Игрок <yellow>{player}</yellow> не находится в бане за смерть.</red>");
        msgs.put("admin.pardon-player-not-found", "{prefix}<red>Игрок <yellow>{player}</yellow> не найден в базе данных.</red>");
        msgs.put("admin.pardon-join-notify", "{prefix}<gold>Вы были помилованы администратором. У вас 1 жизнь!</gold>");
        msgs.put("admin.reset-success", "{prefix}<green>Жизни игрока <yellow>{player}</yellow> полностью восстановлены ({max}/{max}).</green>");
        msgs.put("admin.reset-player-notify", "{prefix}<green>Администратор полностью восстановил ваши жизни ({lives}/{max})!</green>");
        msgs.put("admin.reset-player-not-found", "{prefix}<red>Игрок <yellow>{player}</yellow> не найден в базе данных.</red>");

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
                msgs
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
     * Helper overload to format a message with a single placeholder.
     */
    @NotNull
    public String formatMessage(@NotNull String key, @NotNull String placeholderKey, @NotNull String placeholderValue) {
        return formatMessage(key, Map.of(placeholderKey, placeholderValue));
    }
}
