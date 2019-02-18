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

import org.citygml4j.builder.jaxb.CityGMLBuilder;
import org.citygml4j.model.module.ModuleContext;
import org.citygml4j.model.module.citygml.CityGMLModuleType;
import org.citygml4j.model.module.citygml.CityGMLVersion;
import org.citygml4j.xml.io.CityGMLOutputFactory;
import org.citygml4j.xml.io.writer.AbstractCityGMLWriter;
import org.citygml4j.xml.io.writer.CityGMLWriteException;
import org.citygml4j.xml.io.writer.CityGMLWriter;
import org.citygml4j.xml.io.writer.CityModelWriter;
import picocli.CommandLine;

import java.nio.file.Path;

public class StandardCityGMLOutputOptions {

    @CommandLine.Option(names = "--citygml", description = "CityGML version used for output file: 2.0, 1.0 (default: ${DEFAULT-VALUE}).")
    private String version = "2.0";

    public CityGMLVersion getVersion() {
        return version.equals("1.0") ? CityGMLVersion.v1_0_0 : CityGMLVersion.v2_0_0;
    }

    public CityGMLOutputFactory createCityGMLOutputFactory(CityGMLBuilder builder) {
        return builder.createCityGMLOutputFactory(getVersion());
    }

    public CityModelWriter createCityModelWriter(Path outputFile, CityGMLBuilder builder) throws CityGMLWriteException {
        CityGMLVersion version = getVersion();

        CityGMLOutputFactory out = builder.createCityGMLOutputFactory(version);
        CityModelWriter writer = out.createCityModelWriter(outputFile.toFile());
        setDefaultXMLContext(writer, version);

        return writer;
    }

    public CityGMLWriter createCityGMLWriter(Path outputFile, CityGMLBuilder builder) throws CityGMLWriteException {
        CityGMLVersion version = getVersion();

        CityGMLOutputFactory out = builder.createCityGMLOutputFactory(version);
        CityGMLWriter writer = out.createCityGMLWriter(outputFile.toFile());
        setDefaultXMLContext(writer, version);

        return writer;
    }

    public void setDefaultXMLContext(AbstractCityGMLWriter writer) {
        CityGMLVersion version = getVersion();
        setDefaultXMLContext(writer, version);
    }

    private void setDefaultXMLContext(AbstractCityGMLWriter writer, CityGMLVersion version) {
        ModuleContext moduleContext = new ModuleContext(version);
        writer.setPrefixes(moduleContext);
        writer.setSchemaLocations(moduleContext);
        writer.setDefaultNamespace(version.getCityGMLModule(CityGMLModuleType.CORE));
        writer.setIndentString("  ");
    }
}
