/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.util;

import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.core.AbstractAppearanceProperty;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GlobalObjectHelper {
    public static final String NAME = "name";
    public static final String TEMPLATE_LOD = "lod";

    private final List<Appearance> appearances = new ArrayList<>();
    private final List<CityObjectGroup> cityObjectGroups = new ArrayList<>();
    private final Map<String, AbstractGeometry> templateGeometries = new HashMap<>();

    GlobalObjectHelper() {
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

    void add(ImplicitGeometry implicitGeometry, boolean withTemplateAppearances) {
        add(implicitGeometry, 0, withTemplateAppearances);
    }

    void add(ImplicitGeometry implicitGeometry, int lod, boolean withTemplateAppearances) {
        if (implicitGeometry.getRelativeGeometry() != null) {
            if (implicitGeometry.getRelativeGeometry().isSetInlineObject()
                    && implicitGeometry.getRelativeGeometry().getObject().getId() != null) {
                AbstractGeometry template = implicitGeometry.getRelativeGeometry().getObject();
                template.getLocalProperties().set(TEMPLATE_LOD, lod);
                templateGeometries.put(template.getId(), template);
            }

            if (withTemplateAppearances && implicitGeometry.isSetAppearances()) {
                implicitGeometry.getAppearances().stream()
                        .map(AbstractAppearanceProperty::getObject)
                        .filter(Appearance.class::isInstance)
                        .map(Appearance.class::cast)
                        .forEach(appearances::add);
            }
        }
    }
}
