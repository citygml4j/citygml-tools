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

import org.citygml4j.core.model.common.GeometryInfo;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.AbstractSpaceBoundary;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.gml.model.GMLObject;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.gml.util.reference.ReferenceResolver;
import org.xmlobjects.model.Child;

import java.util.*;

public class CrossLodReferenceResolver {
    private final CrossLodReferenceCollector lodReferenceCollector = new CrossLodReferenceCollector();
    private final GeometryCopyBuilder copyBuilder = GeometryCopyBuilder.newInstance().copyAppearance(true);
    private final Map<Mode, Integer> counter = new HashMap<>();

    private Mode mode = Mode.RESOLVE;

    public enum Mode {
        RESOLVE,
        REMOVE_LOD4_REFERENCES
    }

    private CrossLodReferenceResolver() {
    }

    public static CrossLodReferenceResolver newInstance() {
        return new CrossLodReferenceResolver();
    }

    public CrossLodReferenceResolver withGlobalAppearanceHelper(AppearanceHelper globalAppearanceHelper) {
        copyBuilder.withGlobalAppearanceHelper(globalAppearanceHelper);
        return this;
    }

    public CrossLodReferenceResolver withMode(Mode mode) {
        this.mode = mode;
        return this;
    }

    public CrossLodReferenceResolver copyAppearance(boolean copyAppearance) {
        copyBuilder.copyAppearance(copyAppearance);
        return this;
    }

    public Map<Mode, Integer> getCounter() {
        return counter;
    }

    public void resolveCrossLodReferences(AbstractFeature feature, ReferenceResolver referenceResolver) {
        referenceResolver.resolveReferences(feature);
        resolveCrossLodReferences(feature);
    }

    public void resolveCrossLodReferences(AbstractFeature feature) {
        Map<AbstractGeometry, List<GeometryProperty<?>>> references = lodReferenceCollector.process(feature);
        if (!references.isEmpty()) {
            if (mode == Mode.RESOLVE) {
                copyBuilder.withLocalAppearanceHelper(AppearanceHelper.of(feature));
                for (Map.Entry<AbstractGeometry, List<GeometryProperty<?>>> entry : references.entrySet()) {
                    GeometryProperty<?> targetProperty = getTargetProperty(entry.getValue());
                    AbstractGeometry geometry = copyBuilder.copy(entry.getKey(), feature);

                    targetProperty.setInlineObjectIfValid(geometry);
                    targetProperty.setHref(null);
                    counter.merge(Mode.RESOLVE, 1, Integer::sum);

                    if (entry.getValue().size() > 1) {
                        entry.getValue().stream()
                                .filter(property -> property != targetProperty)
                                .forEach(property -> {
                                    property.setHref("#" + geometry.getId());
                                    counter.merge(Mode.RESOLVE, 1, Integer::sum);
                                });
                    }
                }
            } else {
                references.values().stream()
                        .flatMap(Collection::stream)
                        .forEach(property -> {
                            Child parent = property.getParent();
                            if (parent instanceof GMLObject) {
                                ((GMLObject) parent).unsetProperty(property);
                                counter.merge(Mode.REMOVE_LOD4_REFERENCES, 1, Integer::sum);
                            }
                        });
            }
        }

        lodReferenceCollector.clear();
    }

    private GeometryProperty<?> getTargetProperty(List<GeometryProperty<?>> properties) {
        if (properties.size() > 1) {
            for (GeometryProperty<?> property : properties) {
                if (property.getParent(AbstractSpaceBoundary.class) != null) {
                    return property;
                }
            }
        }

        return properties.get(0);
    }

    private class CrossLodReferenceCollector extends ObjectWalker {
        private final Map<AbstractGeometry, List<GeometryProperty<?>>> references = new IdentityHashMap<>();
        private final Map<GeometryProperty<?>, Integer> propertiesByLod = new IdentityHashMap<>();

        public Map<AbstractGeometry, List<GeometryProperty<?>>> process(AbstractFeature feature) {
            GeometryInfo geometryInfo = feature.getGeometryInfo(true);
            for (int lod : geometryInfo.getLods()) {
                geometryInfo.getGeometries(lod).forEach(property -> propertiesByLod.put(property, lod));
            }

            propertiesByLod.keySet().forEach(this::visit);
            return references;
        }

        public void clear() {
            references.clear();
            propertiesByLod.clear();
        }

        @Override
        public void visit(GeometryProperty<?> property) {
            if (property.isSetReferencedObject() && property.getHref() != null) {
                Integer lod = getLod(property);
                Integer otherLod = getLod(property.getObject().getParent(GeometryProperty.class));
                if (lod != null && otherLod != null && !lod.equals(otherLod)) {
                    if (mode == Mode.RESOLVE || (mode == Mode.REMOVE_LOD4_REFERENCES && otherLod == 4)) {
                        references.computeIfAbsent(property.getObject(), v -> new ArrayList<>()).add(property);
                    }
                }
            }

            super.visit(property);
        }

        private Integer getLod(GeometryProperty<?> property) {
            Integer lod = null;
            if (property != null) {
                do {
                    lod = propertiesByLod.get(property);
                } while (lod == null && (property = property.getParent(GeometryProperty.class)) != null);
            }

            return lod;
        }
    }
}
