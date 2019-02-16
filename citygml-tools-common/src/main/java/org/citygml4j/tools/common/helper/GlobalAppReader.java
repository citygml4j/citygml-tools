/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2013-2019 Claus Nagel <claus.nagel@gmail.com>
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

package org.citygml4j.tools.common.helper;

import org.citygml4j.builder.jaxb.CityGMLBuilder;
import org.citygml4j.builder.jaxb.CityGMLBuilderException;
import org.citygml4j.model.citygml.appearance.Appearance;
import org.citygml4j.model.module.Modules;
import org.citygml4j.xml.io.CityGMLInputFactory;
import org.citygml4j.xml.io.reader.CityGMLReadException;
import org.citygml4j.xml.io.reader.CityGMLReader;
import org.citygml4j.xml.io.reader.FeatureReadMode;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GlobalAppReader {
    private final CityGMLBuilder cityGMLBuilder;

    public GlobalAppReader(CityGMLBuilder cityGMLBuilder) {
        this.cityGMLBuilder = cityGMLBuilder;
    }

    public List<Appearance> readGlobalApps(Path file) throws CityGMLBuilderException, CityGMLReadException {
        CityGMLInputFactory in = cityGMLBuilder.createCityGMLInputFactory();
        in.setProperty(CityGMLInputFactory.FEATURE_READ_MODE, FeatureReadMode.SPLIT_PER_COLLECTION_MEMBER);

        List<Appearance> appearances = new ArrayList<>();
        try (CityGMLReader reader = in.createFilteredCityGMLReader(in.createCityGMLReader(file.toFile()),
                name -> name.getLocalPart().equals("Appearance")
                        && Modules.isCityGMLModuleNamespace(name.getNamespaceURI()))) {
            while (reader.hasNext())
                appearances.add((Appearance) reader.nextFeature());
        }

        return appearances;
    }

}
