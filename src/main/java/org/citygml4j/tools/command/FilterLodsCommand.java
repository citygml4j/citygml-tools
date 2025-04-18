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

import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.tools.io.OutputFile;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.CityGMLOutputVersion;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.option.OverwriteInputOptions;
import org.citygml4j.tools.util.GlobalObjects;
import org.citygml4j.tools.util.GlobalObjectsReader;
import org.citygml4j.tools.util.LodFilter;
import org.citygml4j.tools.util.ResourceProcessor;
import org.citygml4j.xml.reader.ChunkOptions;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(name = "filter-lods",
        description = "Filters LoD representations of city objects.")
public class FilterLodsCommand extends CityGMLTool {
    @CommandLine.Option(names = {"-l", "--lod"}, required = true, split = ",", paramLabel = "<0..4>",
            description = "LoD representations to filter.")
    private int[] lods;

    @CommandLine.Option(names = {"-m", "--mode"}, defaultValue = "keep",
            description = "Filter mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}). The matching " +
                    "minimum or maximum LoD is determined per top-level city object.")
    private LodFilter.Mode mode;

    @CommandLine.Option(names = {"-k", "--keep-empty-objects"},
            description = "Keep city objects even if all their LoD representations have been removed.")
    private boolean keepEmptyObjects;

    @CommandLine.Option(names = "--no-update-extents", negatable = true, defaultValue = "true",
            description = "Update the extents of city objects for which LoD representations have been removed. " +
                    "No coordinate transformation is applied in the calculation (default: ${DEFAULT-VALUE}).")
    private boolean updateExtents;

    @CommandLine.Mixin
    private CityGMLOutputVersion version;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Mixin
    private OverwriteInputOptions overwriteOptions;

    @CommandLine.Mixin
    private InputOptions inputOptions;

    private final String suffix = "__filtered_lods";

    @Override
    public Integer call() throws ExecutionException {
        List<InputFile> inputFiles = getInputFiles(inputOptions, suffix);
        if (inputFiles.isEmpty()) {
            return CommandLine.ExitCode.OK;
        }

        CityGMLInputFactory in = createCityGMLInputFactory().withChunking(ChunkOptions.defaults());
        CityGMLOutputFactory out = createCityGMLOutputFactory(version.getVersion());

        for (int i = 0; i < inputFiles.size(); i++) {
            InputFile inputFile = inputFiles.get(i);
            OutputFile outputFile = getOutputFile(inputFile, suffix, outputOptions, overwriteOptions);

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile + ".");

            log.debug("Reading global appearances, groups and implicit geometries from input file.");
            GlobalObjects globalObjects = GlobalObjectsReader.defaults()
                    .read(inputFile, getCityGMLContext());

            LodFilter lodFilter = LodFilter.of(lods)
                    .withMode(mode)
                    .withGlobalAppearances(globalObjects.getAppearances())
                    .withCityObjectGroups(globalObjects.getCityObjectGroups())
                    .withTemplateGeometries(globalObjects.getTemplateGeometries())
                    .updateExtents(updateExtents)
                    .withFeatureMode(keepEmptyObjects ?
                            LodFilter.FeatureMode.KEEP_EMPTY_FEATURES :
                            LodFilter.FeatureMode.DELETE_EMPTY_FEATURES);

            try (CityGMLReader reader = createSkippingCityGMLReader(in, inputFile, inputOptions,
                    "CityObjectGroup", "Appearance");
                 ResourceProcessor resourceProcessor = ResourceProcessor.of(inputFile, outputFile)) {
                if (!version.isSetVersion()) {
                    setCityGMLVersion(reader, out);
                }

                if (outputFile.isTemporary()) {
                    log.debug("Writing temporary output file " + outputFile + ".");
                } else {
                    log.info("Writing output to file " + outputFile + ".");
                }

                try (CityGMLChunkWriter writer = createCityGMLChunkWriter(out, outputFile, outputOptions)
                        .withCityModelInfo(getFeatureInfo(reader))) {
                    log.debug("Reading city objects and filtering LoD representations.");
                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        if (lodFilter.filter(feature)) {
                            resourceProcessor.process(feature);
                            writer.writeMember(feature);
                        }
                    }

                    lodFilter.postprocess();

                    for (CityObjectGroup group : globalObjects.getCityObjectGroups()) {
                        resourceProcessor.process(group);
                        writer.writeMember(group);
                    }

                    for (Appearance appearance : globalObjects.getAppearances()) {
                        resourceProcessor.process(appearance);
                        writer.writeMember(appearance);
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

    @Override
    public void preprocess(CommandLine commandLine) {
        for (int lod : lods) {
            if (lod < 0 || lod > 4) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: An LoD value must be between 0 and 4 but was '" + lod + "'");
            }
        }
    }
}
