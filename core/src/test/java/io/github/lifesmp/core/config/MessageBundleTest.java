// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class MessageBundleTest {

    @Test
    @DisplayName("Every bundle defines exactly the same keys as the English bundle")
    void allBundlesHaveSameKeys() throws Exception {
        Properties english = read("en");
        assertThat(english.stringPropertyNames()).isNotEmpty();

        for (String language : MessageBundle.SUPPORTED_LANGUAGES) {
            Properties bundle = read(language);
            assertThat(bundle.stringPropertyNames())
                    .as("keys of %s.properties", language)
                    .containsExactlyInAnyOrderElementsOf(english.stringPropertyNames());
        }
    }

    @Test
    @DisplayName("Language codes are normalized and unsupported values fall back to English")
    void normalizesLanguageCodes() {
        assertThat(MessageBundle.normalize("ru")).isEqualTo("ru");
        assertThat(MessageBundle.normalize("RU")).isEqualTo("ru");
        assertThat(MessageBundle.normalize("es_ES")).isEqualTo("es");
        assertThat(MessageBundle.normalize("fr-FR")).isEqualTo("fr");
        assertThat(MessageBundle.normalize("de")).isEqualTo("en");
        assertThat(MessageBundle.normalize("")).isEqualTo("en");
        assertThat(MessageBundle.normalize(null)).isEqualTo("en");
    }

    @Test
    @DisplayName("Default configuration uses the messages and time units of the selected language")
    void defaultConfigUsesSelectedLanguage() {
        LifeConfig english = LifeConfig.createDefault();
        LifeConfig russian = LifeConfig.createDefault("ru");
        LifeConfig italian = LifeConfig.createDefault("it");

        assertThat(english.getRawMessage("gift.no-pending")).contains("You have no pending life gift requests");
        assertThat(russian.getRawMessage("gift.no-pending")).contains("У вас нет активных запросов");
        assertThat(italian.getRawMessage("gift.no-pending")).contains("Non hai richieste");

        Duration duration = Duration.ofDays(1).plusHours(2);
        assertThat(english.formatDuration(duration)).isEqualTo("1d 2h");
        assertThat(russian.formatDuration(duration)).isEqualTo("1д 2ч");
        assertThat(italian.formatDuration(duration)).isEqualTo("1g 2h");
    }

    @Test
    @DisplayName("formatMessage replaces the prefix and both placeholder styles")
    void formatMessageReplacesPlaceholders() {
        LifeConfig config = LifeConfig.createDefault();
        String formatted = config.formatMessage("arena.not-found", "name", "pit");
        assertThat(formatted).startsWith(config.getPrefix()).contains("'pit'").doesNotContain("{prefix}");
    }

    private static Properties read(String language) throws Exception {
        String path = "/lifesmp/lang/" + language + ".properties";
        try (InputStream in = MessageBundle.class.getResourceAsStream(path)) {
            assertThat(in).as(path).isNotNull();
            Properties properties = new Properties();
            properties.load(new InputStreamReader(in, StandardCharsets.UTF_8));
            return properties;
        }
    }
}
