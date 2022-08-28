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

import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.option.*;
import org.citygml4j.tools.util.GlobalObjects;
import org.citygml4j.tools.util.GlobalObjectsReader;
import org.citygml4j.tools.util.InputFiles;
import org.citygml4j.tools.util.SubsetProcessor;
import org.citygml4j.xml.reader.*;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;

@CommandLine.Command(name = "subset",
        description = "Creates a subset of city objects based on filter criteria.")
public class SubsetCommand extends CityGMLTool {
    @CommandLine.ArgGroup(exclusive = false)
    private TypeNamesOption typeNamesOption;

    @CommandLine.ArgGroup
    private IdOption idOption;

    @CommandLine.ArgGroup(exclusive = false)
    private BoundingBoxOption boundingBoxOption;

    @CommandLine.Option(names = "--invert",
            description = "Invert the filter criteria.")
    private boolean invert;

    @CommandLine.ArgGroup(exclusive = false)
    private CounterOption counterOption;

    @CommandLine.Option(names = "--no-remove-group-members", negatable = true, defaultValue = "true",
            description = "Remove group members that do not satisfy the filter criteria from city object groups " +
                    "(default: ${DEFAULT-VALUE}).")
    private boolean removeGroupMembers;

    @CommandLine.Mixin
    private CityGMLOutputVersion version;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Mixin
    OverwriteInputOption overwriteOption;

    @CommandLine.Mixin
    private InputOptions inputOptions;

    private final String suffix = "__subset";

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
        CityGMLOutputFactory out = createCityGMLOutputFactory(version.getVersion());

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            Path outputFile = getOutputFile(inputFile, suffix, overwriteOption.isOverwrite());

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile.toAbsolutePath() + ".");

            log.debug("Reading global appearances, groups and implicit geometries from input file.");
            GlobalObjects globalObjects = GlobalObjectsReader.defaults()
                    .read(inputFile, getCityGMLContext());

            SubsetProcessor subsetProcessor = SubsetProcessor.newInstance()
                    .withGlobalObjects(globalObjects)
                    .withTypeNamesFilter(typeNamesOption, getCityGMLContext())
                    .withIdFilter(idOption)
                    .withBoundingBoxFilter(boundingBoxOption != null ? boundingBoxOption.toBoundingBoxFilter() : null)
                    .invertFilterCriteria(invert)
                    .withCounterOption(counterOption)
                    .removeGroupMembers(removeGroupMembers);

            try (CityGMLReader reader = createSkippingCityGMLReader(in, inputFile, inputOptions,
                    "CityObjectGroup", "Appearance")) {
                FeatureInfo cityModelInfo = getFeatureInfo(reader);
                if (cityModelInfo != null && subsetProcessor.getBoundingBoxFilter() != null) {
                    subsetProcessor.getBoundingBoxFilter().withRootReferenceSystem(cityModelInfo);
                }

                if (!version.isSetVersion()) {
                    setCityGMLVersion(reader, out);
                }

                if (overwriteOption.isOverwrite()) {
                    log.debug("Writing temporary output file " + outputFile.toAbsolutePath() + ".");
                } else {
                    log.info("Writing output to file " + outputFile.toAbsolutePath() + ".");
                }

                try (CityGMLChunkWriter writer = createCityGMLChunkWriter(out, outputFile, outputOptions)
                        .withCityModelInfo(cityModelInfo)) {
                    log.debug("Reading and filtering city objects based on the specified filter criteria.");
                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        if (subsetProcessor.filter(feature, reader.getName(), reader.getPrefix())) {
                            writer.writeMember(feature);
                        }
                    }

                    subsetProcessor.postprocess();

                    for (CityObjectGroup group : globalObjects.getCityObjectGroups()) {
                        writer.writeMember(group);
                    }

                    for (Appearance appearance : globalObjects.getAppearances()) {
                        writer.writeMember(appearance);
                    }
                }
            } catch (CityGMLReadException e) {
                throw new ExecutionException("Failed to read file " + inputFile.toAbsolutePath() + ".", e);
            } catch (CityGMLWriteException e) {
                throw new ExecutionException("Failed to write file " + outputFile.toAbsolutePath() + ".", e);
            }

            if (overwriteOption.isOverwrite()) {
                log.debug("Replacing input file with temporary output file.");
                replaceInputFile(inputFile, outputFile);
            }

            if (!subsetProcessor.getCounter().isEmpty()) {
                log.debug("The following top-level city objects satisfied the filter criteria.");
                subsetProcessor.getCounter().forEach((key, value) -> log.debug(key + ": " + value));
            } else {
                log.debug("No top-level city object satisfies the filter criteria.");
            }
        }

        return CommandLine.ExitCode.OK;
    }
}
