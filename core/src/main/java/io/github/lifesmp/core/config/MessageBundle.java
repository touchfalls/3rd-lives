// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Built-in player-facing messages, one bundle per language, loaded from
 * {@code /lifesmp/lang/<language>.properties} on the classpath.
 * <p>
 * Every bundle is layered on top of the English one, so a key missing from a
 * translation still resolves to the English text instead of the raw key.
 */
public final class MessageBundle {

    public static final String DEFAULT_LANGUAGE = "en";
    public static final List<String> SUPPORTED_LANGUAGES = List.of("en", "ru", "fr", "it", "es");

    private static final String RESOURCE_PATTERN = "/lifesmp/lang/%s.properties";
    private static final Map<String, Map<String, String>> CACHE = new ConcurrentHashMap<>();

    private MessageBundle() {
    }

    /**
     * Maps a configured language value to a supported language code.
     * Accepts region variants such as {@code ru_RU} or {@code es-ES}; anything unsupported becomes English.
     *
     * @param language configured language, may be null or blank
     * @return supported two-letter language code
     */
    @NotNull
    public static String normalize(@Nullable String language) {
        if (language == null || language.isBlank()) {
            return DEFAULT_LANGUAGE;
        }
        String code = language.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        int separator = code.indexOf('_');
        if (separator > 0) {
            code = code.substring(0, separator);
        }
        return SUPPORTED_LANGUAGES.contains(code) ? code : DEFAULT_LANGUAGE;
    }

    /**
     * Returns the built-in messages for a language, with English as fallback for missing keys.
     *
     * @param language configured language
     * @return unmodifiable map of message key to MiniMessage template
     */
    @NotNull
    public static Map<String, String> load(@Nullable String language) {
        return CACHE.computeIfAbsent(normalize(language), code -> {
            Map<String, String> merged = new HashMap<>(readBundle(DEFAULT_LANGUAGE));
            if (!DEFAULT_LANGUAGE.equals(code)) {
                merged.putAll(readBundle(code));
            }
            return Collections.unmodifiableMap(merged);
        });
    }

    private static Map<String, String> readBundle(String code) {
        String path = String.format(RESOURCE_PATTERN, code);
        try (InputStream in = MessageBundle.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing message bundle on classpath: " + path);
            }
            // Properties.load(InputStream) assumes ISO-8859-1; the bundles are UTF-8.
            Properties properties = new Properties();
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                properties.load(reader);
            }
            Map<String, String> messages = new HashMap<>();
            for (String key : properties.stringPropertyNames()) {
                messages.put(key, properties.getProperty(key));
            }
            return messages;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read message bundle " + path, e);
        }
    }
}
