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

import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.CityGMLOutputVersion;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.option.OverwriteInputOption;
import org.citygml4j.tools.util.AppearanceRemover;
import org.citygml4j.tools.util.InputFiles;
import org.citygml4j.xml.reader.ChunkOptions;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@CommandLine.Command(name = "remove-apps",
        description = "Removes appearances from city objects.")
public class RemoveAppsCommand extends CityGMLTool {
    @CommandLine.Option(names = {"-t", "--theme"}, split = ",", paramLabel = "<name>",
            description = "Only remove appearances of the given theme(s). Use '" + AppearanceRemover.NULL_THEME +
                    "' to remove appearances without a theme attribute.")
    private Set<String> themes;

    @CommandLine.Option(names = "--only-textures",
            description = "Just remove textures.")
    private boolean onlyTextures;

    @CommandLine.Option(names = "--only-materials",
            description = "Just remove materials.")
    private boolean onlyMaterials;

    @CommandLine.Mixin
    private CityGMLOutputVersion version;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Mixin
    OverwriteInputOption overwriteOption;

    @CommandLine.Mixin
    private InputOptions inputOptions;

    private final String suffix = "__removed_apps";

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

        AppearanceRemover remover = AppearanceRemover.newInstance()
                .withThemes(themes)
                .onlyTextures(onlyTextures)
                .onlyMaterials(onlyMaterials);

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            Path outputFile = getOutputFile(inputFile, suffix, overwriteOption.isOverwrite());

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile.toAbsolutePath() + ".");

            try (CityGMLReader reader = createFilteredCityGMLReader(in, inputFile, inputOptions)) {
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
                    String themes = this.themes != null ? " with theme(s) " + this.themes.stream()
                            .map(theme -> "'" + theme + "'")
                            .collect(Collectors.joining(", ")) : "";
                    log.debug("Reading city objects and removing appearances" + themes + ".");

                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        if (remover.removeAppearance(feature)) {
                            writer.writeMember(feature);
                        }
                    }

                    if (!remover.getCounter().isEmpty()) {
                        remover.getCounter().forEach((key, value) -> log.debug("Removed " + key + " element(s): " + value));
                    } else {
                        log.debug("No appearance elements removed based on input parameters.");
                    }
                }
            } catch (CityGMLReadException e) {
                throw new ExecutionException("Failed to read file " + inputFile.toAbsolutePath() + ".", e);
            } catch (CityGMLWriteException e) {
                throw new ExecutionException("Failed to write file " + outputFile.toAbsolutePath() + ".", e);
            } finally {
                remover.reset();
            }

            if (overwriteOption.isOverwrite()) {
                log.debug("Replacing input file with temporary output file.");
                replaceInputFile(inputFile, outputFile);
            }
        }

        return CommandLine.ExitCode.OK;
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        if (onlyTextures && onlyMaterials) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --only-textures and --only-materials are mutually exclusive (specify only one)");
        }
    }
}
