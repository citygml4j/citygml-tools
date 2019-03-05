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
            int[] min = {Integer.MAX_VALUE};
            int[] max = {-Integer.MAX_VALUE};

            cityObject.accept(new FeatureWalker() {
                public void visit(AbstractCityObject cityObject) {
                    LodRepresentation representation = cityObject.getLodRepresentation();
                    if (representation.hasLodRepresentations()) {
                        for (int lod = 0; lod < lods.length; lod++) {
                            if (lods[lod] && representation.isSetRepresentation(lod)) {
                                if (lod < min[0]) min[0] = lod;
                                if (lod > max[0]) max[0] = lod;
                            }
                        }
                    }
                }
            });

            if (mode == LodFilterMode.MAXIMUM && max[0] != -Integer.MAX_VALUE)
                filterLods[max[0]] = true;

            if (mode == LodFilterMode.MINIMUM && min[0] != Integer.MAX_VALUE)
                filterLods[min[0]] = true;

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

}
