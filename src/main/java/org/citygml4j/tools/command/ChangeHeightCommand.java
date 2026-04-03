/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.command;

import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.tools.io.OutputFile;
import org.citygml4j.tools.log.Logger;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.CityGMLOutputVersion;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.option.OverwriteInputOptions;
import org.citygml4j.tools.util.ExternalResourceCopier;
import org.citygml4j.tools.util.GlobalObjectReader;
import org.citygml4j.tools.util.HeightChanger;
import org.citygml4j.xml.reader.ChunkOptions;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(name = "change-height",
        description = "Change the height values of city objects by a given offset.")
public class ChangeHeightCommand implements Command {
    @CommandLine.Mixin
    private InputOptions inputOptions;

    @CommandLine.Option(names = {"-z", "--offset"}, paramLabel = "<double>", required = true,
            description = "Offset to add to height values.")
    private double offset;

    @CommandLine.Option(names = {"-m", "--mode"}, defaultValue = "relative",
            description = "Height mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private HeightChanger.Mode mode;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Mixin
    OverwriteInputOptions overwriteOptions;

    @CommandLine.Mixin
    private CityGMLOutputVersion version;

    private final Logger log = Logger.getInstance();
    private final CommandHelper helper = CommandHelper.newInstance();
    private final String suffix = "__changed_height";

    @Override
    public Integer call() throws ExecutionException {
        if (offset == 0 && mode == HeightChanger.Mode.RELATIVE) {
            log.warn("Height values do not change for offset = " + offset + " and mode = " + mode + ".");
            log.info("Cancelling the operation.");
            return CommandLine.ExitCode.OK;
        }

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

            HeightChanger heightChanger = HeightChanger.of(offset).withMode(mode);

            log.debug("Reading implicit geometries from input file.");
            heightChanger.withTemplateGeometries(GlobalObjectReader.onlyImplicitGeometries()
                    .read(inputFile, helper.getCityGMLContext())
                    .getTemplateGeometries());

            try (CityGMLReader reader = helper.createCityGMLReader(in, inputFile, inputOptions);
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
                    log.debug("Reading city objects and changing their height values.");
                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        if (!(feature instanceof Appearance)) {
                            heightChanger.changeHeight(feature);
                        }

                        resourceCopier.process(feature);
                        writer.writeMember(feature);
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
}
