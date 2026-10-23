// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 touchfalls

package io.github.lifesmp.core.ports;

import java.time.Instant;

/**
 * Clock abstraction for decoupling system time from domain logic,
 * enabling deterministic unit and simulation testing.
 */
public interface Clock {

    /**
     * Obtains the current instant.
     *
     * @return current instant
     */
    Instant now();

    /**
     * Creates a standard system clock using {@link Instant#now()}.
     *
     * @return system clock instance
     */
    static Clock system() {
        return Instant::now;
    }
}
