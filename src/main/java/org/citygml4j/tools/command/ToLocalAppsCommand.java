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
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.tools.io.OutputFile;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.CityGMLOutputVersion;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.option.OverwriteInputOptions;
import org.citygml4j.tools.util.GlobalAppearanceConverter;
import org.citygml4j.tools.util.GlobalObjects;
import org.citygml4j.tools.util.GlobalObjectsReader;
import org.citygml4j.tools.util.ResourceProcessor;
import org.citygml4j.xml.reader.ChunkOptions;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import picocli.CommandLine;

import java.util.EnumSet;
import java.util.List;

@CommandLine.Command(name = "to-local-apps",
        description = "Converts global appearances into local ones.")
public class ToLocalAppsCommand extends CityGMLTool {
    @CommandLine.Option(names = {"-t", "--target-object"}, paramLabel = "<level>", defaultValue = "toplevel",
            description = "City object level to assign the local appearance to: ${COMPLETION-CANDIDATES} " +
                    "(default: ${DEFAULT-VALUE}).")
    private GlobalAppearanceConverter.Mode mode;

    @CommandLine.Mixin
    private CityGMLOutputVersion version;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Mixin
    OverwriteInputOptions overwriteOptions;

    @CommandLine.Mixin
    private InputOptions inputOptions;

    private final String suffix = "__local_apps";

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

            try (CityGMLReader reader = createSkippingCityGMLReader(in, inputFile, inputOptions, "Appearance");
                 ResourceProcessor resourceProcessor = ResourceProcessor.of(inputFile, outputFile)) {
                if (!version.isSetVersion()) {
                    setCityGMLVersion(reader, out);
                }

                log.debug("Reading global appearances and implicit geometries from input file.");
                EnumSet<GlobalObjects.Type> types = out.getVersion() == CityGMLVersion.v3_0 ?
                        EnumSet.of(GlobalObjects.Type.APPEARANCE, GlobalObjects.Type.IMPLICIT_GEOMETRY) :
                        EnumSet.of(GlobalObjects.Type.APPEARANCE);

                GlobalObjects globalObjects = GlobalObjectsReader.of(types).read(inputFile, getCityGMLContext());
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

                try (CityGMLChunkWriter writer = createCityGMLChunkWriter(out, outputFile, outputOptions)
                        .withCityModelInfo(getFeatureInfo(reader))) {
                    log.debug("Reading city objects and converting global appearances into local ones.");
                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        converter.convertGlobalAppearance(feature);
                        resourceProcessor.process(feature);
                        writer.writeMember(feature);
                    }

                    if (converter.hasGlobalAppearances()) {
                        List<Appearance> remaining = converter.getGlobalAppearances();
                        log.info(remaining.size() + " global appearance(s) could not be converted due to " +
                                "implicit geometries.");
                        for (Appearance appearance : remaining) {
                            resourceProcessor.process(appearance);
                            writer.writeMember(appearance);
                        }
                    } else {
                        log.info("Successfully converted all global appearances.");
                    }

                    if (!converter.getCounter().isEmpty()) {
                        converter.getCounter().forEach((key, value) ->
                                log.debug("Created local " + key + " element(s): " + value));
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
}
