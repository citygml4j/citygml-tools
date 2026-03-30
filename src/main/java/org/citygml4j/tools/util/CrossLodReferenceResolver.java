/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2026 Claus Nagel <claus.nagel@gmail.com>
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
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.copy.CopySession;
import org.xmlobjects.gml.model.GMLObject;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.model.Child;

import java.util.*;

public class CrossLodReferenceResolver {
    private final GeometryCopier geometryCopier = GeometryCopier.newInstance().copyAppearance(true);
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
        geometryCopier.withGlobalAppearanceHelper(globalAppearanceHelper);
        return this;
    }

    public CrossLodReferenceResolver withMode(Mode mode) {
        this.mode = mode;
        return this;
    }

    public Map<Mode, Integer> getCounter() {
        return counter;
    }

    public void resolveCrossLodReferences(AbstractFeature feature) {
        ReferenceCollector collector = new ReferenceCollector();
        Map<Integer, Map<AbstractGeometry, List<GeometryProperty<?>>>> references = collector.collect(feature);
        if (references.isEmpty()) {
            return;
        }

        if (mode == Mode.RESOLVE) {
            geometryCopier.withLocalAppearanceHelper(AppearanceHelper.of(feature));

            for (Map<AbstractGeometry, List<GeometryProperty<?>>> lod : references.values()) {
                try (CopySession session = geometryCopier.createSession()) {
                    for (Map.Entry<AbstractGeometry, List<GeometryProperty<?>>> entry : lod.entrySet()) {
                        boolean inline = !session.hasClone(entry.getKey());
                        AbstractGeometry clone = geometryCopier.copy(entry.getKey(), feature, session);

                        GeometryProperty<?> targetProperty = entry.getValue().get(0);
                        if (inline) {
                            targetProperty.setInlineObjectIfValid(clone);
                            targetProperty.setHref(null);
                        } else {
                            targetProperty.setReferencedObjectIfValid(clone);
                            targetProperty.setHref("#" + clone.getId());
                        }

                        counter.merge(Mode.RESOLVE, 1, Integer::sum);

                        if (entry.getValue().size() > 1) {
                            entry.getValue().stream()
                                    .skip(1)
                                    .forEach(property -> {
                                        property.setReferencedObjectIfValid(clone);
                                        property.setHref("#" + clone.getId());
                                        counter.merge(Mode.RESOLVE, 1, Integer::sum);
                                    });
                        }
                    }
                }
            }
        } else {
            for (Map<AbstractGeometry, List<GeometryProperty<?>>> lod : references.values()) {
                for (List<GeometryProperty<?>> properties : lod.values()) {
                    for (GeometryProperty<?> property : properties) {
                        Child parent = property.getParent();
                        if (parent instanceof GMLObject object) {
                            object.unsetProperty(property);
                            counter.merge(Mode.REMOVE_LOD4_REFERENCES, 1, Integer::sum);
                        }
                    }
                }
            }
        }
    }

    private class ReferenceCollector extends ObjectWalker {
        private final Map<Integer, Map<AbstractGeometry, List<GeometryProperty<?>>>> references = new HashMap<>();
        private final Map<GeometryProperty<?>, Integer> properties = new IdentityHashMap<>();

        public Map<Integer, Map<AbstractGeometry, List<GeometryProperty<?>>>> collect(AbstractFeature feature) {
            GeometryInfo geometryInfo = feature.getGeometryInfo(true);
            for (int lod : geometryInfo.getLods()) {
                for (GeometryProperty<?> property : geometryInfo.getGeometries(lod)) {
                    properties.put(property, lod);
                }
            }

            feature.accept(this);
            return references;
        }

        @Override
        public void visit(GeometryProperty<?> property) {
            if (property.isSetReferencedObject() && property.getHref() != null) {
                Integer lod = getLod(property);
                Integer targetLod = getLod(property.getObject().getParent(GeometryProperty.class));

                if (lod != null && targetLod != null && !lod.equals(targetLod)) {
                    if (mode == Mode.RESOLVE
                            || (mode == Mode.REMOVE_LOD4_REFERENCES && targetLod == 4)) {
                        references.computeIfAbsent(lod, k -> new LinkedHashMap<>())
                                .computeIfAbsent(property.getObject(), k -> new ArrayList<>())
                                .add(property);
                    }
                }
            }

            super.visit(property);
        }

        private Integer getLod(GeometryProperty<?> property) {
            Integer lod;
            do {
                lod = properties.get(property);
            } while (lod == null && (property = property.getParent(GeometryProperty.class)) != null);

            return lod;
        }
    }
}
