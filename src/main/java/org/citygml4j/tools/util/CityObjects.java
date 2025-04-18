/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2025 Claus Nagel <claus.nagel@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.citygml4j.tools.util;

import org.citygml4j.core.model.core.AbstractFeature;
import org.xmlobjects.gml.model.base.AbstractGML;
import org.xmlobjects.gml.util.id.DefaultIdCreator;

public class CityObjects {

    private CityObjects() {
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
