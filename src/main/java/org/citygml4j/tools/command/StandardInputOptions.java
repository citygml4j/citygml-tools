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

package org.citygml4j.tools.command;

import org.citygml4j.CityGMLContext;
import org.citygml4j.builder.cityjson.CityJSONBuilder;
import org.citygml4j.builder.cityjson.CityJSONBuilderException;
import org.citygml4j.builder.cityjson.json.io.reader.CityJSONInputFactory;
import org.citygml4j.builder.jaxb.CityGMLBuilder;
import org.citygml4j.builder.jaxb.CityGMLBuilderException;
import org.citygml4j.xml.io.CityGMLInputFactory;
import org.citygml4j.xml.io.reader.CityGMLReadException;
import org.citygml4j.xml.io.reader.CityGMLReader;
import org.citygml4j.xml.io.reader.FeatureReadMode;
import picocli.CommandLine;

import java.nio.file.Path;

public class StandardInputOptions {

    @CommandLine.Parameters(paramLabel = "<file>", description = "File(s) or directory to process (glob patterns allowed).")
    private String file;

    public String getFile() {
        return file;
    }

    public CityGMLInputFactory createCityGMLInputFactory(CityGMLBuilder builder) throws CityGMLBuilderException {
        CityGMLInputFactory in = builder.createCityGMLInputFactory();
        in.setProperty(CityGMLInputFactory.SKIP_GENERIC_ADE_CONTENT, true);

        return in;
    }

    public CityGMLReader createCityGMLReader(Path inputFile, CityGMLBuilder builder, boolean useChunks) throws CityGMLBuilderException, CityGMLReadException {
        CityGMLInputFactory in = createCityGMLInputFactory(builder);

        if (useChunks)
            in.setProperty(CityGMLInputFactory.FEATURE_READ_MODE, FeatureReadMode.SPLIT_PER_COLLECTION_MEMBER);

        return in.createCityGMLReader(inputFile.toFile());
    }

    public CityJSONInputFactory createCityJSONInputFactory() throws CityJSONBuilderException {
        CityJSONBuilder builder = CityGMLContext.getInstance().createCityJSONBuilder();
        return builder.createCityJSONInputFactory();
    }
}
