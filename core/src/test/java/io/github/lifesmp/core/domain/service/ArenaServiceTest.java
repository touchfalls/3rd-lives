// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.domain.service;

import io.github.lifesmp.core.ports.ArenaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ArenaServiceTest {

    @Test
    @DisplayName("Creating an arena with a name that already exists keeps the old arena")
    void duplicateNameDoesNotOverwrite() {
        ArenaRepository repository = mock(ArenaRepository.class);
        when(repository.saveArena(any())).thenReturn(CompletableFuture.completedFuture(null));
        ArenaService service = new ArenaService(repository);

        assertThat(service.createArena("Pit", "world", 0, 0, 0, 10, 10, 10).join()).isTrue();
        assertThat(service.createArena("pit", "world", 100, 0, 100, 110, 10, 110).join()).isFalse();

        assertThat(service.isInsideActiveArena("world", 5, 5, 5)).isTrue();
        assertThat(service.isInsideActiveArena("world", 105, 5, 105)).isFalse();
        verify(repository, times(1)).saveArena(any());
    }
}
