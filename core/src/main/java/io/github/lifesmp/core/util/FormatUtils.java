// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.core.util;

import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

/**
 * High-performance utility methods for formatting durations, hearts, and numbers.
 * Employs primitive overloads and string caching to minimize memory allocations on hot paths.
 */
public final class FormatUtils {

    private static final Map<String, String> HEARTS_CACHE = new ConcurrentHashMap<>(32);

    private FormatUtils() {}

    /**
     * Formats a {@link Duration} into a human-readable duration string.
     *
     * @param duration target duration
     * @return formatted duration string
     */
    @NotNull
    public static String formatDuration(@NotNull Duration duration) {
        Objects.requireNonNull(duration, "duration cannot be null");
        return formatDuration(duration.toSeconds());
    }

    /**
     * Primitive overload formatting total seconds into a duration string without allocating a {@link Duration}.
     *
     * @param totalSeconds duration in seconds
     * @return formatted duration string
     */
    @NotNull
    public static String formatDuration(long totalSeconds) {
        if (totalSeconds <= 0L) {
            return "0с";
        }

        long days = totalSeconds / 86400L;
        long hours = (totalSeconds % 86400L) / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;

        StringBuilder sb = new StringBuilder(16);
        if (days > 0) {
            sb.append(days).append("д");
        }
        if (hours > 0) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(hours).append("ч");
        }
        if (minutes > 0) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(minutes).append("м");
        }
        if (seconds > 0 && days == 0) {
            if (!sb.isEmpty()) sb.append(" ");
            sb.append(seconds).append("с");
        }
        return sb.toString();
    }

    /**
     * Formats a {@link Duration} into hour-minute format,
     * primarily used for death ban remaining time displays.
     *
     * @param duration target duration
     * @return formatted duration string in hour-minute format
     */
    @NotNull
    public static String formatDurationHoursMinutes(@NotNull Duration duration) {
        Objects.requireNonNull(duration, "duration cannot be null");
        return formatDurationHoursMinutes(duration.toSeconds());
    }

    /**
     * Primitive overload formatting total seconds into hour-minute format without allocating a {@link Duration}.
     *
     * @param totalSeconds duration in seconds
     * @return formatted duration string in hour-minute format
     */
    @NotNull
    public static String formatDurationHoursMinutes(long totalSeconds) {
        long nonNegative = Math.max(0L, totalSeconds);
        long hours = nonNegative / 3600L;
        long minutes = (nonNegative % 3600L) / 60L;
        return hours + "ч " + minutes + "м";
    }

    /**
     * Formats player lives into a visual MiniMessage hearts string.
     * Filled hearts (❤) are rendered in the player's life color, while empty hearts (❤) are dark gray.
     * Cached by (lives, maxLives, color) to eliminate runtime StringBuilder allocations.
     *
     * @param lives         current life count
     * @param maxLives      maximum lives configured
     * @param colorTagOrHex color specification (e.g. "#FFAA00", "<green>", "gold")
     * @return MiniMessage formatted hearts string (e.g. "<color:#FFAA00>❤❤</color><dark_gray>❤</dark_gray>")
     */
    @NotNull
    public static String formatHearts(int lives, int maxLives, @NotNull String colorTagOrHex) {
        Objects.requireNonNull(colorTagOrHex, "colorTagOrHex cannot be null");
        int total = Math.max(1, maxLives);
        int filled = Math.max(0, Math.min(total, lives));

        String cacheKey = filled + ":" + total + ":" + colorTagOrHex;
        return HEARTS_CACHE.computeIfAbsent(cacheKey, k -> buildHearts(filled, total, colorTagOrHex));
    }

    private static String buildHearts(int filled, int total, String colorTagOrHex) {
        int empty = total - filled;

        String openTag;
        String closeTag;
        if (colorTagOrHex.startsWith("#")) {
            openTag = "<color:" + colorTagOrHex + ">";
            closeTag = "</color>";
        } else if (colorTagOrHex.startsWith("<") && colorTagOrHex.endsWith(">")) {
            openTag = colorTagOrHex;
            String tagName = colorTagOrHex.substring(1, colorTagOrHex.length() - 1);
            if (tagName.startsWith("color:")) {
                closeTag = "</color>";
            } else {
                closeTag = "</" + tagName + ">";
            }
        } else {
            openTag = "<" + colorTagOrHex + ">";
            closeTag = "</" + colorTagOrHex + ">";
        }

        StringBuilder sb = new StringBuilder(48);
        if (filled > 0) {
            sb.append(openTag);
            for (int i = 0; i < filled; i++) {
                sb.append('❤');
            }
            sb.append(closeTag);
        }
        if (empty > 0) {
            sb.append("<dark_gray>");
            for (int i = 0; i < empty; i++) {
                sb.append('❤');
            }
            sb.append("</dark_gray>");
        }
        return sb.toString();
    }
}
