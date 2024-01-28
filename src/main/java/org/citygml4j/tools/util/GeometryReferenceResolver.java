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

import org.citygml4j.core.model.core.*;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.gml.model.basictypes.Code;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.gml.util.id.DefaultIdCreator;

import java.util.*;

public class GeometryReferenceResolver {
    private final GeometryCopyBuilder copyBuilder = GeometryCopyBuilder.newInstance();
    private final Map<String, GeometryReference> references = new HashMap<>();
    private final GeometryProcessor geometryProcessor = new GeometryProcessor();

    private boolean createCityObjectRelations;
    private int resolvedReferencesCounter;
    private int cityObjectRelationsCounter;

    private GeometryReferenceResolver() {
    }

    public static GeometryReferenceResolver newInstance() {
        return new GeometryReferenceResolver();
    }

    public boolean isCreateCityObjectRelations() {
        return createCityObjectRelations;
    }

    public GeometryReferenceResolver createCityObjectRelations(boolean createCityObjectRelations) {
        this.createCityObjectRelations = createCityObjectRelations;
        return this;
    }

    public void processGeometryReferences(AbstractFeature feature, int featureId) {
        GeometryPropertyProcessor processor = new GeometryPropertyProcessor();
        processor.process(feature, featureId);
    }

    public void processReferencedGeometries(AbstractFeature feature) {
        feature.accept(geometryProcessor);
    }

    public int getResolvedReferencesCounter() {
        return resolvedReferencesCounter;
    }

    public int getCityObjectRelationsCounter() {
        return cityObjectRelationsCounter;
    }

    public boolean hasReferences() {
        return !references.isEmpty();
    }

    public void resolveGeometryReferences(AbstractFeature feature, int featureId) {
        if (!references.isEmpty()) {
            ResolverProcessor processor = new ResolverProcessor();
            processor.resolve(feature, featureId);
        }
    }

    private class ResolverProcessor extends ObjectWalker {
        private final Map<AbstractCityObject, Integer> childIds = new IdentityHashMap<>();
        private final Map<AbstractCityObject, Set<String>> relatedTos = new IdentityHashMap<>();
        private int featureId;

        void resolve(AbstractFeature feature, int featureId) {
            this.featureId = featureId;
            feature.accept(this);
            childIds.clear();
            relatedTos.clear();
        }

        @Override
        public void visit(AbstractCityObject cityObject) {
            childIds.put(cityObject, childIds.size());
            super.visit(cityObject);
        }

        @Override
        public void visit(AbstractGeometry geometry) {
            if (createCityObjectRelations && geometry.getId() != null) {
                GeometryReference reference = references.get(geometry.getId());
                if (reference != null) {
                    AbstractCityObject cityObject = geometry.getParent(AbstractCityObject.class);
                    if (cityObject != null) {
                        cityObject.setId(reference.getOwner());
                        for (String relatedTo : reference.getRelatedTos()) {
                            if (relatedTos.computeIfAbsent(cityObject, v -> new HashSet<>()).add(relatedTo)) {
                                cityObjectRelationsCounter++;
                                CityObjectRelation relation = new CityObjectRelation("#" + relatedTo);
                                relation.setRelationType(new Code("shared"));
                                cityObject.getRelatedTo().add(new CityObjectRelationProperty(relation));
                            }
                        }
                    }
                }
            }
        }

        @Override
        public void visit(GeometryProperty<?> property) {
            if (property.getObject() == null
                    && property.getHref() != null
                    && property.getParent(ImplicitGeometry.class) == null) {
                GeometryReference reference = references.get(CityObjects.getIdFromReference(property.getHref()));
                if (reference != null && reference.geometry != null) {
                    resolvedReferencesCounter++;
                    AbstractCityObject cityObject = property.getParent(AbstractCityObject.class);
                    if (cityObject != null) {
                        String target = reference.getTarget(featureId, childIds.get(cityObject));
                        if (target != null) {
                            AbstractGeometry geometry = reference.createGeometryFor(featureId);
                            property.setInlineObjectIfValid(geometry);
                            property.setHref(null);

                            if (createCityObjectRelations
                                    && relatedTos.computeIfAbsent(cityObject, v -> new HashSet<>())
                                    .add(reference.getOwner())) {
                                cityObjectRelationsCounter++;
                                cityObject.setId(target);
                                CityObjectRelation relation = new CityObjectRelation("#" + reference.getOwner());
                                relation.setRelationType(new Code("shared"));
                                cityObject.getRelatedTo().add(new CityObjectRelationProperty(relation));
                            }
                        } else {
                            property.setHref("#" + reference.getOrCreateGeometryId(featureId));
                        }
                    }
                }
            } else if (!property.isSetReferencedObject()) {
                super.visit(property);
            }
        }
    }

    private class GeometryPropertyProcessor extends ObjectWalker {
        private final Map<String, Deque<AbstractCityObject>> referees = new HashMap<>();
        private final Map<AbstractCityObject, Integer> childIds = new IdentityHashMap<>();

        void process(AbstractFeature feature, int featureId) {
            feature.accept(this);

            for (Map.Entry<String, Deque<AbstractCityObject>> entry : referees.entrySet()) {
                Deque<AbstractCityObject> candidates = entry.getValue();
                candidates.stream().filter(AbstractSpaceBoundary.class::isInstance)
                        .map(AbstractSpaceBoundary.class::cast)
                        .map(boundary -> boundary.getParent(AbstractSpace.class))
                        .forEach(space -> candidates.removeIf(space::equals));

                GeometryReference reference = references.computeIfAbsent(entry.getKey(), v -> new GeometryReference());
                reference.addTarget(candidates.getFirst(), featureId, childIds.get(candidates.getFirst()));
            }

            referees.clear();
            childIds.clear();
        }

        @Override
        public void visit(AbstractCityObject cityObject) {
            childIds.put(cityObject, childIds.size());
            super.visit(cityObject);
        }

        @Override
        public void visit(GeometryProperty<?> property) {
            if (property.getObject() == null
                    && property.getHref() != null
                    && property.getParent(ImplicitGeometry.class) == null) {
                AbstractCityObject cityObject = property.getParent(AbstractCityObject.class);
                if (cityObject != null) {
                    String reference = CityObjects.getIdFromReference(property.getHref());
                    referees.computeIfAbsent(reference, v -> new ArrayDeque<>()).add(cityObject);
                }
            } else {
                super.visit(property);
            }
        }
    }

    private class GeometryProcessor extends ObjectWalker {

        @Override
        public void visit(AbstractGeometry geometry) {
            if (geometry.getId() != null) {
                GeometryReference reference = references.get(geometry.getId());
                if (reference != null) {
                    reference.setGeometry(geometry);
                    reference.setOwner(geometry.getParent(AbstractCityObject.class));
                }
            }
        }
    }

    private class GeometryReference {
        private AbstractGeometry geometry;
        private String owner;
        private final Map<Integer, String> geometryIds = new HashMap<>();
        private final Map<String, String> targets = new HashMap<>();

        AbstractGeometry createGeometryFor(int featureId) {
            AbstractGeometry copy = copyBuilder.copy(geometry);
            copy.setId(getOrCreateGeometryId(featureId));
            return copy;
        }

        void setGeometry(AbstractGeometry geometry) {
            this.geometry = geometry;
        }

        String getOrCreateGeometryId(int featureId) {
            return geometryIds.computeIfAbsent(featureId, v -> DefaultIdCreator.getInstance().createId());
        }

        String getOwner() {
            return owner;
        }

        void setOwner(AbstractCityObject owner) {
            this.owner = CityObjects.getOrCreateId(owner);
        }

        Collection<String> getRelatedTos() {
            return targets.values();
        }

        String getTarget(int featureId, int childId) {
            return targets.get(featureId + "_" + childId);
        }

        void addTarget(AbstractCityObject cityObject, int featureId, int childId) {
            targets.put(featureId + "_" + childId, CityObjects.getOrCreateId(cityObject));
        }
    }
}
