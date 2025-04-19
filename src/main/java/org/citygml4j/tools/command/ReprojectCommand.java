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
        description = "Reproject city objects to a new coordinate reference system.")
public class ReprojectCommand extends CityGMLTool {
    @CommandLine.Option(names = {"-t", "--target-crs"}, paramLabel = "<crs>", required = true,
            description = "Target CRS as EPSG code, or in OGC URL, OGC URN, or OGC Well-Known Text (WKT) format.")
    private String targetCRS;

    @CommandLine.Option(names = {"-c", "--crs-name"}, paramLabel = "<name>",
            description = "Name of the target CRS for the gml:srsName attribute in the output.")
    private String targetName;

    @CommandLine.Option(names = {"-f", "--force-lon-lat"},
            description = "Force target CRS axis order to longitude, latitude.")
    private boolean forceLongitudeFirst;

    @CommandLine.Option(names = {"-s", "--source-crs"}, paramLabel = "<crs>",
            description = "Source CRS as EPSG code, or in OGC URL, OGC URN, or OGC Well-Known Text (WKT) format. " +
                    "Overrides any reference system in the input files if specified.")
    private String sourceCRS;

    @CommandLine.Option(names = "--swap-axis-order",
            description = "Swap X and Y axes for all input geometries before reprojection.")
    private boolean swapAxisOrder;

    @CommandLine.Option(names = "--keep-height-values",
            description = "Do not transform height values.")
    private boolean keepHeightValues;

    @CommandLine.Option(names = "--lenient-transform",
            description = "Allow transformation even without datum shift information.")
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
        List<InputFile> inputFiles = getInputFiles(inputOptions, suffix);
        if (inputFiles.isEmpty()) {
            return CommandLine.ExitCode.OK;
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
                replaceInputFile(inputFile, outputFile);
            }
        }

        return CommandLine.ExitCode.OK;
    }
}
