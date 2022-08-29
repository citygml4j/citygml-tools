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

import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.visitor.ObjectWalker;
import org.citygml4j.tools.option.CounterOption;
import org.citygml4j.tools.option.IdOption;
import org.citygml4j.tools.option.TypeNamesOption;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.module.citygml.CityObjectGroupModule;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;

import javax.xml.namespace.QName;
import java.util.*;

public class SubsetFilter {
    private final SkippedFeatureProcessor skippedFeatureProcessor = new SkippedFeatureProcessor();
    private final TemplatesProcessor templatesProcessor = new TemplatesProcessor();
    private final Map<String, AbstractGeometry> templates = new HashMap<>();
    private final Map<String, Integer> counter = new TreeMap<>();
    private final String TEMPLATE_ASSIGNED = "templateAssigned";

    private AppearanceRemover appearanceRemover;
    private CityObjectGroupRemover groupRemover;
    private Set<QName> typeNames;
    private Set<String> ids;
    private BoundingBoxFilter boundingBoxFilter;
    private boolean invert;
    private CounterOption counterOption;
    private boolean removeGroupMembers;

    private long count;
    private long index;

    private SubsetFilter() {
    }

    public static SubsetFilter newInstance() {
        return new SubsetFilter();
    }

    public SubsetFilter withGlobalObjects(GlobalObjects globalObjects) {
        if (globalObjects != null) {
            appearanceRemover = AppearanceRemover.of(globalObjects.getAppearances());
            groupRemover = CityObjectGroupRemover.of(globalObjects.getCityObjectGroups());
            preprocessImplicitGeometries(globalObjects.getTemplateGeometries());
        }

        return this;
    }

    public SubsetFilter withTypeNamesFilter(TypeNamesOption typeNamesOption, CityGMLContext context) {
        this.typeNames = typeNamesOption != null ? typeNamesOption.getTypeNames(context) : null;
        return this;
    }

    public SubsetFilter withIdFilter(IdOption idOption) {
        this.ids = idOption != null ? idOption.getIds() : null;
        return this;
    }

    public BoundingBoxFilter getBoundingBoxFilter() {
        return boundingBoxFilter;
    }

    public SubsetFilter withBoundingBoxFilter(BoundingBoxFilter boundingBoxFilter) {
        this.boundingBoxFilter = boundingBoxFilter;
        return this;
    }

    public SubsetFilter invertFilterCriteria(boolean invert) {
        this.invert = invert;
        return this;
    }

    public SubsetFilter withCounterOption(CounterOption counterOption) {
        this.counterOption = counterOption;
        return this;
    }

    public SubsetFilter removeGroupMembers(boolean removeGroupMembers) {
        this.removeGroupMembers = removeGroupMembers;
        return this;
    }

    public Map<String, Integer> getCounter() {
        return counter;
    }

    public boolean filter(AbstractFeature feature, QName name, String prefix) {
        boolean keep = true;
        if (typeNames != null) {
            keep = typeNames.contains(name);
        }

        if (keep && ids != null) {
            keep = feature.getId() != null && ids.contains(feature.getId());
        }

        if (keep && boundingBoxFilter != null) {
            if (!templates.isEmpty()) {
                templatesProcessor.preprocess(feature);
            }

            keep = boundingBoxFilter.filter(feature);
        }

        if (invert) {
            keep = !keep;
        }

        if (keep && counterOption != null) {
            if (index < counterOption.getStartIndex()) {
                index++;
                keep = false;
            } else {
                count++;
            }

            keep = keep && count <= counterOption.getCount();
        }

        if (keep) {
            templatesProcessor.postprocess(feature);
            counter.merge(prefix + ":" + name.getLocalPart(), 1, Integer::sum);
        } else {
            feature.accept(skippedFeatureProcessor);
        }

        return keep;
    }

    public void postprocess() {
        postprocessGroups();
        postprocessImplicitGeometries();
        if (appearanceRemover != null) {
            appearanceRemover.postprocess();
        }
    }

    private void postprocessGroups() {
        if (groupRemover != null && groupRemover.hasCityObjectGroups()) {
            QName name = null;
            if (typeNames != null) {
                for (QName typeName : typeNames) {
                    if ("CityObjectGroup".equals(typeName.getLocalPart())
                            && CityGMLModules.isCityGMLNamespace(typeName.getNamespaceURI())) {
                        name = typeName;
                        break;
                    }
                }
            }

            if (name == null) {
                name = new QName(CityObjectGroupModule.v3_0.getNamespaceURI(), "CityObjectGroup");
            }

            for (CityObjectGroup group : groupRemover.getCityObjectGroups()) {
                if (group.isSetGroupMembers()) {
                    if (!filter(group, name, "grp")) {
                        group.setGroupMembers(null);
                    }
                }
            }

            groupRemover.postprocess();
        }
    }

    private void preprocessImplicitGeometries(List<AbstractGeometry> templates) {
        for (AbstractGeometry template : templates) {
            if (template.getId() != null) {
                this.templates.put(template.getId(), template);
            }
        }
    }

    private void postprocessImplicitGeometries() {
        for (AbstractGeometry template : templates.values()) {
            if (!template.getLocalProperties().contains(TEMPLATE_ASSIGNED)) {
                template.accept(skippedFeatureProcessor);
            }
        }
    }

    private class SkippedFeatureProcessor extends ObjectWalker {

        @Override
        public void visit(AbstractFeature feature) {
            if (removeGroupMembers && groupRemover != null) {
                groupRemover.removeMember(feature);
            }
        }

        @Override
        public void visit(AbstractGeometry geometry) {
            if (appearanceRemover != null) {
                appearanceRemover.removeTarget(geometry);
            }
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
        }
    }

    private class TemplatesProcessor extends ObjectWalker {
        private boolean preprocess;

        public void preprocess(AbstractFeature feature) {
            preprocess = true;
            feature.accept(this);
        }

        public void postprocess(AbstractFeature feature) {
            preprocess = false;
            feature.accept(this);
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            GeometryProperty<?> property = implicitGeometry.getRelativeGeometry();
            if (property != null) {
                if (preprocess) {
                    if (property.getObject() == null && property.getHref() != null) {
                        property.setReferencedObjectIfValid(templates.get(
                                CityObjects.getIdFromReference(property.getHref())));
                    }
                } else {
                    if (property.isSetInlineObject() && property.getObject().getId() != null) {
                        AbstractGeometry template = templates.get(property.getObject().getId());
                        if (template.getLocalProperties().contains(TEMPLATE_ASSIGNED)) {
                            property.setInlineObject(null);
                            property.setHref("#" + template.getId());
                        } else {
                            template.getLocalProperties().set(TEMPLATE_ASSIGNED, true);
                        }
                    } else if (property.getHref() != null) {
                        AbstractGeometry template = templates.get(CityObjects.getIdFromReference(property.getHref()));
                        if (!template.getLocalProperties().contains(TEMPLATE_ASSIGNED)) {
                            property.setInlineObjectIfValid(template);
                            property.setHref(null);
                            template.getLocalProperties().set(TEMPLATE_ASSIGNED, true);
                        }
                    }
                }
            }
        }
    }
}
