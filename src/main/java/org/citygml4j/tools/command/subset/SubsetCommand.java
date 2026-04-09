/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.command.subset;

import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.command.Command;
import org.citygml4j.tools.io.FileHelper;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.tools.io.OutputFile;
import org.citygml4j.tools.logging.Logger;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.CityGMLOutputVersion;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.option.OverwriteInputOptions;
import org.citygml4j.tools.util.CommandHelper;
import org.citygml4j.tools.util.ExternalResourceCopier;
import org.citygml4j.tools.util.GlobalObjectReader;
import org.citygml4j.tools.util.GlobalObjects;
import org.citygml4j.xml.reader.*;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.xmlobjects.copy.Copier;
import org.xmlobjects.copy.CopierBuilder;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@CommandLine.Command(name = "subset",
        description = "Create subsets of top-level city objects.",
        customSynopsis = {
                "citygml-tools subset [OPTIONS] [FILTER_OPTIONS] <file>",
                "       citygml-tools subset [OPTIONS] <file> group [FILTER_OPTIONS] ..."
        },
        footer = {
                "%nModes:",
                "  Single subset (one output file)",
                "    Define filters directly on 'subset'.",
                "  Multiple subsets (one output file per group)",
                "    Add one or more 'group' subcommands.",
                "    In this mode, filter options on 'subset' are not allowed.",
                "%nExamples:",
                "  citygml-tools subset city.gml --bbox=10,10,20,20%n",
                "  citygml-tools subset city.gml \\",
                "    group --name=buildings --type-name=Building \\",
                "    group --name=roads --type-name=Road"
        },
        subcommandsRepeatable = true,
        subcommands = {
                CommandLine.HelpCommand.class,
                GroupCommand.class
        })
public class SubsetCommand implements Command {
    static final String DEFAULT_GROUP_NAME = "";

    @CommandLine.Mixin
    private InputOptions inputOptions;

    @CommandLine.Option(names = {"-d", "--duplicate-mode"}, paramLabel = "<mode>", defaultValue = "allow",
            description = "Duplicate mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private Filter.DuplicateMode duplicateMode;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Mixin
    OverwriteInputOptions overwriteOptions;

    @CommandLine.Mixin
    private CityGMLOutputVersion version;

    @CommandLine.ArgGroup(exclusive = false,
            heading = "Filter options (single-subset mode only):%n")
    private FilterOptions filterOptions;

    private final Logger log = Logger.getInstance();
    private final CommandHelper helper = CommandHelper.newInstance();
    private final String suffix = "__subset";
    private List<GroupCommand> filterGroups;

    @Override
    public Integer call() throws ExecutionException {
        List<InputFile> inputFiles = helper.getInputFiles(inputOptions, suffix + ".*");
        if (inputFiles.isEmpty()) {
            return CommandLine.ExitCode.OK;
        }

        CityGMLInputFactory in = helper.createCityGMLInputFactory().withChunking(ChunkOptions.defaults());
        CityGMLOutputFactory out = helper.createCityGMLOutputFactory(version.getVersion());

        for (int i = 0; i < inputFiles.size(); i++) {
            InputFile inputFile = inputFiles.get(i);
            OutputFile dummy = helper.getOutputFile(inputFile, suffix, outputOptions, overwriteOptions);

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile + ".");

            log.debug("Reading global appearances, groups and implicit geometries from input file.");
            GlobalObjects globalObjects = GlobalObjectReader.defaults()
                    .read(inputFile, helper.getCityGMLContext());

            List<SubsetContext> contexts;
            try (ExternalResourceCopier resourceCopier = ExternalResourceCopier.of(inputFile, dummy);
                 CityGMLReader reader = helper.createSkippingCityGMLReader(in, inputFile, inputOptions,
                         "CityObjectGroup", "Appearance")) {
                FeatureInfo cityModelInfo = helper.getFeatureInfo(reader);
                if (!version.isSetVersion()) {
                    helper.setCityGMLVersion(reader, out);
                }

                contexts = buildContexts(inputFile, globalObjects, cityModelInfo, out);
                contexts.forEach(context -> {
                    if (context.getOutputFile().isTemporary()) {
                        log.debug(context.format("Writing temporary output file " + context.getOutputFile() + "."));
                    } else {
                        log.info(context.format("Writing output to file " + context.getOutputFile() + "."));
                    }
                });

                while (contexts.stream().anyMatch(SubsetContext::isCountWithinLimit) && reader.hasNext()) {
                    AbstractFeature feature = reader.next();
                    boolean first = true;

                    for (SubsetContext context : contexts) {
                        if (context.filter(feature, reader, first)) {
                            context.write(feature, resourceCopier);
                            first = false;
                        }
                    }
                }

                for (SubsetContext context : contexts) {
                    context.postprocess(resourceCopier);
                    context.close();
                }
            } catch (CityGMLReadException e) {
                throw new ExecutionException("Failed to read file " + inputFile + ".", e);
            }

            if (contexts.size() == 1 && contexts.get(0).getOutputFile().isTemporary()) {
                helper.replaceInputFile(inputFile, contexts.get(0).getOutputFile());
            }

            for (SubsetContext context : contexts) {
                if (!context.getCounter().isEmpty()) {
                    log.debug(context.format("Top-level city objects satisfying the filter criteria."));
                    context.getCounter().forEach((key, value) ->
                            log.debug(context.format(key + ": " + value)));
                } else {
                    log.debug(context.format("No top-level city objects satisfy the filter criteria."));
                }
            }
        }

        return CommandLine.ExitCode.OK;
    }

    private List<SubsetContext> buildContexts(
            InputFile inputFile, GlobalObjects globalObjects, FeatureInfo cityModelInfo,
            CityGMLOutputFactory out) throws ExecutionException {
        List<SubsetContext> contexts = new ArrayList<>(filterGroups.size());
        Copier copier = CopierBuilder.newCopier();

        for (int i = 0; i < filterGroups.size(); i++) {
            GroupCommand group = filterGroups.get(i);
            OutputFile outputFile = helper.getOutputFile(getInputFile(inputFile, group, i), getSuffix(group, i),
                    outputOptions, overwriteOptions);

            FilterOptions filterOptions = group.getFilterOptions();
            Filter filter = Filter.newInstance()
                    .withGlobalObjectHelper(i == 0 ? globalObjects : copier.deepCopy(globalObjects))
                    .withTypeNamesFilter(filterOptions.getTypeNameOptions(), helper.getCityGMLContext())
                    .withIdFilter(filterOptions.getIdOptions())
                    .withBoundingBoxFilter(filterOptions.getBoundingBoxOptions(), cityModelInfo)
                    .invertFilterCriteria(filterOptions.isInvert())
                    .withCounterOption(filterOptions.getCountOptions())
                    .withDuplicateMode(duplicateMode)
                    .removeGroupMembers(filterOptions.isRemoveGroupMembers());

            CityGMLChunkWriter writer = helper.createCityGMLChunkWriter(out, outputFile, outputOptions)
                    .withCityModelInfo(cityModelInfo);

            contexts.add(SubsetContext.of(group, i + 1, filter, outputFile, writer));
        }

        return contexts;
    }

    private InputFile getInputFile(InputFile inputFile, GroupCommand group, int index) {
        if (outputOptions.getOutputDirectory() == null || DEFAULT_GROUP_NAME.equals(group.getName())) {
            return inputFile;
        } else {
            String suffix = group.getName() != null ?
                    group.getName() :
                    String.valueOf(index + 1);
            String filename = FileHelper.appendFileNameSuffix(inputFile.getFile(), "_" + suffix);
            return InputFile.of(inputFile.getFile().resolveSibling(filename), inputFile.getBasePath());
        }
    }

    private String getSuffix(GroupCommand group, int index) {
        if (DEFAULT_GROUP_NAME.equals(group.getName())) {
            return suffix;
        } else if (group.getName() != null) {
            return suffix + "_" + group.getName();
        } else {
            return suffix + "_" + (index + 1);
        }
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        List<GroupCommand> groups = commandLine.getParseResult().asCommandLineList().stream()
                .map(CommandLine::getCommand)
                .filter(GroupCommand.class::isInstance)
                .map(GroupCommand.class::cast)
                .toList();

        if (filterOptions != null
                && !filterOptions.isEmpty()
                && !groups.isEmpty()) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: Filter options on 'subset' cannot be used when 'group' subcommands are present. " +
                            "Move all filter options into a 'group' subcommand.");
        }

        Set<String> names = new HashSet<>(groups.size());
        for (GroupCommand group : groups) {
            if (group.getName() != null && !names.add(group.getName())) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: Group names must be unique but '" + group.getName() + "' is used more than once.");
            }
        }

        if (overwriteOptions.isOverwrite() && groups.size() > 1) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --overwrite cannot be used when more than one 'group' subcommand is present");
        }

        filterGroups = groups.isEmpty() ?
                List.of(new GroupCommand(DEFAULT_GROUP_NAME, filterOptions)) :
                groups;
    }
}
