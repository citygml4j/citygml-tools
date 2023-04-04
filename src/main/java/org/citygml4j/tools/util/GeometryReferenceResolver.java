/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2023 Claus Nagel <claus.nagel@gmail.com>
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
import org.xmlobjects.gml.util.reference.ReferenceResolver;

import java.util.*;

public class GeometryReferenceResolver {
    private final GeometryCopyBuilder copyBuilder = GeometryCopyBuilder.newInstance();
    private final Map<String, GeometryReference> references = new HashMap<>();
    private final ResolverProcessor resolverProcessor = new ResolverProcessor();
    private final GeometryPropertyProcessor geometryPropertyProcessor = new GeometryPropertyProcessor();
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

    public void processGeometryReferences(AbstractFeature feature, int featureId, ReferenceResolver referenceResolver) {
        referenceResolver.resolveReferences(feature);
        processGeometryReferences(feature, featureId);
    }

    public void processGeometryReferences(AbstractFeature feature, int featureId) {
        geometryPropertyProcessor.process(feature, featureId);
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

    public void resolveGeometryReferences(AbstractFeature feature, int featureId, ReferenceResolver referenceResolver) {
        referenceResolver.resolveReferences(feature);
        resolveGeometryReferences(feature, featureId);
    }

    public void resolveGeometryReferences(AbstractFeature feature, int featureId) {
        if (!references.isEmpty()) {
            resolverProcessor.resolve(feature, featureId);
        }
    }

    private class ResolverProcessor extends ObjectWalker {
        private int featureId;
        private int propertyId;

        void resolve(AbstractFeature feature, int featureId) {
            this.featureId = featureId;
            feature.accept(this);
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
                            cityObjectRelationsCounter++;
                            CityObjectRelation relation = new CityObjectRelation("#" + relatedTo);
                            relation.setRelationType(new Code("shared"));
                            cityObject.getRelatedTo().add(new CityObjectRelationProperty(relation));
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
                propertyId++;
                GeometryReference reference = references.get(CityObjects.getIdFromReference(property.getHref()));
                if (reference != null) {
                    resolvedReferencesCounter++;
                    if (reference.getTarget(featureId) == propertyId) {
                        AbstractGeometry geometry = reference.createGeometryFor(featureId);
                        property.setInlineObjectIfValid(geometry);
                        property.setHref(null);
                    } else {
                        property.setHref("#" + reference.getOrCreateGeometryId(featureId));
                    }

                    if (createCityObjectRelations) {
                        String relatedTo = reference.getRelatedTo(featureId, propertyId);
                        if (relatedTo != null) {
                            AbstractCityObject cityObject = property.getParent(AbstractCityObject.class);
                            if (cityObject != null) {
                                cityObjectRelationsCounter++;
                                cityObject.setId(relatedTo);
                                CityObjectRelation relation = new CityObjectRelation("#" + reference.getOwner());
                                relation.setRelationType(new Code("shared"));
                                cityObject.getRelatedTo().add(new CityObjectRelationProperty(relation));
                            }
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
        private final String ID = "id";
        private int propertyId;

        void process(AbstractFeature feature, int featureId) {
            feature.accept(this);

            for (Map.Entry<String, Deque<AbstractCityObject>> entry : referees.entrySet()) {
                Deque<AbstractCityObject> candidates = entry.getValue();
                candidates.stream().filter(AbstractSpaceBoundary.class::isInstance)
                        .map(AbstractSpaceBoundary.class::cast)
                        .map(boundary -> boundary.getParent(AbstractSpace.class))
                        .forEach(space -> candidates.removeIf(space::equals));

                GeometryReference reference = references.computeIfAbsent(entry.getKey(), v -> new GeometryReference());
                reference.addTarget(featureId, candidates.getFirst().getLocalProperties().get(ID, Integer.class));
                if (createCityObjectRelations) {
                    candidates.forEach(candidate -> reference.addRelatedTo(
                            candidate, featureId, candidate.getLocalProperties().get(ID, Integer.class)));
                }
            }

            referees.clear();
        }

        @Override
        public void visit(GeometryProperty<?> property) {
            if (property.getObject() == null
                    && property.getHref() != null
                    && property.getParent(ImplicitGeometry.class) == null) {
                propertyId++;
                AbstractCityObject cityObject = property.getParent(AbstractCityObject.class);
                if (cityObject != null) {
                    String reference = CityObjects.getIdFromReference(property.getHref());
                    referees.computeIfAbsent(reference, v -> new ArrayDeque<>()).add(cityObject);
                    cityObject.getLocalProperties().set(ID, propertyId);
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
        private final Map<String, String> relatedTos = new HashMap<>();
        private final Map<Integer, Integer> targets = new HashMap<>();

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
            return relatedTos.values();
        }

        String getRelatedTo(int featureId, int propertyId) {
            return relatedTos.get(featureId + "_" + propertyId);
        }

        void addRelatedTo(AbstractCityObject cityObject, int featureId, int propertyId) {
            relatedTos.put(featureId + "_" + propertyId, CityObjects.getOrCreateId(cityObject));
        }

        int getTarget(int featureId) {
            return targets.getOrDefault(featureId, -1);
        }

        void addTarget(int featureId, int propertyId) {
            targets.put(featureId, propertyId);
        }
    }
}
