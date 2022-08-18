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

import org.citygml4j.core.model.appearance.*;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.cityobjectgroup.RoleProperty;
import org.citygml4j.core.model.core.AbstractCityObjectReference;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.visitor.ObjectWalker;
import org.citygml4j.tools.option.IdOption;
import org.citygml4j.tools.option.TypeNamesOption;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.module.citygml.CityObjectGroupModule;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.gml.model.geometry.aggregates.MultiSurface;
import org.xmlobjects.gml.model.geometry.primitives.AbstractSurface;

import javax.xml.namespace.QName;
import java.util.*;

public class SubsetProcessor {
    private final SkippedFeatureProcessor skippedFeatureProcessor = new SkippedFeatureProcessor();
    private final TemplatesProcessor templatesProcessor = new TemplatesProcessor();
    private final Map<String, List<AbstractCityObjectReference>> groupParents = new HashMap<>();
    private final Map<String, List<RoleProperty>> groupMembers = new HashMap<>();
    private final Map<String, List<TextureAssociationProperty>> parameterizedTextures = new HashMap<>();
    private final Map<String, List<GeometryReference>> georeferencedTextures = new HashMap<>();
    private final Map<String, List<GeometryReference>> materials = new HashMap<>();
    private final Map<String, AbstractGeometry> templates = new HashMap<>();
    private final Map<String, Integer> counter = new TreeMap<>();
    private final String TEMPLATE_ASSIGNED = "templateAssigned";

    private GlobalObjects globalObjects = new GlobalObjects();
    private Set<QName> typeNames;
    private Set<String> ids;
    private BoundingBoxFilter boundingBoxFilter;
    private boolean invert;
    private boolean removeGroupMembers;

    private SubsetProcessor() {
    }

    public static SubsetProcessor newInstance() {
        return new SubsetProcessor();
    }

    public SubsetProcessor withGlobalObjects(GlobalObjects globalObjects) {
        if (globalObjects != null) {
            this.globalObjects = globalObjects;
            preprocessGroups();
            preprocessImplicitGeometries();
            preprocessAppearances();
        }

        return this;
    }

    public SubsetProcessor withTypeNamesFilter(TypeNamesOption typeNamesOption, CityGMLContext context) {
        this.typeNames = typeNamesOption != null ? typeNamesOption.getTypeNames(context) : null;
        return this;
    }

    public SubsetProcessor withIdFilter(IdOption idOption) {
        this.ids = idOption != null ? idOption.getIds() : null;
        return this;
    }

    public BoundingBoxFilter getBoundingBoxFilter() {
        return boundingBoxFilter;
    }

    public SubsetProcessor withBoundingBoxFilter(BoundingBoxFilter boundingBoxFilter) {
        this.boundingBoxFilter = boundingBoxFilter;
        return this;
    }

    public SubsetProcessor invertFilterCriteria(boolean invert) {
        this.invert = invert;
        return this;
    }

    public SubsetProcessor removeGroupMembers(boolean removeGroupMembers) {
        this.removeGroupMembers = removeGroupMembers;
        return this;
    }

    public Map<String, Integer> getCounter() {
        return counter;
    }

    public boolean filter(AbstractFeature feature, QName name, String prefix) {
        boolean filter = true;
        if (typeNames != null) {
            filter = typeNames.contains(name);
        }

        if (filter && ids != null) {
            filter = feature.getId() != null && ids.contains(feature.getId());
        }

        if (filter && boundingBoxFilter != null) {
            if (!globalObjects.getImplicitGeometries().isEmpty()) {
                templatesProcessor.preprocess(feature);
            }

            filter = boundingBoxFilter.filter(feature);
        }

        if (invert) {
            filter = !filter;
        }

        if (filter) {
            templatesProcessor.postprocess(feature);
            counter.merge(prefix + ":" + name.getLocalPart(), 1, Integer::sum);
        } else {
            feature.accept(skippedFeatureProcessor);
        }

        return filter;
    }

    public void postprocess() {
        postprocessGroups();
        postprocessImplicitGeometries();
        postprocessAppearances();
    }

    private void preprocessGroups() {
        int capacity = Math.min(10, globalObjects.getCityObjectGroups().size());

        for (CityObjectGroup group : globalObjects.getCityObjectGroups()) {
            if (group.getGroupParent() != null && group.getGroupParent().getHref() != null) {
                String id = CityObjects.getIdFromReference(group.getGroupParent().getHref());
                groupParents.computeIfAbsent(id, v -> new ArrayList<>(capacity)).add(group.getGroupParent());
            }

            if (group.isSetGroupMembers()) {
                for (RoleProperty property : group.getGroupMembers()) {
                    if (property.getObject() != null
                            && property.getObject().getGroupMember() != null
                            && property.getObject().getGroupMember().getHref() != null) {
                        String id = CityObjects.getIdFromReference(property.getObject().getGroupMember().getHref());
                        groupMembers.computeIfAbsent(id, v -> new ArrayList<>(capacity)).add(property);
                    }
                }
            }
        }
    }

    private void postprocessGroups() {
        if (!globalObjects.getCityObjectGroups().isEmpty()) {
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

            for (CityObjectGroup group : globalObjects.getCityObjectGroups()) {
                if (group.isSetGroupMembers()) {
                    if (!filter(group, name, "grp")) {
                        group.setGroupMembers(null);
                    }
                }
            }

            globalObjects.getCityObjectGroups().removeIf(group -> !group.isSetGroupMembers());
        }
    }

    private void preprocessImplicitGeometries() {
        for (AbstractGeometry template : globalObjects.getTemplateGeometries()) {
            if (template.getId() != null) {
                templates.put(template.getId(), template);
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

    private void preprocessAppearances() {
        int capacity = (int) Math.min(10, globalObjects.getAppearances().stream()
                .map(Appearance::getTheme)
                .distinct().count());

        for (Appearance appearance : globalObjects.getAppearances()) {
            appearance.accept(new ObjectWalker() {
                @Override
                public void visit(ParameterizedTexture texture) {
                    if (texture.isSetTextureParameterizations()) {
                        for (TextureAssociationProperty property : texture.getTextureParameterizations()) {
                            if (property.getObject() != null
                                    && property.getObject().getTarget() != null
                                    && property.getObject().getTarget().getHref() != null) {
                                String id = CityObjects.getIdFromReference(property.getObject().getTarget().getHref());
                                parameterizedTextures.computeIfAbsent(id, v -> new ArrayList<>(capacity)).add(property);
                            }
                        }
                    }
                }

                @Override
                public void visit(GeoreferencedTexture texture) {
                    if (texture.isSetTargets()) {
                        process(texture.getTargets(), georeferencedTextures);
                    }
                }

                @Override
                public void visit(X3DMaterial material) {
                    if (material.isSetTargets()) {
                        process(material.getTargets(), materials);
                    }
                }

                private void process(List<GeometryReference> references, Map<String, List<GeometryReference>> dest) {
                    for (GeometryReference reference : references) {
                        if (reference.getHref() != null) {
                            String id = CityObjects.getIdFromReference(reference.getHref());
                            dest.computeIfAbsent(id, v -> new ArrayList<>(capacity)).add(reference);
                        }
                    }
                }
            });
        }
    }

    private void postprocessAppearances() {
        if (!globalObjects.getAppearances().isEmpty()) {
            if (!skippedFeatureProcessor.getSurfaceDataIds().isEmpty()) {
                for (Appearance appearance : globalObjects.getAppearances()) {
                    appearance.getSurfaceData().removeIf(property -> property.getHref() != null
                            && skippedFeatureProcessor.surfaceDataIds.contains(
                            CityObjects.getIdFromReference(property.getHref())));
                }
            }

            globalObjects.getAppearances().removeIf(appearance -> !appearance.isSetSurfaceData());
        }
    }

    private class SkippedFeatureProcessor extends ObjectWalker {
        private final Set<String> surfaceDataIds = new HashSet<>();

        public Set<String> getSurfaceDataIds() {
            return surfaceDataIds;
        }

        @Override
        public void visit(AbstractFeature feature) {
            if (removeGroupMembers && feature.getId() != null) {
                List<AbstractCityObjectReference> references = groupParents.remove(feature.getId());
                if (references != null) {
                    references.forEach(reference -> reference.getParent(CityObjectGroup.class).setGroupParent(null));
                }

                List<RoleProperty> properties = groupMembers.remove(feature.getId());
                if (properties != null) {
                    for (RoleProperty property : properties) {
                        CityObjectGroup group = property.getParent(CityObjectGroup.class);
                        group.getGroupMembers().remove(property);
                        if (!group.isSetGroupMembers()) {
                            visit((AbstractFeature) group);
                        }
                    }
                }
            }
        }

        @Override
        public void visit(AbstractSurface surface) {
            process(surface);
            super.visit(surface);
        }

        @Override
        public void visit(MultiSurface multiSurface) {
            process(multiSurface);
            super.visit(multiSurface);
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
        }

        private void process(AbstractGeometry geometry) {
            if (geometry.getId() != null) {
                List<TextureAssociationProperty> properties = parameterizedTextures.remove(geometry.getId());
                if (properties != null) {
                    for (TextureAssociationProperty property : properties) {
                        ParameterizedTexture texture = property.getParent(ParameterizedTexture.class);
                        texture.getTextureParameterizations().remove(property);
                        if (!texture.isSetTextureParameterizations()) {
                            removeSurfaceData(texture);
                        }
                    }
                }

                List<GeometryReference> references = georeferencedTextures.remove(geometry.getId());
                if (references != null) {
                    for (GeometryReference reference : references) {
                        GeoreferencedTexture texture = reference.getParent(GeoreferencedTexture.class);
                        texture.getTargets().remove(reference);
                        if (!texture.isSetTargets()) {
                            removeSurfaceData(texture);
                        }
                    }
                }

                references = materials.remove(geometry.getId());
                if (references != null) {
                    for (GeometryReference reference : references) {
                        X3DMaterial material = reference.getParent(X3DMaterial.class);
                        material.getTargets().remove(reference);
                        if (!material.isSetTargets()) {
                            removeSurfaceData(material);
                        }
                    }
                }
            }
        }

        private void removeSurfaceData(AbstractSurfaceData surfaceData) {
            Appearance appearance = surfaceData.getParent(Appearance.class);
            if (appearance.getSurfaceData().remove(surfaceData.getParent(AbstractSurfaceDataProperty.class))
                    && surfaceData.getId() != null) {
                surfaceDataIds.add(surfaceData.getId());
            }
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
