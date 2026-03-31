/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.command;

import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.tools.io.OutputFile;
import org.citygml4j.tools.option.*;
import org.citygml4j.tools.util.GlobalObjects;
import org.citygml4j.tools.util.GlobalObjectsReader;
import org.citygml4j.tools.util.ResourceProcessor;
import org.citygml4j.tools.util.SubsetFilter;
import org.citygml4j.xml.reader.*;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@CommandLine.Command(name = "subset",
        description = "Create a subset of city objects based on filter criteria.")
public class SubsetCommand extends CityGMLTool {
    @CommandLine.Mixin
    private InputOptions inputOptions;

    @CommandLine.ArgGroup(exclusive = false)
    private TypeNameOptions typeNameOptions;

    @CommandLine.ArgGroup
    private IdOptions idOptions;

    @CommandLine.ArgGroup(exclusive = false)
    private MultiBoundingBoxOptions multiBoundingBoxOptions;

    @CommandLine.Option(names = "--invert",
            description = "Invert the filter criteria.")
    private boolean invert;

    @CommandLine.ArgGroup(exclusive = false)
    private CountOptions countOptions;

    @CommandLine.Option(names = "--no-remove-group-members", negatable = true, defaultValue = "true",
            description = "Remove group members that do not meet the filter criteria (default: ${DEFAULT-VALUE}).")
    private boolean removeGroupMembers;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Mixin
    OverwriteInputOptions overwriteOptions;

    @CommandLine.Mixin
    private CityGMLOutputVersion version;

    private final String suffix = "__subset";

    @Override
    public Integer call() throws ExecutionException {
        List<InputFile> inputFiles = getInputFiles(inputOptions, suffix);
        if (inputFiles.isEmpty()) {
            return CommandLine.ExitCode.OK;
        }

        // Check if we have multiple bounding boxes - if so, use multi-bbox processing
        if (multiBoundingBoxOptions != null && multiBoundingBoxOptions.hasMultipleBBoxes()) {
            return processWithMultipleBBoxes(inputFiles);
        } else {
            return processWithSingleBBox(inputFiles);
        }
    }

    private Integer processWithSingleBBox(List<InputFile> inputFiles) throws ExecutionException {
        CityGMLInputFactory in = createCityGMLInputFactory().withChunking(ChunkOptions.defaults());
        CityGMLOutputFactory out = createCityGMLOutputFactory(version.getVersion());

        for (int i = 0; i < inputFiles.size(); i++) {
            InputFile inputFile = inputFiles.get(i);
            OutputFile outputFile = getOutputFile(inputFile, suffix, outputOptions, overwriteOptions);

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile + ".");

            log.debug("Reading global appearances, groups and implicit geometries from input file.");
            GlobalObjects globalObjects = GlobalObjectsReader.defaults()
                    .read(inputFile, getCityGMLContext());

            SubsetFilter subsetFilter = SubsetFilter.newInstance()
                    .withGlobalObjects(globalObjects)
                    .withTypeNamesFilter(typeNameOptions, getCityGMLContext())
                    .withIdFilter(idOptions)
                    .withBoundingBoxFilter(multiBoundingBoxOptions != null && !multiBoundingBoxOptions.getBBoxConfigs().isEmpty()
                            ? multiBoundingBoxOptions.getBBoxConfigs().get(0).getFilter() : null)
                    .invertFilterCriteria(invert)
                    .withCounterOption(countOptions)
                    .removeGroupMembers(removeGroupMembers);

            try (CityGMLReader reader = createSkippingCityGMLReader(in, inputFile, inputOptions,
                    "CityObjectGroup", "Appearance");
                 ResourceProcessor resourceProcessor = ResourceProcessor.of(inputFile, outputFile)) {
                FeatureInfo cityModelInfo = getFeatureInfo(reader);
                if (cityModelInfo != null && subsetFilter.getBoundingBoxFilter() != null) {
                    subsetFilter.getBoundingBoxFilter().withRootReferenceSystem(cityModelInfo);
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
                    log.debug("Reading and filtering city objects based on the specified filter criteria.");
                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        if (subsetFilter.filter(feature, reader.getName(), reader.getPrefix())) {
                            resourceProcessor.process(feature);
                            writer.writeMember(feature);
                        }
                    }

                    subsetFilter.postprocess();

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

            if (!subsetFilter.getCounter().isEmpty()) {
                log.debug("The following top-level city objects satisfied the filter criteria.");
                subsetFilter.getCounter().forEach((key, value) -> log.debug(key + ": " + value));
            } else {
                log.debug("No top-level city object satisfies the filter criteria.");
            }
        }

        return CommandLine.ExitCode.OK;
    }

    private Integer processWithMultipleBBoxes(List<InputFile> inputFiles) throws ExecutionException {
        CityGMLInputFactory in = createCityGMLInputFactory().withChunking(ChunkOptions.defaults());
        CityGMLOutputFactory out = createCityGMLOutputFactory(version.getVersion());

        List<MultiBoundingBoxOptions.BBoxConfig> bboxConfigs = multiBoundingBoxOptions.getBBoxConfigs();
        boolean duplicateStrategy = multiBoundingBoxOptions.getOverlapStrategy() == MultiBoundingBoxOptions.OverlapStrategy.DUPLICATE;

        log.info("Processing with " + bboxConfigs.size() + " bounding boxes in a single pass.");
        log.info("Overlap strategy: " + multiBoundingBoxOptions.getOverlapStrategy());

        for (int i = 0; i < inputFiles.size(); i++) {
            InputFile inputFile = inputFiles.get(i);

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile + ".");

            log.debug("Reading global appearances, groups and implicit geometries from input file.");
            GlobalObjects globalObjects = GlobalObjectsReader.defaults()
                    .read(inputFile, getCityGMLContext());

            // Create a SubsetFilter and output for each bounding box
            List<SubsetFilterContext> contexts = new ArrayList<>();
            for (MultiBoundingBoxOptions.BBoxConfig bboxConfig : bboxConfigs) {
                String bboxSuffix = suffix + "_" + bboxConfig.getName();
                OutputFile outputFile = getOutputFile(inputFile, bboxSuffix, outputOptions, overwriteOptions);

                SubsetFilter subsetFilter = SubsetFilter.newInstance()
                        .withGlobalObjects(globalObjects)
                        .withTypeNamesFilter(typeNameOptions, getCityGMLContext())
                        .withIdFilter(idOptions)
                        .withBoundingBoxFilter(bboxConfig.getFilter())
                        .invertFilterCriteria(invert)
                        .withCounterOption(countOptions)
                        .removeGroupMembers(removeGroupMembers);

                contexts.add(new SubsetFilterContext(bboxConfig.getName(), subsetFilter, outputFile));
            }

            try (CityGMLReader reader = createSkippingCityGMLReader(in, inputFile, inputOptions,
                    "CityObjectGroup", "Appearance")) {
                FeatureInfo cityModelInfo = getFeatureInfo(reader);

                // Set root reference system for all bbox filters
                for (SubsetFilterContext context : contexts) {
                    if (cityModelInfo != null && context.filter.getBoundingBoxFilter() != null) {
                        context.filter.getBoundingBoxFilter().withRootReferenceSystem(cityModelInfo);
                    }
                }

                if (!version.isSetVersion()) {
                    setCityGMLVersion(reader, out);
                }

                // Create writers and resource processors for each output
                List<CityGMLChunkWriter> writers = new ArrayList<>();
                Map<OutputFile, ResourceProcessor> resourceProcessors = new HashMap<>();

                try {
                    for (SubsetFilterContext context : contexts) {
                        ResourceProcessor resourceProcessor = ResourceProcessor.of(inputFile, context.outputFile);
                        resourceProcessors.put(context.outputFile, resourceProcessor);

                        if (context.outputFile.isTemporary()) {
                            log.debug("Writing temporary output file " + context.outputFile + ".");
                        } else {
                            log.info("Writing output to file " + context.outputFile + " (bbox: " + context.name + ").");
                        }

                        CityGMLChunkWriter writer = createCityGMLChunkWriter(out, context.outputFile, outputOptions)
                                .withCityModelInfo(cityModelInfo);
                        writers.add(writer);
                        context.writer = writer;
                    }

                    log.debug("Reading and filtering city objects based on the specified filter criteria.");
                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        boolean written = false;

                        for (SubsetFilterContext context : contexts) {
                            if (context.filter.filter(feature, reader.getName(), reader.getPrefix())) {
                                ResourceProcessor resourceProcessor = resourceProcessors.get(context.outputFile);
                                resourceProcessor.process(feature);
                                context.writer.writeMember(feature);
                                written = true;

                                // If using FIRST strategy, stop after first match
                                if (!duplicateStrategy) {
                                    break;
                                }
                            }
                        }
                    }

                    // Postprocess each filter and write global objects
                    for (SubsetFilterContext context : contexts) {
                        context.filter.postprocess();

                        ResourceProcessor resourceProcessor = resourceProcessors.get(context.outputFile);
                        for (CityObjectGroup group : globalObjects.getCityObjectGroups()) {
                            resourceProcessor.process(group);
                            context.writer.writeMember(group);
                        }

                        for (Appearance appearance : globalObjects.getAppearances()) {
                            resourceProcessor.process(appearance);
                            context.writer.writeMember(appearance);
                        }
                    }

                } finally {
                    // Close all writers
                    for (CityGMLChunkWriter writer : writers) {
                        try {
                            writer.close();
                        } catch (CityGMLWriteException e) {
                            log.error("Failed to close writer: " + e.getMessage());
                        }
                    }

                    // Close all resource processors
                    for (ResourceProcessor resourceProcessor : resourceProcessors.values()) {
                        try {
                            resourceProcessor.close();
                        } catch (Exception e) {
                            log.error("Failed to close resource processor: " + e.getMessage());
                        }
                    }
                }
            } catch (CityGMLReadException e) {
                throw new ExecutionException("Failed to read file " + inputFile + ".", e);
            } catch (CityGMLWriteException e) {
                throw new ExecutionException("Failed to write output files.", e);
            }

            // Replace input files if temporary outputs were created
            for (SubsetFilterContext context : contexts) {
                if (context.outputFile.isTemporary()) {
                    replaceInputFile(inputFile, context.outputFile);
                }
            }

            // Log statistics for each bounding box
            for (SubsetFilterContext context : contexts) {
                if (!context.filter.getCounter().isEmpty()) {
                    log.debug("BBox '" + context.name + "': The following top-level city objects satisfied the filter criteria.");
                    context.filter.getCounter().forEach((key, value) -> log.debug("  " + key + ": " + value));
                } else {
                    log.debug("BBox '" + context.name + "': No top-level city object satisfies the filter criteria.");
                }
            }
        }

        return CommandLine.ExitCode.OK;
    }

    // Helper class to hold filter context
    private static class SubsetFilterContext {
        final String name;
        final SubsetFilter filter;
        final OutputFile outputFile;
        CityGMLChunkWriter writer;

        SubsetFilterContext(String name, SubsetFilter filter, OutputFile outputFile) {
            this.name = name;
            this.filter = filter;
            this.outputFile = outputFile;
        }
    }
}
