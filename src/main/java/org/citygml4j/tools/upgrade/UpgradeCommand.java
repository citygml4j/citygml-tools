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

package org.citygml4j.tools.upgrade;

import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.util.reference.DefaultReferenceResolver;
import org.citygml4j.tools.cli.CityGMLInputOptions;
import org.citygml4j.tools.cli.CityGMLOutputOptions;
import org.citygml4j.tools.cli.CityGMLTool;
import org.citygml4j.tools.cli.ExecutionException;
import org.citygml4j.tools.log.Logger;
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
        description = "Upgrades CityGML files to version 3.0"
)
public class UpgradeCommand extends CityGMLTool {
    @CommandLine.Parameters(paramLabel = "<file>", arity = "1",
            description = "Files or directories to upgrade (glob patterns allowed).")
    private String[] files;

    @CommandLine.Mixin
    CityGMLInputOptions inputOptions;

    @CommandLine.Mixin
    CityGMLOutputOptions outputOptions;

    private final Logger log = Logger.getInstance();
    private final String suffix = "__v3";

    @Override
    public Integer call() throws ExecutionException {
        log.debug("Searching for CityGML input files.");
        List<Path> inputFiles = InputFiles.of(files)
                .withFilter(path -> !stripFileExtension(path).endsWith(suffix))
                .find();

        if (inputFiles.isEmpty()) {
            log.warn("No files found at " + String.join(", ", files) + ".");
            return 0;
        }

        log.info("Found " + inputFiles.size() + " file(s) at " + String.join(", ", files) + ".");

        CityGMLInputFactory in = createCityGMLInputFactory()
                .withChunking(ChunkOptions.defaults())
                .withReferenceResolver(DefaultReferenceResolver.newInstance());

        CityGMLOutputFactory out = createCityGMLOutputFactory(CityGMLVersion.v3_0);

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            Path outputFile = inputFile.resolveSibling(appendFileNameSuffix(inputFile, suffix));

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file '" + inputFile.toAbsolutePath() + "'.");

            try (CityGMLReader reader = createCityGMLReader(in, inputFile, inputOptions)) {
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

                try (CityGMLChunkWriter writer = createCityGMLChunkWriter(out, outputFile, outputOptions)
                        .withCityModelInfo(info)) {
                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        writer.writeMember(feature);
                    }
                }
            } catch (CityGMLReadException e) {
                throw new ExecutionException("Failed to read file " + inputFile.toAbsolutePath() + ".", e);
            } catch (CityGMLWriteException e) {
                throw new ExecutionException("Failed to write file " + outputFile.toAbsolutePath() + ".", e);
            }
        }

        return 0;
    }
}
