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
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.gml.model.GMLObject;
import org.xmlobjects.gml.model.base.AbstractInlineOrByReferenceProperty;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.aggregates.MultiSurface;
import org.xmlobjects.gml.model.geometry.primitives.AbstractSurface;

import java.util.*;

public class AppearanceRemover {
    private final List<Appearance> appearances;
    private final Map<String, List<TextureAssociationProperty>> parameterizedTextures = new HashMap<>();
    private final Map<String, List<GeometryReference>> georeferencedTextures = new HashMap<>();
    private final Map<String, List<GeometryReference>> materials = new HashMap<>();
    private final TargetProcessor targetProcessor = new TargetProcessor();
    private final Set<String> surfaceDataIds = new HashSet<>();

    private AppearanceRemover(List<Appearance> appearances) {
        this.appearances = appearances;
    }

    public static AppearanceRemover of(List<Appearance> appearances) {
        return new AppearanceRemover(appearances).preprocess();
    }

    public static AppearanceRemover of(AbstractFeature feature) {
        List<Appearance> appearances = new ArrayList<>();
        feature.accept(new ObjectWalker() {
            @Override
            public void visit(Appearance appearance) {
                appearances.add(appearance);
            }
        });

        return of(appearances);
    }

    public List<Appearance> getAppearances() {
        return appearances;
    }

    public boolean hasAppearances() {
        return !appearances.isEmpty();
    }

    private AppearanceRemover preprocess() {
        if (!appearances.isEmpty()) {
            TargetCollector targetCollector = new TargetCollector();
            targetCollector.setCapacity((int) Math.min(10, appearances.stream()
                    .map(Appearance::getTheme)
                    .distinct().count()));

            appearances.forEach(appearance -> appearance.accept(targetCollector));
        }

        return this;
    }

    public void removeTarget(AbstractGeometry geometry) {
        if (!appearances.isEmpty()) {
            geometry.accept(targetProcessor);
        }
    }

    public void removeTarget(AbstractInlineOrByReferenceProperty<?> property) {
        if (!appearances.isEmpty()) {
            targetProcessor.visit(property);
        }
    }

    public void postprocess() {
        parameterizedTextures.clear();
        georeferencedTextures.clear();
        materials.clear();

        if (!surfaceDataIds.isEmpty()) {
            for (Appearance appearance : appearances) {
                appearance.getSurfaceData().removeIf(property -> property.getHref() != null
                        && surfaceDataIds.contains(
                        CityObjects.getIdFromReference(property.getHref())));
            }

            surfaceDataIds.clear();
        }

        Iterator<Appearance> iterator = appearances.iterator();
        while (iterator.hasNext()) {
            Appearance appearance = iterator.next();
            if (!appearance.isSetSurfaceData()) {
                iterator.remove();
                if (appearance.getParent() != null && appearance.getParent().getParent() instanceof GMLObject) {
                    GMLObject parent = (GMLObject) appearance.getParent().getParent();
                    parent.unsetProperty(appearance.getParent(), true);
                }
            }
        }
    }

    private class TargetProcessor extends ObjectWalker {
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

    private class TargetCollector extends ObjectWalker {
        private int capacity;

        void setCapacity(int capacity) {
            this.capacity = capacity;
        }

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
    }
}
