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
import org.xmlobjects.gml.util.id.DefaultIdCreator;
import org.xmlobjects.gml.util.reference.ReferenceResolver;
import org.xmlobjects.model.Child;
import org.xmlobjects.util.copy.CopyBuilder;

import java.util.*;
import java.util.stream.Collectors;

public class GlobalAppearanceConverter {
    private final CityGMLVersion version;
    private final Map<String, List<AbstractSurfaceData>> targets = new HashMap<>();
    private final AppearanceProcessor appearanceProcessor = new AppearanceProcessor();
    private final CopyBuilder copyBuilder = new CopyBuilder().failOnError(true);
    private final CityModel cityModel = new CityModel();
    private final Map<String, Integer> counter = new TreeMap<>();
    private final String ID = "id";

    private Mode mode = Mode.TOPLEVEL;

    public enum Mode {
        TOPLEVEL,
        CHILD;

        @Override
        public String toString() {
            return name().toLowerCase();
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

    public void convertGlobalAppearance(AbstractFeature feature) {
        feature.accept(appearanceProcessor.withTopLevelFeature(feature));
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
        ObjectWalker preprocessor = new ObjectWalker() {
            private final ReferenceResolver resolver = DefaultReferenceResolver.newInstance();
            private int id;

            @Override
            public void visit(AbstractFeature feature) {
                feature.getLocalProperties().set(ID, id++);
            }

            @Override
            public void visit(AbstractInlineOrByReferenceProperty<?> property) {
                if (property.getObject() == null && property.getHref() != null) {
                    Child child = resolver.resolveReference(property.getHref(), appearances);
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
                        TextureAssociation association = resolver.resolveReference(
                                reference.getHref(), TextureAssociation.class, appearances);
                        if (association != null) {
                            TextureAssociation copy = copyBuilder.shallowCopy(association);
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

        appearances.forEach(preprocessor::visit);
    }

    private GeometryReference getGeometryReference(TextureAssociationProperty property) {
        return property.getObject() != null
                && property.getObject().getTarget() != null ?
                property.getObject().getTarget() :
                null;
    }

    private void count(Object object, Map<String, Integer> counter) {
        counter.merge(object.getClass().getSimpleName(), 1, Integer::sum);
    }

    private class AppearanceProcessor extends ObjectWalker {
        private AbstractCityObject topLevelFeature;

        private AppearanceProcessor withTopLevelFeature(AbstractFeature feature) {
            this.topLevelFeature = feature instanceof AbstractCityObject ?
                    (AbstractCityObject) feature :
                    feature.getParent(AbstractCityObject.class);
            return this;
        }

        @Override
        public void visit(AbstractGeometry geometry) {
            if (geometry.getId() != null) {
                List<AbstractSurfaceData> sources = targets.remove(geometry.getId());
                if (sources != null) {
                    for (AbstractSurfaceData source : sources) {
                        Appearance appearance = source.getParent(Appearance.class);
                        AbstractGML target = getTargetObject(geometry);

                        AbstractSurfaceData surfaceData = getOrCreateSurfaceData(target, appearance, source);
                        if (surfaceData instanceof ParameterizedTexture) {
                            ParameterizedTexture texture = (ParameterizedTexture) source;
                            for (TextureAssociationProperty property : texture.getTextureParameterizations()) {
                                GeometryReference reference = getGeometryReference(property);
                                if (reference != null
                                        && reference.getHref() != null
                                        && CityObjects.getIdFromReference(reference.getHref()).equals(geometry.getId())) {
                                    ((ParameterizedTexture) surfaceData).getTextureParameterizations()
                                            .add(property);
                                }
                            }
                        } else if (surfaceData instanceof X3DMaterial) {
                            ((X3DMaterial) surfaceData).getTargets()
                                    .add(new GeometryReference("#" + geometry.getId()));
                        } else if (surfaceData instanceof GeoreferencedTexture) {
                            ((GeoreferencedTexture) surfaceData).getTargets()
                                    .add(new GeometryReference("#" + geometry.getId()));
                        }
                    }
                }
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
                if (property.getObject() instanceof Appearance) {
                    Appearance appearance = (Appearance) property.getObject();
                    if (globalAppearance.getLocalProperties().getAndCompare(ID, appearance.getLocalProperties().get(ID))) {
                        return appearance;
                    }
                }
            }

            Appearance appearance = copyBuilder.shallowCopy(globalAppearance);
            appearance.setId(DefaultIdCreator.getInstance().createId());
            appearance.setSurfaceData(null);
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
            surfaceData.setId(DefaultIdCreator.getInstance().createId());
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
