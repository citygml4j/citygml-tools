/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2025 Claus Nagel <claus.nagel@gmail.com>
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

import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.tools.io.InputFiles;
import org.citygml4j.tools.io.OutputFile;
import org.citygml4j.tools.log.LogLevel;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.CityGMLOutputVersion;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.option.OverwriteInputOptions;
import org.citygml4j.tools.util.Reprojector;
import org.citygml4j.tools.util.ResourceProcessor;
import org.citygml4j.xml.reader.*;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(name = "reproject",
        description = "Reprojects city objects to a new coordinate reference system.")
public class ReprojectCommand extends CityGMLTool {
    @CommandLine.Option(names = {"-t", "--target-crs"}, paramLabel = "<crs>", required = true,
            description = "Target CRS given as either EPSG code, OGC URL, OGC URN, or OGC Well-Known Text (WKT).")
    private String targetCRS;

    @CommandLine.Option(names = {"-n", "--target-name"}, paramLabel = "<name>",
            description = "Name of the target CRS to use as gml:srsName attribute in the output file(s).")
    private String targetName;

    @CommandLine.Option(names = {"-l", "--target-longitude-first"},
            description = "Force axis order of the target CRS to longitude, latitude.")
    private boolean forceLongitudeFirst;

    @CommandLine.Option(names = {"-s", "--source-crs"}, paramLabel = "<crs>",
            description = "Source CRS given as either EPSG code, OGC URL, OGC URN, or OGC Well-Known Text (WKT). " +
                    "If specified, the source CRS takes precedence over any reference systems defined in the " +
                    "input file(s).")
    private String sourceCRS;

    @CommandLine.Option(names = "--source-swap-axis-order",
            description = "Swap X and Y axes for all input geometries before performing the reprojection.")
    private boolean swapAxisOrder;

    @CommandLine.Option(names = "--keep-height-values",
            description = "Do not transform height values.")
    private boolean keepHeightValues;

    @CommandLine.Option(names = "--lenient-transform",
            description = "Perform transformation even when there is no information available for a datum shift.")
    private boolean lenientTransform;

    @CommandLine.Mixin
    private CityGMLOutputVersion version;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Mixin
    OverwriteInputOptions overwriteOptions;

    @CommandLine.Mixin
    private InputOptions inputOptions;

    private final String suffix = "__reprojected";

    @Override
    public Integer call() throws ExecutionException {
        log.debug("Searching for CityGML input files.");
        List<InputFile> inputFiles = InputFiles.of(inputOptions.getFile())
                .withFilter(path -> !stripFileExtension(path).endsWith(suffix))
                .find();

        if (inputFiles.isEmpty()) {
            log.warn("No files found at " + inputOptions.getFile() + ".");
            return CommandLine.ExitCode.OK;
        } else if (inputFiles.size() > 1) {
            log.info("Found " + inputFiles.size() + " file(s) at " + inputOptions.getFile() + ".");
        }

        CityGMLInputFactory in = createCityGMLInputFactory().withChunking(ChunkOptions.defaults());
        CityGMLOutputFactory out = createCityGMLOutputFactory(version.getVersion());

        Reprojector reprojector = Reprojector.of(targetCRS, forceLongitudeFirst)
                .withTargetName(targetName)
                .withSourceCRS(sourceCRS)
                .swapAxisOrder(swapAxisOrder)
                .keepHeightValues(keepHeightValues)
                .lenientTransform(lenientTransform);

        log.debug("Using the following target CRS definition:");
        log.log(LogLevel.DEBUG, reprojector.getTargetCRS().getCRS().toString());

        for (int i = 0; i < inputFiles.size(); i++) {
            InputFile inputFile = inputFiles.get(i);
            OutputFile outputFile = getOutputFile(inputFile, suffix, outputOptions, overwriteOptions);

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile + ".");

            try (CityGMLReader reader = createCityGMLReader(in, inputFile, inputOptions);
                 ResourceProcessor resourceProcessor = ResourceProcessor.of(inputFile, outputFile)) {
                FeatureInfo cityModelInfo = getFeatureInfo(reader);
                if (cityModelInfo != null) {
                    reprojector.withCityModelInfo(cityModelInfo);
                }

                if (!version.isSetVersion()) {
                    setCityGMLVersion(reader, out);
                }

                if (outputFile.isTemporary()) {
                    log.debug("Writing temporary output file " + outputFile + ".");
                } else {
                    log.info("Writing output to file " + outputFile + ".");
                }

                try (CityGMLChunkWriter writer = createCityGMLChunkWriter(out, outputFile, outputOptions)
                        .withCityModelInfo(cityModelInfo)) {
                    log.debug("Reading city objects and transforming coordinates to " + reprojector.getTargetName() + ".");
                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        reprojector.reproject(feature);
                        resourceProcessor.process(feature);
                        writer.writeMember(feature);
                    }
                }
            } catch (CityGMLReadException e) {
                throw new ExecutionException("Failed to read file " + inputFile + ".", e);
            } catch (CityGMLWriteException e) {
                throw new ExecutionException("Failed to write file " + outputFile + ".", e);
            }

            if (outputFile.isTemporary()) {
                log.debug("Replacing input file with temporary output file.");
                replaceInputFile(inputFile, outputFile);
            }
        }

        return CommandLine.ExitCode.OK;
    }
}
