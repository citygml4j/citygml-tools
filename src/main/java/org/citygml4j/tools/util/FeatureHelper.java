/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.util;

import org.citygml4j.core.model.core.AbstractFeature;
import org.xmlobjects.gml.model.base.AbstractGML;
import org.xmlobjects.gml.util.id.DefaultIdCreator;

public class FeatureHelper {

    private FeatureHelper() {
    }

    public static String getObjectSignature(AbstractGML object) {
        return object.getClass().getSimpleName()
                + " '" + (object.getId() != null ? object.getId() : "unknown ID") + "'";
    }

    public static String getIdFromReference(String reference) {
        int index = reference.lastIndexOf("#");
        return index != -1 ? reference.substring(index + 1) : reference;
    }

    public static String createId() {
        return DefaultIdCreator.getInstance().createId();
    }

    public static String getOrCreateId(AbstractFeature feature) {
        if (feature.getId() == null) {
            feature.setId(createId());
        }

        return feature.getId();
    }
}
