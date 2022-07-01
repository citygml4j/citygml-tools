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

import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.log.Logger;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.upgrade.DeprecatedPropertiesProcessor;
import org.citygml4j.tools.util.GlobalObjectsReader;
import org.citygml4j.tools.util.InputFiles;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.reader.*;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;

@CommandLine.Command(
        name = "upgrade",
        description = "Upgrades CityGML files to version 3.0."
)
public class UpgradeCommand extends CityGMLTool {
    @CommandLine.Option(names = {"-u", "--use-lod4-as-lod3"},
            description = "Use the LoD4 representation of city objects as LoD3, replacing an existing LoD3.")
    private boolean useLod4AsLod3;

    @CommandLine.Option(names = {"-m", "--map-lod1-multi-surfaces"},
            description = "Map the LoD1 multi-surface representation of city objects onto generic thematic surfaces.")
    private boolean mapLod1MultiSurfaces;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Option(names = {"-O", "--overwrite"},
            description = "Overwrite input file(s).")
    private boolean overwrite;

    @CommandLine.Mixin
    private InputOptions inputOptions;

    private final Logger log = Logger.getInstance();
    private final String suffix = "__v3";

    @Override
    public Integer call() throws ExecutionException {
        log.debug("Searching for CityGML input files.");
        List<Path> inputFiles = InputFiles.of(inputOptions.getFiles())
                .withFilter(path -> !stripFileExtension(path).endsWith(suffix))
                .find();

        if (inputFiles.isEmpty()) {
            log.warn("No files found at " + inputOptions.joinFiles() + ".");
            return 0;
        }

        log.info("Found " + inputFiles.size() + " file(s) at " + inputOptions.joinFiles() + ".");

        CityGMLInputFactory in = createCityGMLInputFactory().withChunking(ChunkOptions.defaults());
        CityGMLOutputFactory out = createCityGMLOutputFactory(CityGMLVersion.v3_0);

        DeprecatedPropertiesProcessor processor = DeprecatedPropertiesProcessor.newInstance()
                .useLod4AsLod3(useLod4AsLod3)
                .mapLod1MultiSurfaces(mapLod1MultiSurfaces);

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            Path outputFile = getOutputFile(inputFile, suffix, overwrite);

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile.toAbsolutePath() + ".");

            try (CityGMLReader reader = createFilteredCityGMLReader(in, inputFile, inputOptions,
                    useLod4AsLod3 ? new String[]{"Appearance"} : null)) {
                FeatureInfo info = null;
                if (reader.hasNext()) {
                    CityGMLVersion version = CityGMLModules.getCityGMLVersion(reader.getName().getNamespaceURI());
                    if (version == CityGMLVersion.v3_0) {
                        log.info("This is already a CityGML 3.0 file. No action required.");
                        continue;
                    } else if (version == null) {
                        log.error("Failed to detect CityGML version. Skipping file.");
                        continue;
                    }

                    info = reader.getParentInfo();
                }

                if (useLod4AsLod3) {
                    log.debug("Reading global appearances from input file.");
                    processor.withGlobalAppearances(GlobalObjectsReader.onlyAppearances()
                            .read(inputFile, getCityGMLContext())
                            .getAppearances());
                }

                if (overwrite) {
                    log.debug("Writing temporary output file " + outputFile.toAbsolutePath() + ".");
                } else {
                    log.info("Writing output to file " + outputFile.toAbsolutePath() + ".");
                }

                try (CityGMLChunkWriter writer = createCityGMLChunkWriter(out, outputFile, outputOptions)
                        .withCityModelInfo(info)) {
                    log.debug("Reading city objects and upgrading them to CityGML 3.0.");
                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        processor.process(feature);
                        writer.writeMember(feature);
                    }

                    if (useLod4AsLod3) {
                        for (Appearance appearance : processor.getGlobalAppearances()) {
                            writer.writeMember(appearance);
                        }
                    }
                }
            } catch (CityGMLReadException e) {
                throw new ExecutionException("Failed to read file " + inputFile.toAbsolutePath() + ".", e);
            } catch (CityGMLWriteException e) {
                throw new ExecutionException("Failed to write file " + outputFile.toAbsolutePath() + ".", e);
            }

            if (overwrite) {
                log.debug("Replacing input file with temporary output file.");
                replaceInputFile(inputFile, outputFile);
            }
        }

        return 0;
    }
}
