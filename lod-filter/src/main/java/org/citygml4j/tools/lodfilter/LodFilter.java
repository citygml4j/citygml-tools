/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2019 Claus Nagel <claus.nagel@gmail.com>
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

package org.citygml4j.tools.lodfilter;

import org.citygml4j.model.citygml.appearance.Appearance;
import org.citygml4j.model.citygml.core.AbstractCityObject;
import org.citygml4j.model.citygml.core.LodRepresentation;
import org.citygml4j.model.common.base.ModelObject;
import org.citygml4j.model.common.base.ModelObjects;
import org.citygml4j.model.common.child.Child;
import org.citygml4j.model.gml.base.AbstractGML;
import org.citygml4j.model.gml.base.AssociationByRepOrRef;
import org.citygml4j.util.child.ChildInfo;
import org.citygml4j.util.walker.FeatureWalker;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LodFilter {
    private final boolean[] lods = {false, false, false, false, false};
    private final AppearanceCleaner appearanceCleaner = new AppearanceCleaner();
    private final ChildInfo childInfo = new ChildInfo();
    private final Set<String> removedCityObjects = ConcurrentHashMap.newKeySet();

    private LodFilterMode mode = LodFilterMode.KEEP;
    private boolean keepCityObjectsWithoutLods;
    private List<Appearance> globalAppearances;

    public LodFilter filterLod(int lod) {
        if (lod >= 0 && lod < lods.length)
            lods[lod] = true;

        return this;
    }

    public LodFilter withFilterMode(LodFilterMode mode) {
        this.mode = mode;
        return this;
    }

    public LodFilter keepCityObjectsWithoutLods(boolean keepCityObjectsWithoutLods) {
        this.keepCityObjectsWithoutLods = keepCityObjectsWithoutLods;
        return this;
    }

    public LodFilter withGlobalApps(List<Appearance> appearances) {
        globalAppearances = appearances;
        return this;
    }

    public boolean hasRemainingGlobalApps() {
        return globalAppearances != null && !globalAppearances.isEmpty();
    }

    public List<Appearance> getRemainingGlobalApps() {
        return globalAppearances;
    }

    public Set<String> getRemovedCityObjectIds() {
        return new HashSet<>(removedCityObjects);
    }

    public AbstractCityObject apply(AbstractCityObject cityObject) {
        IdentityHashMap<AbstractCityObject, List<AbstractCityObject>> tree = new IdentityHashMap<>();
        List<AssociationByRepOrRef<? extends AbstractGML>> removedProperties = new ArrayList<>();
        boolean[] filterLods = calcFilterLods(cityObject);

        cityObject.accept(new FeatureWalker() {
            public void visit(AbstractCityObject nested) {
                LodRepresentation representation = nested.getLodRepresentation();
                if (representation != null) {
                    for (int lod = 0; lod < filterLods.length; lod++) {
                        if (representation.isSetRepresentation(lod) && (filterLods[lod] ^ mode != LodFilterMode.REMOVE)) {
                            List<AssociationByRepOrRef<? extends AbstractGML>> properties = representation.getRepresentation(lod);
                            for (AssociationByRepOrRef<?> property : properties) {
                                ModelObject parent = property.getParent();
                                ModelObjects.unsetProperty(parent != null ? parent : nested, property);
                            }

                            removedProperties.addAll(properties);
                        }
                    }
                }

                if (!keepCityObjectsWithoutLods && nested != cityObject) {
                    AbstractCityObject parent = childInfo.getParentCityObject(nested);
                    tree.computeIfAbsent(parent, v -> new ArrayList<>()).add(nested);
                }

                super.visit(nested);
            }
        });

        boolean remove = false;
        if (!keepCityObjectsWithoutLods)
            remove = removeNode(cityObject, tree, true);

        if (!remove || globalAppearances != null) {
            Set<String> candidates = appearanceCleaner.getCandidateTargets(removedProperties);

            if (!remove)
                appearanceCleaner.cleanupAppearances(cityObject, candidates);

            if (globalAppearances != null)
                appearanceCleaner.cleanupAppearances(globalAppearances, candidates);
        }

        return remove ? null : cityObject;
    }

    private boolean[] calcFilterLods(AbstractCityObject cityObject) {
        if (mode == LodFilterMode.KEEP || mode == LodFilterMode.REMOVE)
            return lods;

        else {
            boolean[] filterLods = new boolean[5];
            MinMaxLodWalker minMaxLodWalker = new MinMaxLodWalker();
            cityObject.accept(minMaxLodWalker);

            if (mode == LodFilterMode.MAXIMUM && minMaxLodWalker.max != -Integer.MAX_VALUE)
                filterLods[minMaxLodWalker.max] = true;

            if (mode == LodFilterMode.MINIMUM && minMaxLodWalker.min != Integer.MAX_VALUE)
                filterLods[minMaxLodWalker.min] = true;

            return filterLods;
        }
    }

    private boolean removeNode(AbstractCityObject node, IdentityHashMap<AbstractCityObject, List<AbstractCityObject>> tree, boolean isParent) {
        boolean hasChildren = false;

        List<AbstractCityObject> children = tree.get(node);
        if (children != null) {
            for (AbstractCityObject child : children) {
                boolean remove = removeNode(child, tree, false);
                if (!remove)
                    hasChildren = true;
            }
        }

        if (!hasChildren) {
            LodRepresentation representation = node.getLodRepresentation();
            if (!representation.hasLodRepresentations()) {
                if (!isParent) {
                    ModelObject property = node.getParent();
                    if (property instanceof Child)
                        ModelObjects.unsetProperty(((Child) property).getParent(), property);
                }

                if (node.isSetId())
                    removedCityObjects.add(node.getId());

                return true;
            }
        }

        return false;
    }

    private final class MinMaxLodWalker extends FeatureWalker {
        int min = Integer.MAX_VALUE;
        int max = -Integer.MAX_VALUE;

        public void visit(AbstractCityObject cityObject) {
            LodRepresentation representation = cityObject.getLodRepresentation();
            if (representation.hasLodRepresentations()) {
                for (int lod = 0; lod < lods.length; lod++) {
                    if (lods[lod] && representation.isSetRepresentation(lod)) {
                        if (lod < min) min = lod;
                        if (lod > max) max = lod;
                    }
                }
            }
        }
    }

}
