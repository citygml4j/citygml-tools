/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.util;

import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.cityobjectgroup.RoleProperty;
import org.citygml4j.core.model.core.AbstractCityObjectReference;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.visitor.ObjectWalker;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CityObjectGroupRemover {
    private final List<CityObjectGroup> groups;
    private final Map<String, List<AbstractCityObjectReference>> groupParents = new HashMap<>();
    private final Map<String, List<RoleProperty>> groupMembers = new HashMap<>();
    private final MemberProcessor memberProcessor = new MemberProcessor();

    private CityObjectGroupRemover(List<CityObjectGroup> groups) {
        this.groups = groups;
    }

    public static CityObjectGroupRemover of(List<CityObjectGroup> groups) {
        CityObjectGroupRemover remover = new CityObjectGroupRemover(groups);
        remover.preprocess();
        return remover;
    }

    public List<CityObjectGroup> getCityObjectGroups() {
        return groups;
    }

    public boolean hasCityObjectGroups() {
        return !groups.isEmpty();
    }

    private void preprocess() {
        if (!groups.isEmpty()) {
            int capacity = Math.min(10, groups.size());

            for (CityObjectGroup group : groups) {
                if (group.getGroupParent() != null && group.getGroupParent().getHref() != null) {
                    String id = CityObjects.getIdFromReference(group.getGroupParent().getHref());
                    groupParents.computeIfAbsent(id, v -> new ArrayList<>(capacity)).add(group.getGroupParent());
                }

                if (group.isSetGroupMembers()) {
                    for (RoleProperty property : group.getGroupMembers()) {
                        if (property.getObject() != null
                                && property.getObject().getGroupMember() != null
                                && property.getObject().getGroupMember().getHref() != null) {
                            String id = CityObjects.getIdFromReference(property.getObject().getGroupMember().getHref());
                            groupMembers.computeIfAbsent(id, v -> new ArrayList<>(capacity)).add(property);
                        }
                    }
                }
            }
        }
    }

    public void removeMembers(AbstractFeature feature) {
        if (!groups.isEmpty()) {
            feature.accept(memberProcessor);
        }
    }

    public void postprocess() {
        if (!groups.isEmpty()) {
            groups.removeIf(group -> !group.isSetGroupMembers());
        }
    }

    private class MemberProcessor extends ObjectWalker {

        @Override
        public void visit(AbstractFeature feature) {
            if (feature.getId() != null) {
                List<AbstractCityObjectReference> references = groupParents.remove(feature.getId());
                if (references != null) {
                    references.forEach(reference -> reference.getParent(CityObjectGroup.class).setGroupParent(null));
                }

                List<RoleProperty> properties = groupMembers.remove(feature.getId());
                if (properties != null) {
                    for (RoleProperty property : properties) {
                        CityObjectGroup group = property.getParent(CityObjectGroup.class);
                        group.getGroupMembers().remove(property);
                        if (!group.isSetGroupMembers()) {
                            visit((AbstractFeature) group);
                        }
                    }
                }
            }
        }
    }
}
