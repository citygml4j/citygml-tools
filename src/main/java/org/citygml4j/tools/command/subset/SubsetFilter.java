/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.command.subset;

import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.visitor.ObjectWalker;
import org.citygml4j.tools.util.AppearanceRemover;
import org.citygml4j.tools.util.CityObjectGroupRemover;
import org.citygml4j.tools.util.FeatureHelper;
import org.citygml4j.tools.util.GlobalObjectHelper;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.module.citygml.CityObjectGroupModule;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.GeometryProperty;

import javax.xml.namespace.QName;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class SubsetFilter {
    private final SkippedFeatureProcessor skippedFeatureProcessor = new SkippedFeatureProcessor();
    private final TemplatesProcessor templatesProcessor = new TemplatesProcessor();
    private final Map<String, Integer> counter = new TreeMap<>();
    private final String TEMPLATE_ASSIGNED = "templateAssigned";

    private AppearanceRemover appearanceRemover;
    private CityObjectGroupRemover groupRemover;
    private Map<String, AbstractGeometry> templates;
    private Set<QName> typeNames;
    private Set<String> ids;
    private BoundingBoxFilter boundingBoxFilter;
    private boolean invert;
    private CountOptions countOptions;
    private boolean removeGroupMembers;

    private long count;
    private long index;

    private SubsetFilter() {
    }

    public static SubsetFilter newInstance() {
        return new SubsetFilter();
    }

    public SubsetFilter withGlobalObjectHelper(GlobalObjectHelper globalObjectHelper) {
        if (globalObjectHelper != null) {
            appearanceRemover = AppearanceRemover.of(globalObjectHelper.getAppearances());
            groupRemover = CityObjectGroupRemover.of(globalObjectHelper.getCityObjectGroups());
            templates = globalObjectHelper.getTemplateGeometries();
        }

        return this;
    }

    public SubsetFilter withTypeNamesFilter(TypeNameOptions typeNameOptions, CityGMLContext context) {
        this.typeNames = typeNameOptions != null ? typeNameOptions.getTypeNames(context) : null;
        return this;
    }

    public SubsetFilter withIdFilter(IdOptions idOptions) {
        this.ids = idOptions != null ? idOptions.getIds() : null;
        return this;
    }

    public BoundingBoxFilter getBoundingBoxFilter() {
        return boundingBoxFilter;
    }

    public SubsetFilter withBoundingBoxFilter(BoundingBoxFilter boundingBoxFilter) {
        this.boundingBoxFilter = boundingBoxFilter;
        return this;
    }

    public SubsetFilter invertFilterCriteria(boolean invert) {
        this.invert = invert;
        return this;
    }

    public SubsetFilter withCounterOption(CountOptions countOptions) {
        this.countOptions = countOptions;
        return this;
    }

    public SubsetFilter removeGroupMembers(boolean removeGroupMembers) {
        this.removeGroupMembers = removeGroupMembers;
        return this;
    }

    public boolean isCountWithinLimit() {
        return countOptions == null || count < countOptions.getLimit();
    }

    public Map<String, Integer> getCounter() {
        return counter;
    }

    public boolean filter(AbstractFeature feature, QName name, String prefix) {
        boolean keep = true;
        if (typeNames != null) {
            keep = typeNames.contains(name);
        }

        if (keep && ids != null) {
            keep = feature.getId() != null && ids.contains(feature.getId());
        }

        if (keep && boundingBoxFilter != null) {
            if (templates != null && !templates.isEmpty()) {
                templatesProcessor.preprocess(feature);
            }

            keep = boundingBoxFilter.filter(feature);
        }

        if (invert) {
            keep = !keep;
        }

        if (keep && countOptions != null) {
            if (index < countOptions.getStartIndex()) {
                index++;
                keep = false;
            } else {
                count++;
            }

            keep = keep && count <= countOptions.getLimit();
        }

        if (keep) {
            templatesProcessor.postprocess(feature);
            counter.merge(prefix + ":" + name.getLocalPart(), 1, Integer::sum);
        } else {
            feature.accept(skippedFeatureProcessor);
        }

        return keep;
    }

    public void postprocess() {
        postprocessGroups();
        postprocessImplicitGeometries();
        if (appearanceRemover != null) {
            appearanceRemover.postprocess();
        }
    }

    private void postprocessGroups() {
        if (groupRemover != null && groupRemover.hasCityObjectGroups()) {
            QName name = getGroupName();
            for (CityObjectGroup group : groupRemover.getCityObjectGroups()) {
                if (group.isSetGroupMembers()) {
                    if (!filter(group, name, "grp")) {
                        group.setGroupMembers(null);
                    }
                }
            }

            groupRemover.postprocess();
        }
    }

    private void postprocessImplicitGeometries() {
        if (templates != null && !templates.isEmpty()) {
            for (AbstractGeometry template : templates.values()) {
                if (!template.getLocalProperties().contains(TEMPLATE_ASSIGNED)) {
                    template.accept(skippedFeatureProcessor);
                }
            }
        }
    }

    private QName getGroupName() {
        if (typeNames != null) {
            for (QName typeName : typeNames) {
                if ("CityObjectGroup".equals(typeName.getLocalPart())
                        && CityGMLModules.isCityGMLNamespace(typeName.getNamespaceURI())) {
                    return typeName;
                }
            }
        }

        return new QName(CityObjectGroupModule.v3_0.getNamespaceURI(), "CityObjectGroup");
    }

    private class SkippedFeatureProcessor extends ObjectWalker {

        @Override
        public void visit(AbstractFeature feature) {
            if (removeGroupMembers && groupRemover != null) {
                groupRemover.removeMembers(feature);
            }
        }

        @Override
        public void visit(AbstractGeometry geometry) {
            if (appearanceRemover != null) {
                appearanceRemover.removeTarget(geometry);
            }
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
        }
    }

    private class TemplatesProcessor extends ObjectWalker {
        private boolean preprocess;

        public void preprocess(AbstractFeature feature) {
            preprocess = true;
            feature.accept(this);
        }

        public void postprocess(AbstractFeature feature) {
            preprocess = false;
            feature.accept(this);
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            GeometryProperty<?> property = implicitGeometry.getRelativeGeometry();
            if (property != null) {
                if (preprocess) {
                    if (property.getObject() == null && property.getHref() != null) {
                        property.setReferencedObjectIfValid(templates.get(
                                FeatureHelper.getIdFromReference(property.getHref())));
                    }
                } else {
                    if (property.isSetInlineObject() && property.getObject().getId() != null) {
                        AbstractGeometry template = templates.get(property.getObject().getId());
                        if (template.getLocalProperties().contains(TEMPLATE_ASSIGNED)) {
                            property.setInlineObject(null);
                            property.setHref("#" + template.getId());
                        } else {
                            template.getLocalProperties().set(TEMPLATE_ASSIGNED, true);
                        }
                    } else if (property.getHref() != null) {
                        AbstractGeometry template = templates.get(FeatureHelper.getIdFromReference(property.getHref()));
                        if (!template.getLocalProperties().contains(TEMPLATE_ASSIGNED)) {
                            property.setInlineObjectIfValid(template);
                            property.setHref(null);
                            template.getLocalProperties().set(TEMPLATE_ASSIGNED, true);
                        }
                    }
                }
            }
        }
    }
}
