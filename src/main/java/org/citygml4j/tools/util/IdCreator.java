/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.util;

import org.xmlobjects.gml.util.id.DefaultIdCreator;

import java.security.SecureRandom;
import java.util.UUID;

public class IdCreator implements org.xmlobjects.gml.util.id.IdCreator {
    private static final String seed;

    static {
        seed = Long.toUnsignedString(new SecureRandom().nextLong() ^ System.currentTimeMillis());
    }

    private final String prefix = DefaultIdCreator.getInstance().getDefaultPrefix();
    private long index;

    @Override
    public String createId() {
        String id = seed + index++;
        return prefix + UUID.nameUUIDFromBytes(id.getBytes());
    }
}
