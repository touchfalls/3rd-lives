// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.fabric.util;

import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Lightweight parser that converts MiniMessage formatted strings, gradient tags,
 * and click events into native Minecraft {@link Component} instances with caching.
 */
public final class FabricMessageFormatter {

    private static final Pattern TAG_PATTERN = Pattern.compile("<(/?[a-zA-Z0-9_#:-]+)(?::'([^']*)')?>");
    private static final int MAX_CACHE_SIZE = 512;
    private static final Map<String, Component> CACHE = new ConcurrentHashMap<>();

    private static final Map<String, Integer> NAMED_COLORS = Map.ofEntries(
            Map.entry("green", 0x55FF55),
            Map.entry("gold", 0xFFAA00),
            Map.entry("red", 0xFF5555),
            Map.entry("gray", 0xAAAAAA),
            Map.entry("yellow", 0xFFFF55),
            Map.entry("white", 0xFFFFFF),
            Map.entry("aqua", 0x55FFFF),
            Map.entry("blue", 0x5555FF),
            Map.entry("light_purple", 0xFF55FF),
            Map.entry("dark_red", 0xAA0000),
            Map.entry("dark_green", 0x00AA00),
            Map.entry("dark_blue", 0x0000AA),
            Map.entry("dark_aqua", 0x00AAAA),
            Map.entry("dark_gray", 0x555555),
            Map.entry("black", 0x000000)
    );

    private FabricMessageFormatter() {}

    /**
     * Parses a MiniMessage formatted text string into a native Minecraft {@link Component}.
     *
     * @param text input formatted string
     * @return composite Minecraft component
     */
    @NotNull
    public static Component parse(@Nullable String text) {
        if (text == null || text.isEmpty()) {
            return Component.empty();
        }

        Component cached = CACHE.get(text);
        if (cached != null) {
            return cached;
        }

        Component parsed = parseInternal(text);
        if (CACHE.size() < MAX_CACHE_SIZE) {
            CACHE.put(text, parsed);
        }
        return parsed;
    }

    /**
     * Clears cached components on reload.
     */
    public static void clearCache() {
        CACHE.clear();
    }

    @NotNull
    private static Component parseInternal(@NotNull String text) {
        MutableComponent root = Component.empty();
        Matcher matcher = TAG_PATTERN.matcher(text);

        Deque<FormatState> stateStack = new ArrayDeque<>();
        stateStack.push(new FormatState(null, null, false, false, null));

        int lastEnd = 0;
        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                String segment = text.substring(lastEnd, matcher.start());
                appendSegment(root, segment, stateStack.peek());
            }

            String tag = matcher.group(1).toLowerCase();
            FormatState current = stateStack.peek();

            if (tag.startsWith("/")) {
                // Closing tag
                if (stateStack.size() > 1) {
                    stateStack.pop();
                }
            } else if (tag.startsWith("#") && tag.length() == 7) {
                // Hex color: <#RRGGBB>
                try {
                    int hex = Integer.parseInt(tag.substring(1), 16);
                    stateStack.push(new FormatState(hex, null, current.bold, current.italic, current.clickEvent));
                } catch (NumberFormatException ignored) {}
            } else if (tag.startsWith("color:")) {
                // <color:#RRGGBB>
                String colorVal = tag.substring(6);
                if (colorVal.startsWith("#") && colorVal.length() == 7) {
                    try {
                        int hex = Integer.parseInt(colorVal.substring(1), 16);
                        stateStack.push(new FormatState(hex, null, current.bold, current.italic, current.clickEvent));
                    } catch (NumberFormatException ignored) {}
                }
            } else if (tag.startsWith("gradient:")) {
                // <gradient:#HEX1:#HEX2>
                String[] parts = tag.substring(9).split(":");
                if (parts.length >= 2) {
                    try {
                        String h1 = parts[0].startsWith("#") ? parts[0].substring(1) : parts[0];
                        String h2 = parts[1].startsWith("#") ? parts[1].substring(1) : parts[1];
                        int c1 = Integer.parseInt(h1, 16);
                        int c2 = Integer.parseInt(h2, 16);
                        stateStack.push(new FormatState(null, new Gradient(c1, c2), current.bold, current.italic, current.clickEvent));
                    } catch (NumberFormatException ignored) {}
                }
            } else if (tag.equals("click:run_command") && matcher.group(2) != null) {
                String command = matcher.group(2);
                ClickEvent click = new ClickEvent(ClickEvent.Action.RUN_COMMAND, command);
                stateStack.push(new FormatState(current.colorRgb, current.gradient, current.bold, current.italic, click));
            } else if (NAMED_COLORS.containsKey(tag)) {
                stateStack.push(new FormatState(NAMED_COLORS.get(tag), null, current.bold, current.italic, current.clickEvent));
            } else if ("bold".equals(tag) || "b".equals(tag)) {
                stateStack.push(new FormatState(current.colorRgb, current.gradient, true, current.italic, current.clickEvent));
            } else if ("italic".equals(tag) || "i".equals(tag)) {
                stateStack.push(new FormatState(current.colorRgb, current.gradient, current.bold, true, current.clickEvent));
            } else if ("reset".equals(tag) || "r".equals(tag)) {
                stateStack.push(new FormatState(null, null, false, false, null));
            }

            lastEnd = matcher.end();
        }

        if (lastEnd < text.length()) {
            String remainder = text.substring(lastEnd);
            appendSegment(root, remainder, stateStack.peek());
        }

        return root;
    }

    private static void appendSegment(MutableComponent root, String segment, FormatState state) {
        if (segment.isEmpty()) return;

        if (state != null && state.gradient != null) {
            Gradient grad = state.gradient;
            int len = segment.length();
            for (int i = 0; i < len; i++) {
                float t = len > 1 ? (float) i / (len - 1) : 0f;
                int r1 = (grad.startRgb >> 16) & 0xFF;
                int g1 = (grad.startRgb >> 8) & 0xFF;
                int b1 = grad.startRgb & 0xFF;
                int r2 = (grad.endRgb >> 16) & 0xFF;
                int g2 = (grad.endRgb >> 8) & 0xFF;
                int b2 = grad.endRgb & 0xFF;
                int r = (int) (r1 + t * (r2 - r1));
                int g = (int) (g1 + t * (g2 - g1));
                int b = (int) (b1 + t * (b2 - b1));
                int rgb = (r << 16) | (g << 8) | b;
                MutableComponent ch = Component.literal(String.valueOf(segment.charAt(i)));
                root.append(applyStyle(ch, state, rgb));
            }
        } else {
            root.append(applyStyle(Component.literal(segment), state, state != null ? state.colorRgb : null));
        }
    }

    private static Component applyStyle(MutableComponent comp, FormatState state, @Nullable Integer colorOverride) {
        if (state == null && colorOverride == null) {
            return comp;
        }
        return comp.withStyle(style -> {
            Style s = style;
            if (colorOverride != null) {
                s = s.withColor(TextColor.fromRgb(colorOverride));
            } else if (state != null && state.colorRgb != null) {
                s = s.withColor(TextColor.fromRgb(state.colorRgb));
            }
            if (state != null) {
                if (state.bold) {
                    s = s.withBold(true);
                }
                if (state.italic) {
                    s = s.withItalic(true);
                }
                if (state.clickEvent != null) {
                    s = s.withClickEvent(state.clickEvent);
                }
            }
            return s;
        });
    }

    private record Gradient(int startRgb, int endRgb) {}
    private record FormatState(
            @Nullable Integer colorRgb,
            @Nullable Gradient gradient,
            boolean bold,
            boolean italic,
            @Nullable ClickEvent clickEvent
    ) {}
}
