/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2024 Claus Nagel <claus.nagel@gmail.com>
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
import org.citygml4j.tools.option.CounterOptions;
import org.citygml4j.tools.option.IdOptions;
import org.citygml4j.tools.option.TypeNameOptions;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.module.citygml.CityObjectGroupModule;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;

import javax.xml.namespace.QName;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class SubsetFilter {
    private final SkippedFeatureProcessor skippedFeatureProcessor = new SkippedFeatureProcessor();
    private final TemplatesProcessor templatesProcessor = new TemplatesProcessor();
    private final Map<String, Integer> counter = new TreeMap<>();
    private final String TEMPLATE_ASSIGNED = "templateAssigned";

    private AppearanceRemover appearanceRemover;
    private CityObjectGroupRemover groupRemover;
    private Map<String, AbstractGeometry> templates;
    private Set<QName> typeNames;
    private Set<String> ids;
    private BoundingBoxFilter boundingBoxFilter;
    private boolean invert;
    private CounterOptions counterOptions;
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
            templates = globalObjects.getTemplateGeometries();
        }

        return this;
    }

    public SubsetFilter withTypeNamesFilter(TypeNameOptions typeNameOptions, CityGMLContext context) {
        this.typeNames = typeNameOptions != null ? typeNameOptions.getTypeNames(context) : null;
        return this;
    }

    public SubsetFilter withIdFilter(IdOptions idOptions) {
        this.ids = idOptions != null ? idOptions.getIds() : null;
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

    public SubsetFilter withCounterOption(CounterOptions counterOptions) {
        this.counterOptions = counterOptions;
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
            if (templates != null && !templates.isEmpty()) {
                templatesProcessor.preprocess(feature);
            }

            keep = boundingBoxFilter.filter(feature);
        }

        if (invert) {
            keep = !keep;
        }

        if (keep && counterOptions != null) {
            if (index < counterOptions.getStartIndex()) {
                index++;
                keep = false;
            } else {
                count++;
            }

            keep = keep && count <= counterOptions.getCount();
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

    private void postprocessImplicitGeometries() {
        if (templates != null && !templates.isEmpty()) {
            for (AbstractGeometry template : templates.values()) {
                if (!template.getLocalProperties().contains(TEMPLATE_ASSIGNED)) {
                    template.accept(skippedFeatureProcessor);
                }
            }
        }
    }

    private class SkippedFeatureProcessor extends ObjectWalker {

        @Override
        public void visit(AbstractFeature feature) {
            if (removeGroupMembers && groupRemover != null) {
                groupRemover.removeMembers(feature);
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
