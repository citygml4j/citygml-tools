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

package org.citygml4j.tools.command;

import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.option.OverwriteInputOption;
import org.citygml4j.tools.util.InputFiles;
import org.citygml4j.tools.util.UpgradeProcessor;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.reader.ChunkOptions;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;

@CommandLine.Command(name = "upgrade",
        description = "Upgrades CityGML files to version 3.0.")
public class UpgradeCommand extends CityGMLTool {
    @CommandLine.Option(names = {"-u", "--use-lod4-as-lod3"},
            description = "Use the LoD4 representation of city objects as LoD3, replacing an existing LoD3.")
    private boolean useLod4AsLod3;

    @CommandLine.Option(names = {"-m", "--map-lod1-multi-surfaces"},
            description = "Map the LoD1 multi-surface representation of city objects onto generic thematic surfaces.")
    private boolean mapLod1MultiSurfaces;

    @CommandLine.Option(names = {"-x", "--resolve-cross-lod-references"},
            description = "Resolve geometry references between different LoDs of the same top-level city object.")
    private boolean resolveCrossLodReferences;

    @CommandLine.Option(names = {"-g", "--resolve-geometry-references"},
            description = "Resolve geometry references between top-level city objects.")
    private boolean resolveGeometryReferences;

    @CommandLine.Option(names = {"-l", "--add-object-links"},
            description = "Add CityObjectRelation links between top-level city objects sharing a common geometry. " +
                    "Use only when resolving of geometry between top-level city objects references is enabled.")
    private boolean createCityObjectRelations;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Mixin
    OverwriteInputOption overwriteOption;

    @CommandLine.Mixin
    private InputOptions inputOptions;

    private final String suffix = "__v3";

    @Override
    public Integer call() throws ExecutionException {
        log.debug("Searching for CityGML input files.");
        List<Path> inputFiles = InputFiles.of(inputOptions.getFiles())
                .withFilter(path -> !stripFileExtension(path).endsWith(suffix))
                .find();

        if (inputFiles.isEmpty()) {
            log.warn("No files found at " + inputOptions.joinFiles() + ".");
            return CommandLine.ExitCode.OK;
        }

        log.info("Found " + inputFiles.size() + " file(s) at " + inputOptions.joinFiles() + ".");

        CityGMLInputFactory in = createCityGMLInputFactory().withChunking(ChunkOptions.defaults());
        CityGMLOutputFactory out = createCityGMLOutputFactory(CityGMLVersion.v3_0);

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            Path outputFile = getOutputFile(inputFile, suffix, overwriteOption.isOverwrite());

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile.toAbsolutePath() + ".");

            try (CityGMLReader reader = createSkippingCityGMLReader(in, inputFile, inputOptions, "Appearance")) {
                if (reader.hasNext()) {
                    CityGMLVersion version = CityGMLModules.getCityGMLVersion(reader.getName().getNamespaceURI());
                    if (version == CityGMLVersion.v3_0) {
                        log.info("This is already a CityGML 3.0 file. No action required.");
                        continue;
                    }
                }

                UpgradeProcessor processor = UpgradeProcessor.newInstance()
                        .useLod4AsLod3(useLod4AsLod3)
                        .mapLod1MultiSurfaces(mapLod1MultiSurfaces)
                        .resolveCrossLodReferences(resolveCrossLodReferences)
                        .resolveGeometryReferences(resolveGeometryReferences)
                        .createCityObjectRelations(createCityObjectRelations);

                log.debug("Reading global objects from input file.");
                processor.readGlobalObjects(inputFile, getCityGMLContext());

                if (overwriteOption.isOverwrite()) {
                    log.debug("Writing temporary output file " + outputFile.toAbsolutePath() + ".");
                } else {
                    log.info("Writing output to file " + outputFile.toAbsolutePath() + ".");
                }

                try (CityGMLChunkWriter writer = createCityGMLChunkWriter(out, outputFile, outputOptions)
                        .withCityModelInfo(getFeatureInfo(reader))) {
                    log.debug("Reading city objects and upgrading them to CityGML 3.0.");
                    int featureId = 0;
                    while (reader.hasNext()) {
                        featureId++;
                        AbstractFeature feature = reader.next();
                        processor.upgrade(feature, featureId);
                        writer.writeMember(feature);
                    }

                    processor.postprocess();

                    for (Appearance appearance : processor.getGlobalAppearances()) {
                        writer.writeMember(appearance);
                    }
                }

                printResultStatistics(processor.getResultStatistics());
            } catch (CityGMLReadException e) {
                throw new ExecutionException("Failed to read file " + inputFile.toAbsolutePath() + ".", e);
            } catch (CityGMLWriteException e) {
                throw new ExecutionException("Failed to write file " + outputFile.toAbsolutePath() + ".", e);
            }

            if (overwriteOption.isOverwrite()) {
                log.debug("Replacing input file with temporary output file.");
                replaceInputFile(inputFile, outputFile);
            }
        }

        return CommandLine.ExitCode.OK;
    }

    private void printResultStatistics(UpgradeProcessor.ResultStatistics resultStatistics) {
        if (resolveGeometryReferences) {
            if (resultStatistics.getResolvedCrossTopLevelReferences() > 0) {
                log.debug("Resolved geometry references between top-level city objects: "
                        + resultStatistics.getResolvedCrossTopLevelReferences());

                if (createCityObjectRelations) {
                    log.debug("Created city object relations: " + resultStatistics.getCreatedCityObjectRelations());
                }
            } else {
                log.debug("The input file has no geometry references between top-level city objects.");
            }
        }

        if (resolveCrossLodReferences) {
            if (resultStatistics.getResolvedCrossLodReferences() > 0) {
                log.debug("Resolved cross-LoD geometry references: " + resultStatistics.getResolvedCrossLodReferences());
            } else {
                log.debug("The input file has no cross-level geometry references.");
            }
        } else if (resultStatistics.getRemovedCrossLodReferences() > 0) {
            log.debug("Removed cross-LoD geometry references: " + resultStatistics.getRemovedCrossLodReferences());
        }
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (createCityObjectRelations && !resolveGeometryReferences) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --create-object-relations can only be used together with --resolve-geometry-references");
        }
    }
}
