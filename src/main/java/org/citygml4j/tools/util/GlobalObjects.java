/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2022 Claus Nagel <claus.nagel@gmail.com>
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
import org.xmlobjects.gml.model.geometry.GeometryProperty;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class GlobalObjects {
    public static final String NAME = "name";

    public enum Type {
        APPEARANCE,
        CITY_OBJECT_GROUP,
        IMPLICIT_GEOMETRY;

        static final EnumSet<Type> TOP_LEVEL_TYPES = EnumSet.of(APPEARANCE, CITY_OBJECT_GROUP);
    }

    private final List<Appearance> appearances = new ArrayList<>();
    private final List<CityObjectGroup> cityObjectGroups = new ArrayList<>();
    private final List<ImplicitGeometry> implicitGeometries = new ArrayList<>();

    GlobalObjects() {
    }

    public List<Appearance> getAppearances() {
        return appearances;
    }

    public List<CityObjectGroup> getCityObjectGroups() {
        return cityObjectGroups;
    }

    public List<ImplicitGeometry> getImplicitGeometries() {
        return implicitGeometries;
    }

    public List<AbstractGeometry> getTemplateGeometries() {
        return implicitGeometries.stream()
                .map(ImplicitGeometry::getRelativeGeometry)
                .filter(Objects::nonNull)
                .map(GeometryProperty::getObject)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    void add(Appearance appearance, QName name) {
        appearance.getLocalProperties().set(NAME, name);
        appearances.add(appearance);
    }

    void add(CityObjectGroup cityObjectGroup, QName name) {
        cityObjectGroup.getLocalProperties().set(NAME, name);
        cityObjectGroups.add(cityObjectGroup);
    }

    void add(ImplicitGeometry implicitGeometry, QName name) {
        implicitGeometry.getLocalProperties().set(NAME, name);
        implicitGeometries.add(implicitGeometry);
    }
}
