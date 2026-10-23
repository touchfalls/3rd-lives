// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) <FILL IN: year> <FILL IN: author>

package io.github.lifesmp.fabric;

import io.github.lifesmp.core.config.LifeConfig;
import io.github.lifesmp.core.domain.service.LifeEngine;
import io.github.lifesmp.fabric.platform.FabricServerBridge;
import org.jetbrains.annotations.NotNull;

/**
 * Host contract providing access to server services and configuration reload.
 */
public interface FabricServerHost {

    @NotNull
    LifeConfig getLifeConfig();

    @NotNull
    LifeEngine getLifeEngine();

    @NotNull
    FabricServerBridge getBridge();

    void reloadServerConfig();
}
