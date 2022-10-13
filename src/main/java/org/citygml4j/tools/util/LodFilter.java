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

import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.common.GeometryInfo;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.visitor.ObjectWalker;
import org.xmlobjects.gml.model.GMLObject;
import org.xmlobjects.gml.model.base.AbstractProperty;
import org.xmlobjects.gml.model.feature.FeatureProperty;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.Envelope;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.model.Child;

import java.util.*;

public class LodFilter {
    private final boolean[] lods = {false, false, false, false, false};
    private final ExtentUpdater extentUpdater = new ExtentUpdater();

    private Mode mode = Mode.KEEP;
    private boolean updateExtents;
    private AppearanceRemover globalAppearanceRemover;
    private CityObjectGroupRemover groupRemover;
    private Map<String, AbstractGeometry> templates;
    private boolean keepEmptyObjects;

    public enum Mode {
        KEEP,
        REMOVE,
        MINIMUM,
        MAXIMUM;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private LodFilter() {
    }

    private LodFilter(int[] lods) {
        withLods(lods);
    }

    public static LodFilter newInstance() {
        return new LodFilter();
    }

    public static LodFilter of(int... lods) {
        return new LodFilter(lods);
    }

    public LodFilter withLods(int... lods) {
        Arrays.fill(this.lods, false);
        for (int lod : lods) {
            if (lod >= 0 && lod < this.lods.length) {
                this.lods[lod] = true;
            }
        }

        return this;
    }

    public LodFilter withMode(Mode mode) {
        this.mode = mode != null ? mode : Mode.KEEP;
        return this;
    }

    public LodFilter updateExtents(boolean updateExtents) {
        this.updateExtents = updateExtents;
        return this;
    }

    public List<Appearance> getGlobalAppearances() {
        return globalAppearanceRemover != null ?
                globalAppearanceRemover.getAppearances() :
                Collections.emptyList();
    }

    public LodFilter withGlobalAppearances(List<Appearance> globalAppearances) {
        globalAppearanceRemover = AppearanceRemover.of(globalAppearances);
        return this;
    }

    public LodFilter withGlobalAppearanceHelper(AppearanceHelper globalAppearanceHelper) {
        globalAppearanceRemover = AppearanceRemover.of(globalAppearanceHelper);
        return this;
    }

    public List<CityObjectGroup> getCityObjectGroups() {
        return groupRemover != null ?
                groupRemover.getCityObjectGroups() :
                Collections.emptyList();
    }

    public LodFilter withCityObjectGroups(List<CityObjectGroup> groups) {
        groupRemover = CityObjectGroupRemover.of(groups);
        return this;
    }

    public LodFilter withTemplateGeometries(Map<String, AbstractGeometry> templates) {
        this.templates = templates;
        return this;
    }

    public LodFilter keepEmptyObjects(boolean keepEmptyObjects) {
        this.keepEmptyObjects = keepEmptyObjects;
        return this;
    }

    public boolean filter(AbstractFeature feature) {
        return filter(feature, keepEmptyObjects);
    }

    private boolean filter(AbstractFeature feature, boolean keepEmptyObjects) {
        GeometryInfo geometryInfo = feature.getGeometryInfo(true);
        boolean[] filter = createLodFilter(geometryInfo);

        List<AbstractProperty<?>> geometries = new ArrayList<>();
        for (int lod = 0; lod < filter.length; lod++) {
            if (filter[lod] ^ mode != Mode.REMOVE) {
                geometries.addAll(geometryInfo.getGeometries(lod));
                geometries.addAll(geometryInfo.getImplicitGeometries(lod));
            }
        }

        boolean remove = false;
        if (!geometries.isEmpty()) {
            FeatureInfo featureInfo = new FeatureInfo();
            Set<String> removedFeatureIds = new HashSet<>();

            removeGeometries(geometries, featureInfo, keepEmptyObjects);
            remove = removeEmptyFeatures(feature, featureInfo, keepEmptyObjects, removedFeatureIds);
            removeAppearances(geometries, remove, feature);
            removeFeatureProperties(removedFeatureIds, remove, feature);

            if (!remove && updateExtents) {
                extentUpdater.updateExtent(feature);
            }
        }

        return !remove;
    }

    public void postprocess() {
        postprocessGroups();
        if (globalAppearanceRemover != null) {
            globalAppearanceRemover.postprocess();
        }
    }

    private void removeGeometries(List<AbstractProperty<?>> geometries, FeatureInfo featureInfo, boolean keepEmptyObjects) {
        for (AbstractProperty<?> geometry : geometries) {
            Child child = geometry.getParent();
            if (child instanceof GMLObject) {
                ((GMLObject) child).unsetProperty(geometry, true);
            }

            if (!keepEmptyObjects) {
                featureInfo.add(child instanceof AbstractFeature ?
                        (AbstractFeature) child :
                        geometry.getParent(AbstractFeature.class));
            }
        }
    }

    private boolean removeEmptyFeatures(AbstractFeature feature, FeatureInfo featureInfo, boolean keepEmptyObjects, Set<String> removedFeatureIds) {
        boolean empty = true;
        for (AbstractFeature child : featureInfo.getChildren(feature)) {
            if (!removeEmptyFeatures(child, featureInfo, keepEmptyObjects, removedFeatureIds)) {
                empty = false;
            }
        }

        if (empty && !keepEmptyObjects) {
            GeometryInfo geometryInfo = feature.getGeometryInfo(true);
            if (!geometryInfo.hasLodGeometries() && !geometryInfo.hasLodImplicitGeometries()) {
                if (feature.getParent() != null && feature.getParent().getParent() instanceof GMLObject) {
                    GMLObject parent = (GMLObject) feature.getParent().getParent();
                    parent.unsetProperty(feature.getParent(), true);
                }

                if (groupRemover != null) {
                    groupRemover.removeMembers(feature);
                }

                if (feature.getId() != null) {
                    removedFeatureIds.add(feature.getId());
                }

                return true;
            }
        }

        return false;
    }

    private void removeAppearances(List<AbstractProperty<?>> geometries, boolean remove, AbstractFeature root) {
        if (!remove || globalAppearanceRemover != null) {
            AppearanceRemover localAppearanceRemover = AppearanceRemover.of(root);
            geometries.forEach(localAppearanceRemover::removeTarget);
            localAppearanceRemover.postprocess();

            if (globalAppearanceRemover != null) {
                geometries.forEach(globalAppearanceRemover::removeTarget);
            }
        }
    }

    private void removeFeatureProperties(Set<String> removedIds, boolean remove, AbstractFeature root) {
        if (!remove && !removedIds.isEmpty()) {
            root.accept(new ObjectWalker() {
                @Override
                public void visit(FeatureProperty<?> property) {
                    if (property.getHref() != null
                            && removedIds.contains(CityObjects.getIdFromReference(property.getHref()))) {
                        Child child = property.getParent();
                        if (child instanceof GMLObject) {
                            ((GMLObject) child).unsetProperty(property, true);
                        }
                    }

                    super.visit(property);
                }
            });
        }
    }

    private boolean[] createLodFilter(GeometryInfo geometryInfo) {
        if (mode == Mode.KEEP || mode == Mode.REMOVE) {
            return lods;
        } else {
            boolean[] lods = new boolean[this.lods.length];
            int min = Integer.MAX_VALUE;
            int max = -Integer.MAX_VALUE;

            for (int lod : geometryInfo.getLods()) {
                if (lod >= 0 && lod < lods.length && this.lods[lod]) {
                    min = Math.min(lod, min);
                    max = Math.max(lod, max);
                }
            }

            if (mode == Mode.MAXIMUM && max != -Integer.MAX_VALUE) {
                lods[max] = true;
            } else if (mode == Mode.MINIMUM && min != Integer.MAX_VALUE) {
                lods[min] = true;
            }

            return lods;
        }
    }

    private void postprocessGroups() {
        if (groupRemover != null && groupRemover.hasCityObjectGroups()) {
            for (CityObjectGroup group : groupRemover.getCityObjectGroups()) {
                if (!filter(group, true)) {
                    group.setGroupMembers(null);
                }
            }

            groupRemover.postprocess();
        }
    }

    private class ExtentUpdater extends ObjectWalker {
        private boolean preprocess;

        public void updateExtent(AbstractFeature feature) {
            if (templates != null && !templates.isEmpty()) {
                preprocess = true;
                feature.accept(this);
            }

            preprocess = false;
            feature.accept(this);
        }

        @Override
        public void visit(AbstractFeature feature) {
            if (!preprocess) {
                if (feature.getBoundedBy() != null && feature.getBoundedBy().isSetEnvelope()) {
                    Envelope envelope = feature.computeEnvelope();
                    if (!envelope.isEmpty()) {
                        feature.getBoundedBy().getEnvelope().setLowerCorner(envelope.getLowerCorner());
                        feature.getBoundedBy().getEnvelope().setUpperCorner(envelope.getUpperCorner());
                    } else {
                        feature.setBoundedBy(null);
                    }
                }
            }

            super.visit(feature);
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            if (preprocess) {
                GeometryProperty<?> property = implicitGeometry.getRelativeGeometry();
                if (property.getObject() == null && property.getHref() != null) {
                    property.setReferencedObjectIfValid(templates.get(
                            CityObjects.getIdFromReference(property.getHref())));
                }
            }
        }
    }

    private static class FeatureInfo {
        private final Map<AbstractFeature, Set<AbstractFeature>> tree = new IdentityHashMap<>();

        void add(AbstractFeature feature) {
            AbstractFeature parent = feature;
            while ((parent = parent.getParent(AbstractFeature.class)) != null) {
                if (tree.computeIfAbsent(parent, k -> Collections.newSetFromMap(new IdentityHashMap<>()))
                        .add(feature)) {
                    feature = parent;
                } else {
                    return;
                }
            }
        }

        Set<AbstractFeature> getChildren(AbstractFeature feature) {
            return tree.getOrDefault(feature, Collections.emptySet());
        }
    }
}
