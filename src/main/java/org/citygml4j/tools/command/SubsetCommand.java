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
import org.citygml4j.tools.log.Logger;
import org.citygml4j.tools.option.*;
import org.citygml4j.tools.util.ExternalResourceCopier;
import org.citygml4j.tools.util.GlobalObjectHelper;
import org.citygml4j.tools.util.GlobalObjectReader;
import org.citygml4j.tools.util.SubsetFilter;
import org.citygml4j.xml.reader.*;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(name = "subset",
        description = "Create a subset of city objects based on filter criteria.")
public class SubsetCommand implements Command {
    @CommandLine.Mixin
    private InputOptions inputOptions;

    @CommandLine.ArgGroup(exclusive = false)
    private TypeNameOptions typeNameOptions;

    @CommandLine.ArgGroup
    private IdOptions idOptions;

    @CommandLine.ArgGroup(exclusive = false)
    private BoundingBoxOptions boundingBoxOptions;

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

    private final Logger log = Logger.getInstance();
    private final CommandHelper helper = CommandHelper.newInstance();
    private final String suffix = "__subset";

    @Override
    public Integer call() throws ExecutionException {
        List<InputFile> inputFiles = helper.getInputFiles(inputOptions, suffix);
        if (inputFiles.isEmpty()) {
            return CommandLine.ExitCode.OK;
        }

        CityGMLInputFactory in = helper.createCityGMLInputFactory().withChunking(ChunkOptions.defaults());
        CityGMLOutputFactory out = helper.createCityGMLOutputFactory(version.getVersion());

        for (int i = 0; i < inputFiles.size(); i++) {
            InputFile inputFile = inputFiles.get(i);
            OutputFile outputFile = helper.getOutputFile(inputFile, suffix, outputOptions, overwriteOptions);

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile + ".");

            log.debug("Reading global appearances, groups and implicit geometries from input file.");
            GlobalObjectHelper globalObjectHelper = GlobalObjectReader.defaults()
                    .read(inputFile, helper.getCityGMLContext());

            SubsetFilter subsetFilter = SubsetFilter.newInstance()
                    .withGlobalObjects(globalObjectHelper)
                    .withTypeNamesFilter(typeNameOptions, helper.getCityGMLContext())
                    .withIdFilter(idOptions)
                    .withBoundingBoxFilter(boundingBoxOptions != null ? boundingBoxOptions.toBoundingBoxFilter() : null)
                    .invertFilterCriteria(invert)
                    .withCounterOption(countOptions)
                    .removeGroupMembers(removeGroupMembers);

            try (CityGMLReader reader = helper.createSkippingCityGMLReader(in, inputFile, inputOptions,
                    "CityObjectGroup", "Appearance");
                 ExternalResourceCopier resourceCopier = ExternalResourceCopier.of(inputFile, outputFile)) {
                FeatureInfo cityModelInfo = helper.getFeatureInfo(reader);
                if (cityModelInfo != null && subsetFilter.getBoundingBoxFilter() != null) {
                    subsetFilter.getBoundingBoxFilter().withRootReferenceSystem(cityModelInfo);
                }

                if (!version.isSetVersion()) {
                    helper.setCityGMLVersion(reader, out);
                }

                if (outputFile.isTemporary()) {
                    log.debug("Writing temporary output file " + outputFile + ".");
                } else {
                    log.info("Writing output to file " + outputFile + ".");
                }

                try (CityGMLChunkWriter writer = helper.createCityGMLChunkWriter(out, outputFile, outputOptions)
                        .withCityModelInfo(cityModelInfo)) {
                    log.debug("Reading and filtering city objects based on the specified filter criteria.");
                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        if (subsetFilter.filter(feature, reader.getName(), reader.getPrefix())) {
                            resourceCopier.process(feature);
                            writer.writeMember(feature);
                        }
                    }

                    subsetFilter.postprocess();

                    for (CityObjectGroup group : globalObjectHelper.getCityObjectGroups()) {
                        resourceCopier.process(group);
                        writer.writeMember(group);
                    }

                    for (Appearance appearance : globalObjectHelper.getAppearances()) {
                        resourceCopier.process(appearance);
                        writer.writeMember(appearance);
                    }
                }
            } catch (CityGMLReadException e) {
                throw new ExecutionException("Failed to read file " + inputFile + ".", e);
            } catch (CityGMLWriteException e) {
                throw new ExecutionException("Failed to write file " + outputFile + ".", e);
            }

            if (outputFile.isTemporary()) {
                helper.replaceInputFile(inputFile, outputFile);
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
}
