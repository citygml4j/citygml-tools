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

import com.google.gson.JsonSyntaxException;
import org.citygml4j.builder.cityjson.json.io.reader.CityJSONReadException;
import org.citygml4j.builder.cityjson.json.io.reader.CityJSONReader;
import org.citygml4j.builder.jaxb.CityGMLBuilder;
import org.citygml4j.model.citygml.core.CityModel;
import org.citygml4j.tools.CityGMLTools;
import org.citygml4j.tools.command.options.CityGMLOutputOptions;
import org.citygml4j.tools.command.options.InputOptions;
import org.citygml4j.tools.common.log.Logger;
import org.citygml4j.tools.util.ObjectRegistry;
import org.citygml4j.tools.util.Util;
import org.citygml4j.xml.io.writer.CityGMLWriteException;
import org.citygml4j.xml.io.writer.CityGMLWriter;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(name = "from-cityjson",
        description = "Converts CityJSON files into CityGML.",
        versionProvider = CityGMLTools.class,
        mixinStandardHelpOptions = true,
        showAtFileInUsageHelp = true)
public class FromCityJSONCommand implements CityGMLTool {
    @CommandLine.Option(names = "--map-unknown-extensions", description = "Map unknown extensions to generic city objects and attributes.")
    private boolean mapUnknownExtensions;

    @CommandLine.Mixin
    private CityGMLOutputOptions cityGMLOutput;

    @CommandLine.Mixin
    private InputOptions input;

    @Override
    public Integer call() throws Exception {
        Logger log = Logger.getInstance();
        CityGMLBuilder cityGMLBuilder = ObjectRegistry.getInstance().get(CityGMLBuilder.class);

        log.debug("Searching for CityJSON input files.");
        List<Path> inputFiles;
        try {
            inputFiles = new ArrayList<>(Util.listFiles(input.getFile(), "**.{json,cityjson}"));
            log.info("Found " + inputFiles.size() + " file(s) at '" + input.getFile() + "'.");
        } catch (IOException e) {
            log.warn("Failed to find file(s) at '" + input.getFile() + "'.");
            return 0;
        }

        if (mapUnknownExtensions)
            log.debug("Mapping unknown extensions to generic city objects and attributes.");

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file '" + inputFile.toAbsolutePath() + "'.");

            Path outputFile = Util.replaceFileExtension(inputFile, ".gml");
            log.info("Writing output to file '" + outputFile.toAbsolutePath() + "'.");

            CityModel cityModel;
            try (CityJSONReader reader = input.createCityJSONReader(inputFile, mapUnknownExtensions)) {
                log.debug("Reading CityJSON input file into main memory.");
                cityModel = reader.read();
            } catch (CityJSONReadException e) {
                log.error("Failed to read CityJSON file.", e);
                if (e.getCause() instanceof JsonSyntaxException)
                    log.error("Maybe an unsupported CityJSON version?");

                return 1;
            }

            try (CityGMLWriter writer = cityGMLOutput.createCityGMLWriter(outputFile)) {
                writer.write(cityModel);
            } catch (CityGMLWriteException e) {
                log.error("Failed to write CityGML file.", e);
                return 1;
            }

            log.debug("Successfully converted CityJSON file into CityGML.");
        }

        return 0;
    }
}
