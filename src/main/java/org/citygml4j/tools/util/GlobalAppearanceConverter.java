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

import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.appearance.*;
import org.citygml4j.core.model.core.*;
import org.citygml4j.core.model.deprecated.appearance.DeprecatedPropertiesOfParameterizedTexture;
import org.citygml4j.core.model.deprecated.appearance.TextureAssociationReference;
import org.citygml4j.core.util.reference.DefaultReferenceResolver;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.gml.model.base.AbstractGML;
import org.xmlobjects.gml.model.base.AbstractInlineOrByReferenceProperty;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.model.Child;
import org.xmlobjects.util.copy.CopyBuilder;

import java.util.*;
import java.util.stream.Collectors;

public class GlobalAppearanceConverter {
    private final CityGMLVersion version;
    private final Map<String, List<AbstractSurfaceData>> targets = new HashMap<>();
    private final Set<String> processedTemplates = new HashSet<>();
    private final CopyBuilder copyBuilder = new CopyBuilder().failOnError(true);
    private final CityModel cityModel = new CityModel();
    private final Map<String, Integer> counter = new TreeMap<>();
    private final String ID = "id";

    private Mode mode = Mode.TOPLEVEL;
    private Map<String, AbstractGeometry> templates;

    public enum Mode {
        TOPLEVEL,
        CHILD;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private GlobalAppearanceConverter(CityGMLVersion version) {
        this.version = version;
    }

    public static GlobalAppearanceConverter of(List<Appearance> appearances, CityGMLVersion version) {
        GlobalAppearanceConverter converter = new GlobalAppearanceConverter(version);
        converter.preprocess(appearances);
        return converter;
    }

    public GlobalAppearanceConverter withMode(Mode mode) {
        this.mode = mode;
        return this;
    }

    public GlobalAppearanceConverter withTemplateGeometries(Map<String, AbstractGeometry> templates) {
        this.templates = templates;
        return this;
    }

    public void convertGlobalAppearance(AbstractFeature feature) {
        AppearanceProcessor processor = new AppearanceProcessor(feature);
        feature.accept(processor);
    }

    public boolean hasGlobalAppearances() {
        return cityModel.isSetAppearanceMembers();
    }

    public List<Appearance> getGlobalAppearances() {
        return cityModel.getAppearanceMembers().stream()
                .map(AbstractAppearanceProperty::getObject)
                .filter(Objects::nonNull)
                .filter(Appearance.class::isInstance)
                .map(Appearance.class::cast)
                .collect(Collectors.toList());
    }

    public Map<String, Integer> getCounter() {
        return counter;
    }

    private void preprocess(List<Appearance> appearances) {
        if (!appearances.isEmpty()) {
            ObjectWalker preprocessor = new ObjectWalker() {
                private int id;

                @Override
                public void visit(AbstractFeature feature) {
                    feature.getLocalProperties().set(ID, id++);
                }

                @Override
                public void visit(AbstractInlineOrByReferenceProperty<?> property) {
                    if (property.isSetReferencedObject()) {
                        Child child = property.getObject();
                        property.setInlineObjectIfValid(copyBuilder.shallowCopy(child));
                        property.setHref(null);
                    }

                    super.visit(property);
                }

                @Override
                public void visit(ParameterizedTexture texture) {
                    if (texture.hasDeprecatedProperties()) {
                        DeprecatedPropertiesOfParameterizedTexture properties = texture.getDeprecatedProperties();
                        Iterator<TextureAssociationReference> iterator = properties.getTargets().iterator();
                        while (iterator.hasNext()) {
                            TextureAssociationReference reference = iterator.next();
                            if (reference.isSetReferencedObject()) {
                                TextureAssociation copy = copyBuilder.shallowCopy(reference.getReferencedObject());
                                texture.getTextureParameterizations().add(new TextureAssociationProperty(copy));
                                iterator.remove();
                            } else if (version != CityGMLVersion.v3_0 && reference.getURI() != null) {
                                targets.computeIfAbsent(
                                        CityObjects.getIdFromReference(reference.getURI()),
                                        v -> new ArrayList<>()).add(texture);
                            }
                        }
                    }

                    for (TextureAssociationProperty property : texture.getTextureParameterizations()) {
                        GeometryReference reference = getGeometryReference(property);
                        if (reference != null && reference.getHref() != null) {
                            targets.computeIfAbsent(
                                    CityObjects.getIdFromReference(reference.getHref()),
                                    v -> new ArrayList<>()).add(texture);
                        }
                    }

                    super.visit(texture);
                }

                @Override
                public void visit(GeoreferencedTexture texture) {
                    for (GeometryReference reference : texture.getTargets()) {
                        if (reference.getHref() != null) {
                            targets.computeIfAbsent(
                                    CityObjects.getIdFromReference(reference.getHref()),
                                    v -> new ArrayList<>()).add(texture);
                        }
                    }

                    super.visit(texture);
                }

                @Override
                public void visit(X3DMaterial material) {
                    for (GeometryReference reference : material.getTargets()) {
                        if (reference.getHref() != null) {
                            targets.computeIfAbsent(
                                    CityObjects.getIdFromReference(reference.getHref()),
                                    v -> new ArrayList<>()).add(material);
                        }
                    }

                    super.visit(material);
                }
            };

            DefaultReferenceResolver.newInstance().resolveReferences(appearances);
            appearances.forEach(preprocessor::visit);
        }
    }

    private GeometryReference getGeometryReference(TextureAssociationProperty property) {
        return property.getObject() != null && property.getObject().getTarget() != null ?
                property.getObject().getTarget() :
                null;
    }

    private void count(Object object, Map<String, Integer> counter) {
        counter.merge(object.getClass().getSimpleName(), 1, Integer::sum);
    }

    private class AppearanceProcessor extends ObjectWalker {
        private final AbstractCityObject topLevelFeature;

        AppearanceProcessor(AbstractFeature feature) {
            topLevelFeature = feature instanceof AbstractCityObject ?
                    (AbstractCityObject) feature :
                    feature.getParent(AbstractCityObject.class);
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            if (templates != null && implicitGeometry.getRelativeGeometry() != null) {
                AbstractGeometry template = templates.get(implicitGeometry.getRelativeGeometry().isSetInlineObject() ?
                        implicitGeometry.getRelativeGeometry().getObject().getId() :
                        CityObjects.getIdFromReference(implicitGeometry.getRelativeGeometry().getHref()));

                if (template != null) {
                    if (processedTemplates.add(template.getId())) {
                        template.accept(this);
                    }

                    if (version == CityGMLVersion.v3_0) {
                        ImplicitGeometry other = template.getParent(ImplicitGeometry.class);
                        if (other.isSetAppearances()) {
                            boolean isInline = implicitGeometry.getRelativeGeometry().isSetInlineObject();
                            for (AbstractAppearanceProperty property : other.getAppearances()) {
                                if (property.isSetInlineObject()
                                        && property.getObject().hasLocalProperties()
                                        && property.getObject().getLocalProperties().contains(ID)) {
                                    implicitGeometry.getAppearances().add(isInline ?
                                            new AbstractAppearanceProperty(property.getObject()) :
                                            new AbstractAppearanceProperty("#" + property.getObject().getId()));
                                }
                            }
                        }
                    }
                }
            } else {
                super.visit(implicitGeometry);
            }
        }

        @Override
        public void visit(AbstractGeometry geometry) {
            if (geometry.getId() != null) {
                List<AbstractSurfaceData> sources = targets.remove(geometry.getId());
                if (sources != null) {
                    for (AbstractSurfaceData source : sources) {
                        AbstractGML target = getTargetObject(geometry);
                        if (target != null) {
                            convertAppearance(target, source, geometry);
                        }
                    }
                }
            }
        }

        @Override
        public void visit(GeometryProperty<?> property) {
            if (property.isSetInlineObject()) {
                super.visit(property);
            }
        }

        private AbstractGML getTargetObject(AbstractGeometry geometry) {
            ImplicitGeometry implicitGeometry = geometry.getParent(ImplicitGeometry.class);
            if (implicitGeometry != null) {
                return version == CityGMLVersion.v3_0 ? implicitGeometry : cityModel;
            } else if (mode == Mode.TOPLEVEL) {
                return topLevelFeature != null ? topLevelFeature : cityModel;
            } else {
                AbstractCityObject cityObject = geometry.getParent(AbstractCityObject.class);
                return cityObject != null ? cityObject : cityModel;
            }
        }

        private void convertAppearance(AbstractGML target, AbstractSurfaceData source, AbstractGeometry geometry) {
            Appearance appearance = source.getParent(Appearance.class);
            AbstractSurfaceData surfaceData = getOrCreateSurfaceData(target, appearance, source);
            if (surfaceData instanceof ParameterizedTexture) {
                ParameterizedTexture texture = (ParameterizedTexture) source;
                for (TextureAssociationProperty property : texture.getTextureParameterizations()) {
                    GeometryReference reference = getGeometryReference(property);
                    if (reference != null
                            && reference.getHref() != null
                            && CityObjects.getIdFromReference(reference.getHref()).equals(geometry.getId())) {
                        ((ParameterizedTexture) surfaceData).getTextureParameterizations().add(property);
                    }
                }
            } else if (surfaceData instanceof X3DMaterial) {
                ((X3DMaterial) surfaceData).getTargets().add(new GeometryReference("#" + geometry.getId()));
            } else if (surfaceData instanceof GeoreferencedTexture) {
                ((GeoreferencedTexture) surfaceData).getTargets().add(new GeometryReference("#" + geometry.getId()));
            }
        }

        private Appearance getOrCreateAppearance(AbstractGML target, Appearance globalAppearance) {
            List<AbstractAppearanceProperty> appearances;
            if (target instanceof AbstractCityObject) {
                appearances = ((AbstractCityObject) target).getAppearances();
            } else if (target instanceof ImplicitGeometry) {
                appearances = ((ImplicitGeometry) target).getAppearances();
            } else {
                appearances = cityModel.getAppearanceMembers();
            }

            for (AbstractAppearanceProperty property : appearances) {
                if (property.getObject() instanceof Appearance appearance) {
                    if (globalAppearance.getLocalProperties().getAndCompare(ID, appearance.getLocalProperties().get(ID))) {
                        return appearance;
                    }
                }
            }

            Appearance appearance = copyBuilder.shallowCopy(globalAppearance);
            appearance.setId(target instanceof ImplicitGeometry ? CityObjects.createId() : null);
            appearance.setSurfaceData(null);
            appearance.setLocalProperties(null);
            appearance.getLocalProperties().set(ID, globalAppearance.getLocalProperties().get(ID));
            appearances.add(new AbstractAppearanceProperty(appearance));

            if (!(target instanceof CityModel)) {
                count(appearance, counter);
            }

            return appearance;
        }

        private AbstractSurfaceData getOrCreateSurfaceData(AbstractGML target, Appearance globalAppearance, AbstractSurfaceData globalSurfaceData) {
            Appearance appearance = getOrCreateAppearance(target, globalAppearance);
            for (AbstractSurfaceDataProperty property : appearance.getSurfaceData()) {
                if (property.getObject() != null) {
                    AbstractSurfaceData surfaceData = property.getObject();
                    if (globalSurfaceData.getLocalProperties().getAndCompare(ID, surfaceData.getLocalProperties().get(ID))) {
                        return surfaceData;
                    }
                }
            }

            AbstractSurfaceData surfaceData = copyBuilder.shallowCopy(globalSurfaceData);
            surfaceData.setId(null);
            surfaceData.setLocalProperties(null);
            surfaceData.getLocalProperties().set(ID, globalSurfaceData.getLocalProperties().get(ID));
            appearance.getSurfaceData().add(new AbstractSurfaceDataProperty(surfaceData));

            if (surfaceData instanceof ParameterizedTexture) {
                ((ParameterizedTexture) surfaceData).setTextureParameterizations(null);
            } else if (surfaceData instanceof X3DMaterial) {
                ((X3DMaterial) surfaceData).setTargets(null);
            } else if (surfaceData instanceof GeoreferencedTexture) {
                ((GeoreferencedTexture) surfaceData).setTargets(null);
            }

            if (!(target instanceof CityModel)) {
                count(surfaceData, counter);
            }

            return surfaceData;
        }
    }
}
