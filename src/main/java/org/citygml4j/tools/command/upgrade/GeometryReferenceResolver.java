/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.command.upgrade;

import org.citygml4j.core.model.core.*;
import org.citygml4j.core.util.reference.DefaultReferenceResolver;
import org.citygml4j.core.util.reference.ResolveMode;
import org.citygml4j.core.visitor.ObjectWalker;
import org.citygml4j.tools.util.FeatureHelper;
import org.xmlobjects.copy.CopySession;
import org.xmlobjects.gml.model.basictypes.Code;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.gml.util.reference.ReferenceResolver;

import java.util.*;

public class GeometryReferenceResolver {
    private final GeometryCopier geometryCopier = GeometryCopier.newInstance();
    private final Map<String, GeometryReference> references = new HashMap<>();
    private final ReferenceResolver referenceResolver = DefaultReferenceResolver.newInstance()
            .withResolveMode(ResolveMode.GEOMETRIES_ONLY);

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
        referenceResolver.resolveReferences(feature);
        new ReferenceCollector().collect(feature, featureId);
    }

    public void processReferencedGeometries(AbstractFeature feature) {
        referenceResolver.resolveReferences(feature);
        new GeometryCollector().collect(feature).forEach((geometry, owner) -> {
            geometry.accept(new ObjectWalker() {
                @Override
                public void visit(GeometryProperty<?> property) {
                    if (property.isSetReferencedObject()) {
                        property.getObject().setParent(null);
                    } else {
                        super.visit(property);
                    }
                }
            });

            geometry.setParent(null);
            references.get(geometry.getId()).setGeometry(geometry, owner);
        });
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
            try (CopySession session = geometryCopier.createSession()) {
                new ResolverProcessor(featureId, session).resolve(feature);
            }
        }
    }

    private class ResolverProcessor extends ObjectWalker {
        private final Map<AbstractCityObject, Integer> childIds = new IdentityHashMap<>();
        private final Map<AbstractCityObject, Set<String>> relatedTos = new IdentityHashMap<>();
        private final int featureId;
        private final CopySession session;

        ResolverProcessor(int featureId, CopySession session) {
            this.featureId = featureId;
            this.session = session;
        }

        void resolve(AbstractFeature feature) {
            try {
                feature.accept(this);
            } finally {
                childIds.clear();
                relatedTos.clear();
            }
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
                                addRelatedTo(cityObject, relatedTo);
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
                GeometryReference reference = references.get(FeatureHelper.getIdFromReference(property.getHref()));
                if (reference != null && reference.geometry != null) {
                    resolvedReferencesCounter++;
                    AbstractCityObject cityObject = property.getParent(AbstractCityObject.class);
                    if (cityObject != null) {
                        boolean inline = !session.hasClone(reference.geometry);
                        AbstractGeometry clone = geometryCopier.copy(reference.geometry, session);

                        String target = reference.getTarget(featureId, childIds.get(cityObject));
                        if (target != null) {
                            if (inline) {
                                property.setInlineObjectIfValid(clone);
                                property.setHref(null);
                            } else {
                                property.setReferencedObjectIfValid(clone);
                                property.setHref("#" + clone.getId());
                            }

                            if (createCityObjectRelations
                                    && relatedTos.computeIfAbsent(cityObject, v -> new HashSet<>())
                                    .add(reference.getOwner())) {
                                cityObject.setId(target);
                                addRelatedTo(cityObject, reference.getOwner());
                            }
                        } else {
                            property.setReferencedObjectIfValid(clone);
                            property.setHref("#" + clone.getId());
                        }
                    }
                }
            } else if (property.isSetInlineObject()) {
                super.visit(property);
            }
        }

        private void addRelatedTo(AbstractCityObject cityObject, String relatedTo) {
            cityObjectRelationsCounter++;
            CityObjectRelation relation = new CityObjectRelation("#" + relatedTo);
            relation.setRelationType(new Code("shared"));
            cityObject.getRelatedTo().add(new CityObjectRelationProperty(relation));
        }
    }

    private class ReferenceCollector extends ObjectWalker {
        private final Map<String, List<AbstractCityObject>> referees = new HashMap<>();
        private final Map<AbstractCityObject, Integer> childIds = new IdentityHashMap<>();

        void collect(AbstractFeature feature, int featureId) {
            try {
                feature.accept(this);
                for (Map.Entry<String, List<AbstractCityObject>> entry : referees.entrySet()) {
                    GeometryReference reference = references.computeIfAbsent(entry.getKey(),
                            v -> new GeometryReference());
                    for (AbstractCityObject cityObject : entry.getValue()) {
                        reference.addTarget(cityObject, featureId, childIds.get(cityObject));
                    }
                }
            } finally {
                referees.clear();
                childIds.clear();
            }
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
                    String reference = FeatureHelper.getIdFromReference(property.getHref());
                    referees.computeIfAbsent(reference, k -> new ArrayList<>()).add(cityObject);
                }
            } else {
                super.visit(property);
            }
        }
    }

    private class GeometryCollector extends ObjectWalker {
        private final Map<AbstractGeometry, String> geometries = new IdentityHashMap<>();

        Map<AbstractGeometry, String> collect(AbstractFeature feature) {
            feature.accept(this);
            return geometries;
        }

        @Override
        public void visit(GeometryProperty<?> property) {
            if (property.isSetInlineObject()) {
                AbstractGeometry geometry = property.getObject();
                if (geometry.getId() != null && references.containsKey(geometry.getId())) {
                    AbstractCityObject parent = geometry.getParent(AbstractCityObject.class);
                    geometries.put(geometry, FeatureHelper.getOrCreateId(parent));
                }

                super.visit(property);
            }
        }
    }

    private static class GeometryReference {
        private final Map<String, String> targets = new HashMap<>();
        private AbstractGeometry geometry;
        private String owner;

        void setGeometry(AbstractGeometry geometry, String owner) {
            this.geometry = geometry;
            this.owner = owner;
        }

        String getOwner() {
            return owner;
        }

        Collection<String> getRelatedTos() {
            return targets.values();
        }

        String getTarget(int featureId, int childId) {
            return targets.get(featureId + "_" + childId);
        }

        void addTarget(AbstractCityObject cityObject, int featureId, int childId) {
            targets.put(featureId + "_" + childId, FeatureHelper.getOrCreateId(cityObject));
        }
    }
}
