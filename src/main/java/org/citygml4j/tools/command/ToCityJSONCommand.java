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

package org.citygml4j.tools.command;

import org.citygml4j.CityGMLContext;
import org.citygml4j.binding.cityjson.metadata.MetadataType;
import org.citygml4j.builder.cityjson.CityJSONBuilder;
import org.citygml4j.builder.cityjson.json.io.writer.CityJSONOutputFactory;
import org.citygml4j.builder.cityjson.json.io.writer.CityJSONWriter;
import org.citygml4j.builder.cityjson.marshal.util.DefaultTextureVerticesBuilder;
import org.citygml4j.builder.cityjson.marshal.util.DefaultVerticesBuilder;
import org.citygml4j.builder.cityjson.marshal.util.DefaultVerticesTransformer;
import org.citygml4j.builder.jaxb.CityGMLBuilderException;
import org.citygml4j.model.citygml.CityGML;
import org.citygml4j.model.citygml.core.AbstractCityObject;
import org.citygml4j.model.citygml.core.CityModel;
import org.citygml4j.model.citygml.core.CityObjectMember;
import org.citygml4j.tools.common.log.Logger;
import org.citygml4j.tools.util.SrsNameParser;
import org.citygml4j.tools.util.SrsParseException;
import org.citygml4j.tools.util.Util;
import org.citygml4j.xml.io.CityGMLInputFactory;
import org.citygml4j.xml.io.reader.CityGMLReader;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(name = "to-cityjson",
        description = "Converts CityGML files into CityJSON.",
        mixinStandardHelpOptions = true)
public class ToCityJSONCommand implements CityGMLTool {

    @CommandLine.Option(names = "--epsg", paramLabel = "<code>", description = "EPSG code to be used as CRS metadata.")
    private int epsg = 0;

    @CommandLine.Option(names = "--vertices-digits", paramLabel = "<digits>", description = "Number of digits to keep for geometry vertices (default: ${DEFAULT-VALUE}).")
    private int verticesDigites = 3;

    @CommandLine.Option(names = "--template-digits", paramLabel = "<digits>", description = "Number of digits to keep for template vertices (default: ${DEFAULT-VALUE}).")
    private int templateDigites = 3;

    @CommandLine.Option(names = "--texture-vertices-digits", paramLabel = "<digits>", description = "Number of digits to keep for texture vertices (default: ${DEFAULT-VALUE}).")
    private int textureVerticesDigites = 7;

    @CommandLine.Option(names = {"-c", "--compress"}, description = "Compress file by storing vertices with integers.")
    private boolean compress;

    @CommandLine.Option(names = "--compress-digits", paramLabel = "<digits>", description = "Number of digits to keep in compression (default: ${DEFAULT-VALUE}).")
    private int compressDigits = 3;

    @CommandLine.Option(names = "--overwrite-files", description = "Overwrite output file(s).")
    private boolean overwriteOutputFiles;

    @CommandLine.Mixin
    private StandardInputOptions input;

    @CommandLine.ParentCommand
    private MainCommand main;

    @Override
    public boolean execute() throws Exception {
        Logger log = Logger.getInstance();
        log.info("Executing command 'to-cityjson'.");

        CityGMLInputFactory in;
        try {
            in = main.getCityGMLBuilder().createCityGMLInputFactory();
        } catch (CityGMLBuilderException e) {
            log.error("Failed to create CityGML input factory", e);
            return false;
        }

        CityJSONBuilder builder = CityGMLContext.getInstance().createCityJSONBuilder();
        CityJSONOutputFactory out = builder.createCityJSONOutputFactory();

        // set builder for geometry, template and texture vertices
        out.setVerticesBuilder(new DefaultVerticesBuilder().withSignificantDigits(verticesDigites));
        out.setTemplatesVerticesBuilder(new DefaultVerticesBuilder().withSignificantDigits(templateDigites));
        out.setTextureVerticesBuilder(new DefaultTextureVerticesBuilder().withSignificantDigits(textureVerticesDigites));

        // apply compression if requested
        if (compress)
            out.setVerticesTransformer(new DefaultVerticesTransformer().withSignificantDigits(compressDigits));

        log.debug("Searching for CityGML input files.");
        List<Path> inputFiles = new ArrayList<>();
        try {
            inputFiles.addAll(Util.listFiles(input.getFile(), "**.{gml,xml}"));
            log.info("Found " + inputFiles.size() + " file(s) at '" + input.getFile() + "'.");
        } catch (IOException e) {
            log.warn("Failed to find file(s) at '" + input.getFile() + "'.");
        }

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file '" + inputFile.toAbsolutePath() + "'.");

            Path outputFile = Util.replaceFileExtension(inputFile, ".json");
            log.info("Writing output to file '" + outputFile.toAbsolutePath() + "'.");

            if (!overwriteOutputFiles && Files.exists(outputFile)) {
                log.error("The output file '" + outputFile.toAbsolutePath() + "' already exists. Remove it first.");
                continue;
            }

            try (CityGMLReader reader = in.createCityGMLReader(inputFile.toFile());
                 CityJSONWriter writer = out.createCityJSONWriter(outputFile.toFile())) {

                log.debug("Reading CityJSON input file into main memory.");
                CityGML cityGML = reader.nextFeature();

                if (cityGML instanceof CityModel) {
                    CityModel cityModel = (CityModel) cityGML;

                    // retrieve metadata
                    writer.setMetadata(getMetadata(cityModel, log));

                    // convert and write city model
                    writer.write(cityModel);
                    log.debug("Successfully converted CityGML file into CityJSON.");
                } else
                    log.error("Failed to find a root CityModel element. Skipping CityGML file.");
            }
        }

        return true;
    }

    private MetadataType getMetadata(CityModel cityModel, Logger log) {
        MetadataType metadata = new MetadataType();

        if (epsg > 0)
            metadata.setReferenceSystem(epsg);
        else {
            String srsName = null;

            if (cityModel.isSetBoundedBy()
                    && cityModel.getBoundedBy().isSetEnvelope()
                    && cityModel.getBoundedBy().getEnvelope().isSetSrsName()) {
                srsName = cityModel.getBoundedBy().getEnvelope().getSrsName();
            } else {
                for (CityObjectMember member : cityModel.getCityObjectMember()) {
                    if (member.isSetCityObject()) {
                        AbstractCityObject cityObject = member.getCityObject();
                        if (cityObject.isSetBoundedBy()
                                && cityObject.getBoundedBy().isSetEnvelope()
                                && cityObject.getBoundedBy().getEnvelope().isSetSrsName()) {
                            String tmp = cityObject.getBoundedBy().getEnvelope().getSrsName();
                            if (srsName == null)
                                srsName = tmp;
                            else if (!srsName.equals(tmp)) {
                                log.debug("Failed to retrieve EPSG code due to multiple CRSs used in the input file.");
                                srsName = null;
                                break;
                            }
                        }
                    }
                }
            }

            if (srsName != null) {
                try {
                    log.debug("Found CRS name '" + srsName + "'.");
                    metadata.setReferenceSystem(new SrsNameParser().getEPSGCode(srsName));
                } catch (SrsParseException e) {
                    log.warn("Failed to retrieve EPSG code from the CRS name '" + srsName + "'.", e);
                }
            }
        }

        return metadata;
    }

    @Override
    public void validate() throws CommandLine.ParameterException {
        // nothing to do
    }
}
