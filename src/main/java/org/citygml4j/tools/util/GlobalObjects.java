/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.util;

import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.core.AbstractAppearanceProperty;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.xmlobjects.copy.CopyContext;
import org.xmlobjects.copy.Copyable;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;

import javax.xml.namespace.QName;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GlobalObjects implements Copyable<GlobalObjects> {
    public static final String NAME = "name";
    public static final String TEMPLATE_LOD = "lod";

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

    @Override
    public void shallowCopyTo(GlobalObjects dest, CopyContext context) {
        dest.appearances.addAll(appearances);
        dest.cityObjectGroups.addAll(cityObjectGroups);
        dest.templateGeometries.putAll(templateGeometries);
    }

    @Override
    public void deepCopyTo(GlobalObjects dest, CopyContext context) {
        appearances.forEach(appearance -> dest.appearances.add(context.deepCopy(appearance)));
        cityObjectGroups.forEach(cityObjectGroup -> dest.cityObjectGroups.add(context.deepCopy(cityObjectGroup)));
        templateGeometries.forEach((key, value) -> dest.templateGeometries.put(key, context.deepCopy(value)));
    }
}
