/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2025 Claus Nagel <claus.nagel@gmail.com>
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

import org.citygml4j.core.model.appearance.*;
import org.citygml4j.core.model.core.AbstractGenericAttribute;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.util.CityGMLPatterns;
import org.citygml4j.core.visitor.ObjectWalker;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.log.Logger;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.Module;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.module.citygml.CoreModule;
import org.xmlobjects.gml.model.base.AbstractReference;
import org.xmlobjects.gml.model.feature.BoundingShape;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.model.geometry.Envelope;
import org.xmlobjects.gml.model.geometry.GeometryProperty;
import org.xmlobjects.gml.model.geometry.SRSReference;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;
import java.util.*;
import java.util.function.Consumer;
import java.util.regex.Matcher;

public class StatisticsProcessor {
    private final Statistics statistics;
    private final CityGMLContext context;
    private final StatisticsWalker statisticsWalker = new StatisticsWalker();
    private final Deque<FeatureInfo> featureInfos = new ArrayDeque<>();
    private final Deque<SrsInfo> srsInfos = new ArrayDeque<>();
    private final Matcher lodMatcher = CityGMLPatterns.LOD_FROM_PROPERTY_NAME.matcher("");
    private final String DEFAULT_SRS_NAME = "unknown";

    private boolean computeEnvelope;
    private boolean onlyTopLevelFeatures;
    private boolean generateObjectHierarchy;
    private List<Appearance> globalAppearances;
    private Map<String, AbstractGeometry> templates;

    private StatisticsProcessor(Statistics statistics, CityGMLContext context) {
        this.statistics = statistics;
        this.context = context;
    }

    public static StatisticsProcessor of(Statistics statistics, CityGMLContext context) {
        return new StatisticsProcessor(statistics, context);
    }

    public StatisticsProcessor computeEnvelope(boolean computeEnvelope) {
        this.computeEnvelope = computeEnvelope;
        return this;
    }

    public StatisticsProcessor onlyTopLevelFeatures(boolean onlyTopLevelFeatures) {
        this.onlyTopLevelFeatures = onlyTopLevelFeatures;
        return this;
    }

    public StatisticsProcessor generateObjectHierarchy(boolean generateObjectHierarchy) {
        this.generateObjectHierarchy = generateObjectHierarchy;
        return this;
    }

    public StatisticsProcessor withGlobalAppearances(List<Appearance> globalAppearances) {
        this.globalAppearances = globalAppearances;
        return this;
    }

    public void process(QName element, String prefix, boolean isTopLevel, int depth, Statistics statistics) {
        statistics.addVersion(CityGMLModules.getCityGMLVersion(element.getNamespaceURI()));

        Module module = CityGMLModules.getModuleFor(element.getNamespaceURI());
        if (module instanceof CoreModule
                && !statistics.hasObjects(Statistics.ObjectType.FEATURE)
                && "CityModel".equals(element.getLocalPart())) {
            return;
        }

        if (!statistics.hasValidExtent()) {
            computeEnvelope = true;
        }

        if (module != null) {
            prefix = module.getNamespacePrefix();
        } else {
            prefix = XMLConstants.DEFAULT_NS_PREFIX.equals(prefix) ? "ade" : prefix;
        }

        statistics.addModule(prefix, element.getNamespaceURI());
        if (isTopLevel || !onlyTopLevelFeatures) {
            addObject(element, prefix, statistics::addFeature);

            if (generateObjectHierarchy) {
                featureInfos.push(new FeatureInfo(prefix + ":" + element.getLocalPart(), depth));
                List<String> hierarchy = new ArrayList<>();
                featureInfos.descendingIterator()
                        .forEachRemaining(featureInfo -> hierarchy.add(featureInfo.name()));
                statistics.addFeatureHierarchy(hierarchy);
            }
        }
    }

    public void process(QName element, Appearance appearance, boolean isTopLevel) {
        if (appearance != null) {
            if (isTopLevel) {
                statistics.setHasGlobalAppearances(true);
            }

            String prefix = getPrefix(element.getNamespaceURI(), "app");
            addObjectAndModule(element, prefix, statistics::addAppearance);
            statistics.addTheme(appearance.getTheme());
            appearance.accept(statisticsWalker.withAppearancePrefix(prefix));
        }
    }

    public void process(QName element, AbstractGeometry geometry, QName parent) {
        if (geometry != null) {
            addObjectAndModule(element, getPrefix(element.getNamespaceURI(), "gml"), statistics::addGeometry);
            getLodFromPropertyName(parent);

            if (computeEnvelope) {
                statistics.getExtent(getSrsName(geometry)).include(geometry.computeEnvelope());
            }

            if (globalAppearances != null) {
                processGlobalAppearances(geometry);
            }

            geometry.accept(statisticsWalker);
        }
    }

    public void process(QName element, ImplicitGeometry implicitGeometry, QName parent) throws ExecutionException {
        if (implicitGeometry != null) {
            addObjectAndModule(element, getPrefix(element.getNamespaceURI(), "core"), statistics::addGeometry);
            getLodFromPropertyName(parent);

            if (computeEnvelope) {
                GeometryProperty<?> property = implicitGeometry.getRelativeGeometry();
                if (property != null
                        && property.getObject() == null
                        && property.getHref() != null) {
                    if (templates == null) {
                        lazyLoadTemplates();
                    }

                    AbstractGeometry template = templates.get(CityObjects.getIdFromReference(property.getHref()));
                    if (template != null) {
                        property.setReferencedObjectIfValid(template);
                        if (globalAppearances != null) {
                            processGlobalAppearances(template);
                        }
                    }
                }

                String srsName = implicitGeometry.getReferencePoint() != null
                        && implicitGeometry.getReferencePoint().getObject() != null ?
                        getSrsName(implicitGeometry.getReferencePoint().getObject()) :
                        getCurrentSrsName();

                statistics.getExtent(srsName).include(implicitGeometry.computeEnvelope());
            }

            statistics.setHasImplicitGeometries(true);
            implicitGeometry.accept(statisticsWalker);
        }
    }

    public void process(AbstractGenericAttribute<?> genericAttribute) {
        if (genericAttribute != null) {
            statistics.addGenericAttribute(genericAttribute.getName(), genericAttribute.getClass().getSimpleName());
        }
    }

    public void process(BoundingShape boundingShape, int depth, boolean isCityModel, Statistics statistics) {
        if (boundingShape != null && boundingShape.isSetEnvelope()) {
            Envelope envelope = boundingShape.getEnvelope();

            if (envelope.getSrsName() != null) {
                srsInfos.push(new SrsInfo(envelope.getSrsName(), depth));
                statistics.addReferenceSystem(envelope.getSrsName());
            }

            if (isCityModel && !computeEnvelope) {
                statistics.getExtent(getSrsName(envelope)).include(envelope);
            }
        }
    }

    public void updateDepth(int depth) {
        if (generateObjectHierarchy
                && !featureInfos.isEmpty()
                && featureInfos.peek().depth() == depth + 1) {
            featureInfos.pop();
        }

        if (!srsInfos.isEmpty()
                && srsInfos.peek().depth() == depth + 1) {
            srsInfos.pop();
        }
    }

    private void addObjectAndModule(QName element, String prefix, Consumer<String> consumer) {
        statistics.addModule(prefix, element.getNamespaceURI());
        addObject(element, prefix, consumer);
    }

    private void addObject(QName element, String prefix, Consumer<String> consumer) {
        consumer.accept(prefix + ":" + element.getLocalPart());
    }

    private void getLodFromPropertyName(QName property) {
        if (property != null) {
            lodMatcher.reset(property.getLocalPart());
            if (lodMatcher.matches()) {
                statistics.addLod(Integer.parseInt(lodMatcher.group(1)));
            }
        }
    }

    private String getPrefix(String namespaceURI, String defaultPrefix) {
        Module module = CityGMLModules.getModuleFor(namespaceURI);
        return module != null ? module.getNamespacePrefix() : defaultPrefix;
    }

    private String getCurrentSrsName() {
        if (!srsInfos.isEmpty()) {
            return srsInfos.peek().srsName();
        } else {
            statistics.addReferenceSystem(DEFAULT_SRS_NAME);
            return DEFAULT_SRS_NAME;
        }
    }

    private String getSrsName(SRSReference srsReference) {
        return srsReference != null && srsReference.getSrsName() != null ?
                srsReference.getSrsName() :
                getCurrentSrsName();
    }

    private void lazyLoadTemplates() throws ExecutionException {
        Logger.getInstance().debug("Reading implicit geometries from input file.");
        templates = GlobalObjectsReader.onlyImplicitGeometries()
                .read(statistics.getFile(), context)
                .getTemplateGeometries();
    }

    private void processGlobalAppearances(AbstractGeometry geometry) {
        GlobalAppearanceResolver resolver = GlobalAppearanceResolver.of(geometry);
        for (Appearance globalAppearance : globalAppearances) {
            Appearance appearance = resolver.resolve(globalAppearance);
            if (appearance != null) {
                QName name = globalAppearance.getLocalProperties().get(GlobalObjects.NAME, QName.class);
                process(name, appearance, true);
            }
        }
    }

    private record FeatureInfo(String name, int depth) {
    }

    private record SrsInfo(String srsName, int depth) {
    }

    private class StatisticsWalker extends ObjectWalker {
        private String appearancePrefix;

        public StatisticsWalker withAppearancePrefix(String appearancePrefix) {
            this.appearancePrefix = appearancePrefix;
            return this;
        }

        @Override
        public void visit(AbstractGeometry geometry) {
            statistics.addReferenceSystem(geometry.getSrsName());
            super.visit(geometry);
        }

        @Override
        public void visit(AbstractSurfaceData surfaceData) {
            statistics.addAppearance(appearancePrefix + ":" + surfaceData.getClass().getSimpleName());
        }

        @Override
        public void visit(AbstractTexture texture) {
            statistics.setHasTextures(true);
            super.visit(texture);
        }

        @Override
        public void visit(X3DMaterial x3dMaterial) {
            statistics.setHasMaterials(true);
            super.visit(x3dMaterial);
        }
    }

    private static class GlobalAppearanceResolver extends ObjectWalker {
        private final Set<String> targets = new HashSet<>();
        private final Set<AbstractSurfaceData> surfaceData = Collections.newSetFromMap(new IdentityHashMap<>());

        public static GlobalAppearanceResolver of(AbstractGeometry geometry) {
            GlobalAppearanceResolver resolver = new GlobalAppearanceResolver();
            geometry.accept(resolver);
            return resolver;
        }

        public Appearance resolve(Appearance globalAppearance) {
            globalAppearance.accept(this);
            if (!surfaceData.isEmpty()) {
                Appearance appearance = new Appearance();
                appearance.setTheme(globalAppearance.getTheme());
                surfaceData.stream()
                        .map(AbstractSurfaceDataProperty::new)
                        .forEach(appearance.getSurfaceData()::add);

                surfaceData.clear();
                return appearance;
            } else {
                return null;
            }
        }

        @Override
        public void visit(AbstractGeometry geometry) {
            if (geometry.getId() != null) {
                targets.add(geometry.getId());
                targets.add("#" + geometry.getId());
            }
        }

        @Override
        public void visit(AbstractReference<?> reference) {
            if (reference.getHref() != null && targets.contains(reference.getHref())) {
                AbstractSurfaceData surfaceData = reference.getParent(AbstractSurfaceData.class);
                if (surfaceData != null) {
                    this.surfaceData.add(surfaceData);
                }
            }
        }
    }
}
