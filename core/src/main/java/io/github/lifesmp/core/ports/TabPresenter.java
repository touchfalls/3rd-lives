// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.ports;

import io.github.lifesmp.core.domain.model.DeadEntry;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.UUID;

/**
 * Platform abstraction port for presenting fake dead player entries in the TAB list.
 * Core domain service calls this port; Paper and Fabric adapters implement it using network packets.
 */
public interface TabPresenter {

    /**
     * Shows a single dead player entry to all online players.
     *
     * @param entry dead player entry to display
     */
    void showDead(@NotNull DeadEntry entry);

    /**
     * Hides/removes a single dead player entry from all online players' TAB lists.
     *
     * @param uuid player UUID
     */
    void hideDead(@NotNull UUID uuid);

    /**
     * Sends the complete batch of current dead player entries to a single viewer who just joined.
     *
     * @param viewerUuid viewer player UUID
     * @param entries    active dead entries collection
     */
    void showAllTo(@NotNull UUID viewerUuid, @NotNull Collection<DeadEntry> entries);

    /**
     * Refreshes display text or timers of all active dead entries across all online viewers.
     *
     * @param entries active dead entries collection
     */
    void refreshAll(@NotNull Collection<DeadEntry> entries);

    /**
     * Parameterless overload for refreshing dead player entries in the tab list.
     */
    default void refreshAll() {
        refreshAll(java.util.Collections.emptyList());
    }
}
