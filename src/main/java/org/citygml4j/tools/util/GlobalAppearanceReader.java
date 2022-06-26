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
import org.citygml4j.tools.cli.ExecutionException;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.reader.ChunkOptions;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class GlobalAppearanceReader {

    private GlobalAppearanceReader() {
    }

    public static GlobalAppearanceReader newInstance() {
        return new GlobalAppearanceReader();
    }

    public List<Appearance> read(Path file, CityGMLContext context) throws ExecutionException {
        List<Appearance> appearances = new ArrayList<>();
        try {
            CityGMLInputFactory in = context.createCityGMLInputFactory()
                    .withChunking(ChunkOptions.defaults());

            try (CityGMLReader reader = in.createFilteredCityGMLReader(in.createCityGMLReader(file),
                    name -> name.getLocalPart().equals("Appearance")
                            && CityGMLModules.isCityGMLNamespace(name.getNamespaceURI()))) {
                while (reader.hasNext()) {
                    appearances.add((Appearance) reader.next());
                }
            }
        } catch (CityGMLReadException e) {
            throw new ExecutionException("Failed to read global appearances.", e);
        }

        return appearances;
    }
}
