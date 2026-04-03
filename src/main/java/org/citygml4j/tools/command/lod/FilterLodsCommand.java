/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.command.lod;

import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.cityobjectgroup.CityObjectGroup;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.command.Command;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.tools.io.OutputFile;
import org.citygml4j.tools.logging.Logger;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.CityGMLOutputVersion;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.option.OverwriteInputOptions;
import org.citygml4j.tools.util.*;
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
        description = "Filter LoD representations of city objects.")
public class FilterLodsCommand implements Command {
    @CommandLine.Mixin
    private InputOptions inputOptions;

    @CommandLine.Option(names = {"-l", "--lod"}, required = true, split = ",", paramLabel = "<0..4>",
            description = "LoD levels to filter.")
    private int[] lods;

    @CommandLine.Option(names = {"-m", "--mode"}, defaultValue = "keep",
            description = "LoD filter mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}). The " +
                    "minimum and maximum LoD is determined per top-level city object.")
    private LodFilter.Mode mode;

    @CommandLine.Option(names = {"-k", "--keep-empty-objects"},
            description = "Keep city objects even if all their LoD representations have been removed.")
    private boolean keepEmptyObjects;

    @CommandLine.Option(names = "--no-update-extents", negatable = true, defaultValue = "true",
            description = "Update the extents of city objects after removing LoDs (default: ${DEFAULT-VALUE}).")
    private boolean updateExtents;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Mixin
    private OverwriteInputOptions overwriteOptions;

    @CommandLine.Mixin
    private CityGMLOutputVersion version;

    private final Logger log = Logger.getInstance();
    private final CommandHelper helper = CommandHelper.newInstance();
    private final String suffix = "__filtered_lods";

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

            LodFilter lodFilter = LodFilter.of(lods)
                    .withMode(mode)
                    .withGlobalAppearances(globalObjectHelper.getAppearances())
                    .withCityObjectGroups(globalObjectHelper.getCityObjectGroups())
                    .withTemplateGeometries(globalObjectHelper.getTemplateGeometries())
                    .updateExtents(updateExtents)
                    .withFeatureMode(keepEmptyObjects ?
                            LodFilter.FeatureMode.KEEP_EMPTY_FEATURES :
                            LodFilter.FeatureMode.DELETE_EMPTY_FEATURES);

            try (CityGMLReader reader = helper.createSkippingCityGMLReader(in, inputFile, inputOptions,
                    "CityObjectGroup", "Appearance");
                 ExternalResourceCopier resourceCopier = ExternalResourceCopier.of(inputFile, outputFile)) {
                if (!version.isSetVersion()) {
                    helper.setCityGMLVersion(reader, out);
                }

                if (outputFile.isTemporary()) {
                    log.debug("Writing temporary output file " + outputFile + ".");
                } else {
                    log.info("Writing output to file " + outputFile + ".");
                }

                try (CityGMLChunkWriter writer = helper.createCityGMLChunkWriter(out, outputFile, outputOptions)
                        .withCityModelInfo(helper.getFeatureInfo(reader))) {
                    log.debug("Reading city objects and filtering LoD representations.");
                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        if (lodFilter.filter(feature)) {
                            resourceCopier.process(feature);
                            writer.writeMember(feature);
                        }
                    }

                    lodFilter.postprocess();

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
