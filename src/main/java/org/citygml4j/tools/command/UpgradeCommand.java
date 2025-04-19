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

import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.tools.io.OutputFile;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.option.OverwriteInputOptions;
import org.citygml4j.tools.util.ResourceProcessor;
import org.citygml4j.tools.util.UpgradeProcessor;
import org.citygml4j.xml.reader.ChunkOptions;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(name = "upgrade",
        description = "Upgrade CityGML files to version 3.0.")
public class UpgradeCommand extends CityGMLTool {
    @CommandLine.Mixin
    private InputOptions inputOptions;

    @CommandLine.Option(names = {"-l", "--use-lod4-as-lod3"},
            description = "Use LoD4 as LoD3, replacing an existing LoD3.")
    private boolean useLod4AsLod3;

    @CommandLine.Option(names = {"-r", "--map-lod0-roof-edge"},
            description = "Map LoD0 roof edges onto roof surfaces.")
    private boolean mapLod0RoofEdge;

    @CommandLine.Option(names = {"-s", "--map-lod1-surface"},
            description = "Map LoD1 multi-surfaces onto generic thematic surfaces.")
    private boolean mapLod1MultiSurfaces;

    @CommandLine.Option(names = "--no-resolve-cross-lod-references", negatable = true, defaultValue = "true",
            description = "Resolve geometry references across LoDs of the same top-level city object " +
                    "(default: ${DEFAULT-VALUE}).")
    private boolean resolveCrossLodReferences;

    @CommandLine.Option(names = "--no-resolve-geometry-references", negatable = true, defaultValue = "true",
            description = "Resolve geometry references between top-level city objects (default: ${DEFAULT-VALUE}).")
    private boolean resolveGeometryReferences;

    @CommandLine.Option(names = {"-a", "--add-object-relations"},
            description = "Add CityObjectRelation links between top-level city objects sharing a common geometry. " +
                    "Use only when resolving geometry references is enabled.")
    private boolean createCityObjectRelations;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Mixin
    private OverwriteInputOptions overwriteOptions;

    private final String suffix = "__v3";

    @Override
    public Integer call() throws ExecutionException {
        List<InputFile> inputFiles = getInputFiles(inputOptions, suffix);
        if (inputFiles.isEmpty()) {
            return CommandLine.ExitCode.OK;
        }

        CityGMLInputFactory in = createCityGMLInputFactory().withChunking(ChunkOptions.defaults());
        CityGMLOutputFactory out = createCityGMLOutputFactory(CityGMLVersion.v3_0);

        for (int i = 0; i < inputFiles.size(); i++) {
            InputFile inputFile = inputFiles.get(i);
            OutputFile outputFile = getOutputFile(inputFile, suffix, outputOptions, overwriteOptions);

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile + ".");

            try (CityGMLReader reader = createSkippingCityGMLReader(in, inputFile, inputOptions);
                 ResourceProcessor resourceProcessor = ResourceProcessor.of(inputFile, outputFile)) {
                UpgradeProcessor processor = UpgradeProcessor.newInstance()
                        .useLod4AsLod3(useLod4AsLod3)
                        .mapLod0RoofEdge(mapLod0RoofEdge)
                        .mapLod1MultiSurfaces(mapLod1MultiSurfaces)
                        .resolveCrossLodReferences(resolveCrossLodReferences)
                        .resolveGeometryReferences(resolveGeometryReferences)
                        .createCityObjectRelations(createCityObjectRelations);

                log.debug("Reading global objects from input file.");
                processor.readGlobalObjects(inputFile, getCityGMLContext());

                if (outputFile.isTemporary()) {
                    log.debug("Writing temporary output file " + outputFile + ".");
                } else {
                    log.info("Writing output to file " + outputFile + ".");
                }

                try (CityGMLChunkWriter writer = createCityGMLChunkWriter(out, outputFile, outputOptions)
                        .withCityModelInfo(getFeatureInfo(reader))) {
                    log.debug("Reading city objects and upgrading them to CityGML 3.0.");
                    int featureId = 0;
                    while (reader.hasNext()) {
                        featureId++;
                        AbstractFeature feature = reader.next();
                        if (processor.upgrade(feature, featureId)) {
                            resourceProcessor.process(feature);
                            writer.writeMember(feature);
                        }
                    }

                    processor.postprocess();

                    for (CityObjectGroup cityObjectGroup : processor.getCityObjectGroups()) {
                        resourceProcessor.process(cityObjectGroup);
                        writer.writeMember(cityObjectGroup);
                    }

                    for (Appearance appearance : processor.getGlobalAppearances()) {
                        resourceProcessor.process(appearance);
                        writer.writeMember(appearance);
                    }
                }

                printResultStatistics(processor.getResultStatistics());
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
                    "Error: --add-object-relations can only be used together with --resolve-geometry-references");
        }
    }
}
