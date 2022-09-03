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
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.util.reference.DefaultReferenceResolver;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.reader.ChunkOptions;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;
import org.xmlobjects.gml.util.reference.ReferenceResolver;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class UpgradeProcessor {
    private final ReferenceResolver referenceResolver = DefaultReferenceResolver.newInstance();
    private final DeprecatedPropertiesProcessor propertiesProcessor = DeprecatedPropertiesProcessor.newInstance();
    private final GeometryReferenceResolver crossTopLevelResolver = GeometryReferenceResolver.newInstance();
    private final CrossLodReferenceResolver crossLodResolver = CrossLodReferenceResolver.newInstance()
            .withMode(CrossLodReferenceResolver.Mode.REMOVE_LOD4_REFERENCES);

    private boolean resolveGeometryReferences;
    private boolean resolveCrossLodReferences;

    private UpgradeProcessor() {
    }

    public static UpgradeProcessor newInstance() {
        return new UpgradeProcessor();
    }

    public UpgradeProcessor useLod4AsLod3(boolean useLod4AsLod3) {
        propertiesProcessor.useLod4AsLod3(useLod4AsLod3);
        return this;
    }

    public UpgradeProcessor mapLod1MultiSurfaces(boolean mapLod1MultiSurfaces) {
        propertiesProcessor.mapLod1MultiSurfaces(mapLod1MultiSurfaces);
        return this;
    }

    public UpgradeProcessor resolveGeometryReferences(boolean resolveGeometryReferences) {
        this.resolveGeometryReferences = resolveGeometryReferences;
        return this;
    }

    public UpgradeProcessor createCityObjectRelations(boolean createCityObjectRelations) {
        crossTopLevelResolver.createCityObjectRelations(createCityObjectRelations);
        return this;
    }

    public UpgradeProcessor resolveCrossLodReferences(boolean resolveCrossLodReferences) {
        this.resolveCrossLodReferences = resolveCrossLodReferences;
        crossLodResolver.withMode(resolveCrossLodReferences ?
                CrossLodReferenceResolver.Mode.RESOLVE :
                CrossLodReferenceResolver.Mode.REMOVE_LOD4_REFERENCES);
        return this;
    }

    public List<Appearance> getGlobalAppearances() {
        return propertiesProcessor.getGlobalAppearances();
    }

    public void readGlobalObjects(Path file, CityGMLContext context) throws ExecutionException {
        try {
            try (CityGMLReader reader = createCityGMLReader(file, context, false)) {
                List<Appearance> appearances = new ArrayList<>();
                int featureId = 0;
                while (reader.hasNext()) {
                    AbstractFeature feature = reader.next();
                    if (feature instanceof Appearance) {
                        appearances.add((Appearance) feature);
                    } else if (resolveGeometryReferences) {
                        featureId++;
                        referenceResolver.resolveReferences(feature);
                        crossTopLevelResolver.processGeometryReferences(feature, featureId);
                    }
                }

                if (!appearances.isEmpty()) {
                    AppearanceHelper appearanceHelper = AppearanceHelper.of(appearances);
                    propertiesProcessor.withGlobalAppearanceHelper(appearanceHelper);
                    crossLodResolver.withGlobalAppearanceHelper(appearanceHelper);
                }
            }

            if (resolveGeometryReferences && crossTopLevelResolver.hasReferences()) {
                try (CityGMLReader reader = createCityGMLReader(file, context, true)) {
                    while (reader.hasNext()) {
                        crossTopLevelResolver.processReferencedGeometries(reader.next());
                    }
                }
            }
        } catch (CityGMLReadException e) {
            throw new ExecutionException("Failed to read global objects.", e);
        }
    }

    public void upgrade(AbstractFeature feature, int featureId) {
        referenceResolver.resolveReferences(feature);

        if (resolveGeometryReferences && crossTopLevelResolver.hasReferences()) {
            crossTopLevelResolver.resolveGeometryReferences(feature, featureId);
        }

        if (resolveCrossLodReferences || !propertiesProcessor.isUseLod4AsLod3()) {
            crossLodResolver.resolveCrossLodReferences(feature);
        }

        propertiesProcessor.process(feature);
    }

    public void postprocess() {
        propertiesProcessor.postprocess();
    }

    public ResultStatistics getResultStatistics() {
        return new ResultStatistics();
    }

    private CityGMLReader createCityGMLReader(Path file, CityGMLContext context, boolean skipAppearance) throws CityGMLReadException {
        CityGMLInputFactory in = context.createCityGMLInputFactory()
                .withChunking(ChunkOptions.defaults())
                .withIdCreator(new IdCreator());

        CityGMLReader reader = in.createCityGMLReader(file);
        if (skipAppearance) {
            return in.createFilteredCityGMLReader(reader, name -> !"Appearance".equals(name.getLocalPart())
                    || !CityGMLModules.isCityGMLNamespace(name.getNamespaceURI()));
        } else {
            return reader;
        }
    }

    public class ResultStatistics {

        public int getResolvedCrossTopLevelReferences() {
            return crossTopLevelResolver.getResolvedReferencesCounter();
        }

        public int getCreatedCityObjectRelations() {
            return crossTopLevelResolver.getCityObjectRelationsCounter();
        }

        public int getResolvedCrossLodReferences() {
            return crossLodResolver.getCounter().getOrDefault(CrossLodReferenceResolver.Mode.RESOLVE, 0);
        }

        public int getRemovedCrossLodReferences() {
            return crossLodResolver.getCounter().getOrDefault(CrossLodReferenceResolver.Mode.REMOVE_LOD4_REFERENCES, 0);
        }
    }
}
