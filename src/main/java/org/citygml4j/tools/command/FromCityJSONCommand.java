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
import org.citygml4j.builder.cityjson.CityJSONBuilderException;
import org.citygml4j.builder.cityjson.json.io.reader.CityJSONInputFactory;
import org.citygml4j.builder.cityjson.json.io.reader.CityJSONReadException;
import org.citygml4j.builder.cityjson.json.io.reader.CityJSONReader;
import org.citygml4j.model.citygml.core.CityModel;
import org.citygml4j.tools.common.log.Logger;
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
        versionProvider = MainCommand.class,
        mixinStandardHelpOptions = true)
public class FromCityJSONCommand implements CityGMLTool {

    @CommandLine.Option(names = "--map-unknown-extensions", description = "Map unknown extensions to generic city objects and attributes.")
    private boolean mapUnknwonExtensions;

    @CommandLine.Mixin
    private StandardCityGMLOutputOptions cityGMLOutput;

    @CommandLine.Mixin
    private StandardInputOptions input;

    @CommandLine.ParentCommand
    private MainCommand main;

    @Override
    public boolean execute() throws Exception {
        Logger log = Logger.getInstance();

        CityJSONInputFactory in;
        try {
            in = input.createCityJSONInputFactory();
            if (mapUnknwonExtensions) {
                log.debug("Mapping unknown extensions to generic city objects and attributes.");
                in.setProcessUnknownExtensions(mapUnknwonExtensions);
            }
        } catch (CityJSONBuilderException e) {
            log.error("Failed to create CityJSON input factory.", e);
            return false;
        }

        log.debug("Searching for CityJSON input files.");
        List<Path> inputFiles = new ArrayList<>();
        try {
            inputFiles.addAll(Util.listFiles(input.getFile(), "**.{json,cityjson}"));
            log.info("Found " + inputFiles.size() + " file(s) at '" + input.getFile() + "'.");
        } catch (IOException e) {
            log.warn("Failed to find file(s) at '" + input.getFile() + "'.");
        }

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file '" + inputFile.toAbsolutePath() + "'.");

            Path outputFile = outputFile = Util.replaceFileExtension(inputFile, ".gml");
            log.info("Writing output to file '" + outputFile.toAbsolutePath() + "'.");

            CityModel cityModel;
            try (CityJSONReader reader = in.createCityJSONReader(inputFile.toFile())) {
                log.debug("Reading CityJSON input file into main memory.");
                cityModel = reader.read();
            } catch (CityJSONReadException e) {
                log.error("Failed to read CityJSON file.", e);
                if (e.getCause() instanceof JsonSyntaxException)
                    log.error("Maybe an unsupported CityJSON version?");

                return false;
            }

            try (CityGMLWriter writer = cityGMLOutput.createCityGMLWriter(outputFile, main.getCityGMLBuilder())) {
                writer.write(cityModel);
            } catch (CityGMLWriteException e) {
                log.error("Failed to write CityGML file.", e);
                return false;
            }

            log.debug("Successfully converted CityJSON file into CityGML.");
        }

        return true;
    }
}
