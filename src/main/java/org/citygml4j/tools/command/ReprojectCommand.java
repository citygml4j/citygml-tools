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

import org.citygml4j.builder.jaxb.CityGMLBuilderException;
import org.citygml4j.model.citygml.CityGML;
import org.citygml4j.model.gml.feature.AbstractFeature;
import org.citygml4j.tools.CityGMLTools;
import org.citygml4j.tools.common.helper.CityModelInfoHelper;
import org.citygml4j.tools.common.log.LogLevel;
import org.citygml4j.tools.common.log.Logger;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.option.LoggingOptions;
import org.citygml4j.tools.reproject.ReprojectionBuilder;
import org.citygml4j.tools.reproject.ReprojectionBuilderException;
import org.citygml4j.tools.reproject.ReprojectionException;
import org.citygml4j.tools.reproject.Reprojector;
import org.citygml4j.tools.util.Util;
import org.citygml4j.xml.io.reader.CityGMLReadException;
import org.citygml4j.xml.io.reader.CityGMLReader;
import org.citygml4j.xml.io.writer.CityGMLWriteException;
import org.citygml4j.xml.io.writer.CityModelInfo;
import org.citygml4j.xml.io.writer.CityModelWriter;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@CommandLine.Command(name = "reproject",
        description = "Reprojects city objects to a new spatial reference system.",
        versionProvider = CityGMLTools.class,
        mixinStandardHelpOptions = true,
        showAtFileInUsageHelp = true)
public class ReprojectCommand implements CityGMLTool {
    @CommandLine.Option(names = "--target-crs", paramLabel = "<crs>", required = true, description = "Target CRS for the reprojection given as EPSG code, as GML srsName or as OGC WKT with escaped quotes.")
    private String targetCRS;

    @CommandLine.Option(names = "--target-name", paramLabel = "<name>", description = "GML srsName to be used in the output file.")
    private String targetSRSName;

    @CommandLine.Option(names = "--target-force-xy", description = "Force XY axis order for target CRS.")
    private boolean targetForceXY;

    @CommandLine.Option(names = "--keep-height-values", description = "Do not reproject height values.")
    private boolean keepHeightValues;

    @CommandLine.Option(names = "--source-crs", paramLabel = "<crs>", description = "If provided, the source CRS overrides any reference system in the input file. Given as EPSG code, as GML srsName or as OGC WKT with escaped quotes.")
    private String sourceCRS;

    @CommandLine.Option(names = "--source-swap-xy", description = "Swap XY axes for all geometries in the input file.")
    private boolean sourceSwapXY;

    @CommandLine.Option(names = "--overwrite-files", description = "Overwrite input file(s).")
    private boolean overwriteInputFiles;

    @CommandLine.Mixin
    private CityGMLOutputOptions cityGMLOutput;

    @CommandLine.Mixin
    private InputOptions input;

    @CommandLine.Mixin
    LoggingOptions logging;

    @Override
    public Integer call() throws Exception {
        Logger log = Logger.getInstance();
        String fileNameSuffix = "_reprojected";

        log.debug("Searching for CityGML input files.");
        List<Path> inputFiles;
        try {
            inputFiles = new ArrayList<>(Util.listFiles(input.getFile(), "**.{gml,xml}", fileNameSuffix));
            log.info("Found " + inputFiles.size() + " file(s) at '" + input.getFile() + "'.");
        } catch (IOException e) {
            log.warn("Failed to find file(s) at '" + input.getFile() + "'.");
            return 0;
        }

        Reprojector reprojector;
        try {
            reprojector = ReprojectionBuilder.defaults()
                    .withTargetCRS(targetCRS)
                    .withTargetSRSName(targetSRSName)
                    .withSourceCRS(sourceCRS)
                    .forceXYAxisOrderForTargetCRS(targetForceXY)
                    .keepHeightValues(keepHeightValues)
                    .swapXYAxisOrderForSourceGeometries(sourceSwapXY)
                    .build();

            log.debug("Using the following target CRS definition:");
            log.print(LogLevel.DEBUG, reprojector.getTargetCRSAsWKT());

        } catch (ReprojectionBuilderException e) {
            log.error("Failed to create reprojection configuration.", e);
            return 1;
        }

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file '" + inputFile.toAbsolutePath() + "'.");

            Path outputFile;
            if (!overwriteInputFiles) {
                outputFile = Util.addFileNameSuffix(inputFile, fileNameSuffix);
                log.info("Writing output to file '" + outputFile.toAbsolutePath() + "'.");
            } else {
                outputFile = inputFile.resolveSibling("tmp-" + UUID.randomUUID());
                log.debug("Writing temporary output file '" + outputFile.toAbsolutePath() + "'.");
            }

            log.debug("Reading city objects from input file and reprojecting coordinates.");

            try (CityGMLReader reader = input.createCityGMLReader(inputFile, input.createSkipFilter("CityModel"));
                 CityModelWriter writer = cityGMLOutput.createCityModelWriter(outputFile)) {
                boolean isInitialized = false;

                while (reader.hasNext()) {
                    CityGML cityGML = reader.nextFeature();

                    if (!isInitialized) {
                        CityModelInfo cityModelInfo = CityModelInfoHelper.getCityModelInfo(cityGML, reader.getParentInfo());

                        if (cityModelInfo.isSetBoundedBy()) {
                            if (cityModelInfo.getBoundedBy().isSetEnvelope()
                                    &&cityModelInfo.getBoundedBy().getEnvelope().isSetSrsName())
                                reprojector.setFallbackSRSName(cityModelInfo.getBoundedBy().getEnvelope().getSrsName());

                            reprojector.reproject(cityModelInfo.getBoundedBy());
                        }

                        writer.setCityModelInfo(cityModelInfo);
                        writer.writeStartDocument();
                        isInitialized = true;
                    }

                    if (cityGML instanceof AbstractFeature) {
                        AbstractFeature feature = (AbstractFeature) cityGML;
                        reprojector.reproject(feature);
                        writer.writeFeatureMember(feature);
                    }
                }
            } catch (ReprojectionException e) {
                log.error("Failed to reproject city objects.", e);
                return 1;
            } catch (CityGMLBuilderException | CityGMLReadException e) {
                log.error("Failed to read city objects.", e);
                return 1;
            } catch (CityGMLWriteException e) {
                log.error("Failed to write city objects.", e);
                return 1;
            }

            if (overwriteInputFiles) {
                try {
                    log.debug("Replacing input file with temporary file.");
                    Files.delete(inputFile);
                    Files.move(outputFile, outputFile.resolveSibling(inputFile.getFileName()));
                } catch (IOException e) {
                    log.error("Failed to overwrite input file.", e);
                    return 1;
                }
            }
        }

        return 0;
    }
}
