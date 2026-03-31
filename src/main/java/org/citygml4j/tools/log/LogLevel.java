/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.log;

import java.util.Locale;

public enum LogLevel {
    ERROR,
    WARN,
    INFO,
    DEBUG;

    @Override
    public String toString() {
        return name().toLowerCase(Locale.ROOT);
    }
}
