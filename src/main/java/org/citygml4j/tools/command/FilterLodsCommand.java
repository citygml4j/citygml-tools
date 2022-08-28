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
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.CityGMLOutputVersion;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.option.OverwriteInputOption;
import org.citygml4j.tools.util.CityObjects;
import org.citygml4j.tools.util.GlobalObjectsReader;
import org.citygml4j.tools.util.InputFiles;
import org.citygml4j.tools.util.LodFilter;
import org.citygml4j.xml.reader.ChunkOptions;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
            description = "Keep city objects even if all their LoD representations have been filtered.")
    private boolean keepEmptyObjects;

    @CommandLine.Mixin
    private CityGMLOutputVersion version;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Mixin
    OverwriteInputOption overwriteOption;

    @CommandLine.Mixin
    private InputOptions inputOptions;

    private final String suffix = "__filtered_lods";

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

            log.debug("Reading global appearances from input file.");
            List<CityObjectGroup> groups = new ArrayList<>();
            List<Appearance> appearances = GlobalObjectsReader.onlyAppearances()
                    .read(inputFile, getCityGMLContext())
                    .getAppearances();

            LodFilter lodFilter = LodFilter.of(lods)
                    .withMode(mode)
                    .withGlobalAppearances(appearances)
                    .keepEmptyObjects(keepEmptyObjects);

            try (CityGMLReader reader = createSkippingCityGMLReader(in, inputFile, inputOptions, "Appearance")) {
                if (!version.isSetVersion()) {
                    setCityGMLVersion(reader, out);
                }

                if (overwriteOption.isOverwrite()) {
                    log.debug("Writing temporary output file " + outputFile.toAbsolutePath() + ".");
                } else {
                    log.info("Writing output to file " + outputFile.toAbsolutePath() + ".");
                }

                try (CityGMLChunkWriter writer = createCityGMLChunkWriter(out, outputFile, outputOptions)
                        .withCityModelInfo(getFeatureInfo(reader))) {
                    log.debug("Reading city objects and filtering LoD representations.");
                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        boolean keep = lodFilter.filter(feature);

                        if (feature instanceof CityObjectGroup) {
                            groups.add((CityObjectGroup) feature);
                        } else if (keep) {
                            writer.writeMember(feature);
                        }
                    }

                    lodFilter.postprocess();

                    if (!groups.isEmpty()) {
                        postprocess(groups, lodFilter.getRemovedFeatureIds());
                        for (CityObjectGroup group : groups) {
                            writer.writeMember(group);
                        }
                    }

                    for (Appearance appearance : appearances) {
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
        }

        return CommandLine.ExitCode.OK;
    }

    private void postprocess(List<CityObjectGroup> groups, Set<String> removedFeatureIds) {
        if (!removedFeatureIds.isEmpty()) {
            for (CityObjectGroup group : groups) {
                if (group.isSetGroupMembers()) {
                    group.getGroupMembers().removeIf(property -> property.getObject() != null
                            && property.getObject().getGroupMember() != null
                            && property.getObject().getGroupMember().getHref() != null
                            && removedFeatureIds.contains(CityObjects.getIdFromReference(
                                    property.getObject().getGroupMember().getHref())));
                }

                if (group.getGroupParent() != null
                        && group.getGroupParent().getHref() != null
                        && removedFeatureIds.contains(CityObjects.getIdFromReference(
                                group.getGroupParent().getHref()))) {
                    group.setGroupParent(null);
                }
            }

            List<CityObjectGroup> emptyGroups = groups.stream()
                    .filter(group -> !group.isSetGroupMembers())
                    .collect(Collectors.toList());

            if (!emptyGroups.isEmpty()) {
                groups.removeAll(emptyGroups);
                if (!groups.isEmpty()) {
                    postprocess(groups, emptyGroups.stream()
                            .map(CityObjectGroup::getId)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toSet()));
                }
            }
        }
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
