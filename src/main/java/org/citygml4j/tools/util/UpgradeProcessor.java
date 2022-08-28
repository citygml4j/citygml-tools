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
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.reader.ChunkOptions;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class UpgradeProcessor {
    private final DeprecatedPropertiesProcessor propertiesProcessor = DeprecatedPropertiesProcessor.newInstance();
    private final GeometryReferenceResolver referenceResolver = GeometryReferenceResolver.newInstance();

    private boolean resolveGeometryReferences;

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
        referenceResolver.createCityObjectRelations(createCityObjectRelations);
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
                        referenceResolver.processGeometryReferences(feature, featureId);
                    }
                }

                if (!appearances.isEmpty()) {
                    propertiesProcessor.withGlobalAppearances(appearances);
                }
            }

            if (resolveGeometryReferences && referenceResolver.hasReferences()) {
                try (CityGMLReader reader = createCityGMLReader(file, context, true)) {
                    while (reader.hasNext()) {
                        referenceResolver.processReferencedGeometries(reader.next());
                    }
                }
            }
        } catch (CityGMLReadException e) {
            throw new ExecutionException("Failed to read global objects.", e);
        }
    }

    public void upgrade(AbstractFeature feature, int featureId) {
        propertiesProcessor.process(feature);
        if (resolveGeometryReferences && referenceResolver.hasReferences()) {
            referenceResolver.resolveGeometryReferences(feature, featureId);
        }
    }

    public void postprocess() {
        propertiesProcessor.postprocess();
    }

    private CityGMLReader createCityGMLReader(Path file, CityGMLContext context, boolean skipAppearance) throws CityGMLReadException {
        CityGMLInputFactory in = context.createCityGMLInputFactory().withChunking(ChunkOptions.defaults());
        CityGMLReader reader = in.createCityGMLReader(file);
        if (skipAppearance) {
            return in.createFilteredCityGMLReader(reader, name -> !"Appearance".equals(name.getLocalPart())
                    || !CityGMLModules.isCityGMLNamespace(name.getNamespaceURI()));
        } else {
            return reader;
        }
    }
}
