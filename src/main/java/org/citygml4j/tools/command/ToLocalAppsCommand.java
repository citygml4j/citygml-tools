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
import org.citygml4j.tools.util.GlobalAppearanceConverter;
import org.citygml4j.tools.util.GlobalObjects;
import org.citygml4j.tools.util.GlobalObjectsReader;
import org.citygml4j.xml.reader.ChunkOptions;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(name = "to-local-apps",
        description = "Convert global appearances into local ones.")
public class ToLocalAppsCommand implements Command {
    @CommandLine.Mixin
    private InputOptions inputOptions;

    @CommandLine.Option(names = {"-t", "--target-object"}, paramLabel = "<level>", defaultValue = "toplevel",
            description = "City object level to assign the local appearance to: ${COMPLETION-CANDIDATES} " +
                    "(default: ${DEFAULT-VALUE}).")
    private GlobalAppearanceConverter.Mode mode;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Mixin
    OverwriteInputOptions overwriteOptions;

    @CommandLine.Mixin
    private CityGMLOutputVersion version;

    private final Logger log = Logger.getInstance();
    private final CommandHelper helper = CommandHelper.newInstance();
    private final String suffix = "__local_apps";

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

            try (CityGMLReader reader = helper.createSkippingCityGMLReader(in, inputFile, inputOptions, "Appearance");
                 ExternalResourceCopier resourceCopier = ExternalResourceCopier.of(inputFile, outputFile)) {
                if (!version.isSetVersion()) {
                    helper.setCityGMLVersion(reader, out);
                }

                log.debug("Reading global appearances and implicit geometries from input file.");
                GlobalObjects globalObjects = GlobalObjectsReader.of(
                                GlobalObjects.Type.APPEARANCE,
                                GlobalObjects.Type.IMPLICIT_GEOMETRY)
                        .read(inputFile, helper.getCityGMLContext());
                List<Appearance> appearances = globalObjects.getAppearances();
                if (appearances.isEmpty()) {
                    log.info("The file does not contain global appearances. No action required.");
                    continue;
                } else {
                    log.debug("Found " + appearances.size() + " global appearance(s).");
                }

                if (outputFile.isTemporary()) {
                    log.debug("Writing temporary output file " + outputFile + ".");
                } else {
                    log.info("Writing output to file " + outputFile + ".");
                }

                GlobalAppearanceConverter converter = GlobalAppearanceConverter.of(appearances, out.getVersion())
                        .withMode(mode)
                        .withTemplateGeometries(globalObjects.getTemplateGeometries());

                try (CityGMLChunkWriter writer = helper.createCityGMLChunkWriter(out, outputFile, outputOptions)
                        .withCityModelInfo(helper.getFeatureInfo(reader))) {
                    log.debug("Reading city objects and converting global appearances into local ones.");
                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        converter.convertGlobalAppearance(feature);
                        resourceCopier.process(feature);
                        writer.writeMember(feature);
                    }

                    if (converter.hasGlobalAppearances()) {
                        List<Appearance> remaining = converter.getGlobalAppearances();
                        log.info(remaining.size() + " global appearance(s) could not be converted due to " +
                                "implicit geometries.");
                        for (Appearance appearance : remaining) {
                            resourceCopier.process(appearance);
                            writer.writeMember(appearance);
                        }
                    } else {
                        log.info("Successfully converted all global appearances.");
                    }

                    if (!converter.getCounter().isEmpty()) {
                        converter.getCounter().forEach((key, value) ->
                                log.debug("Created local " + key + " elements: " + value));
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
