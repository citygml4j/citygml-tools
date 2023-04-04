/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2023 Claus Nagel <claus.nagel@gmail.com>
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

import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GlobalObjects {
    public static final String NAME = "name";

    public enum Type {
        APPEARANCE,
        CITY_OBJECT_GROUP,
        IMPLICIT_GEOMETRY;
    }

    private final List<Appearance> appearances = new ArrayList<>();
    private final List<CityObjectGroup> cityObjectGroups = new ArrayList<>();
    private final Map<String, AbstractGeometry> templateGeometries = new HashMap<>();

    GlobalObjects() {
    }

    public List<Appearance> getAppearances() {
        return appearances;
    }

    public List<CityObjectGroup> getCityObjectGroups() {
        return cityObjectGroups;
    }

    public Map<String, AbstractGeometry> getTemplateGeometries() {
        return templateGeometries;
    }

    void add(Appearance appearance, QName name) {
        appearance.getLocalProperties().set(NAME, name);
        appearances.add(appearance);
    }

    void add(CityObjectGroup cityObjectGroup, QName name) {
        cityObjectGroup.getLocalProperties().set(NAME, name);
        cityObjectGroups.add(cityObjectGroup);
    }

    void add(ImplicitGeometry implicitGeometry) {
        if (implicitGeometry.getRelativeGeometry() != null
                && implicitGeometry.getRelativeGeometry().isSetInlineObject()
                && implicitGeometry.getRelativeGeometry().getObject().getId() != null) {
            AbstractGeometry template = implicitGeometry.getRelativeGeometry().getObject();
            templateGeometries.put(template.getId(), template);
        }
    }
}
