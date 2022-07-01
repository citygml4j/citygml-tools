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
import org.citygml4j.core.model.appearance.AbstractSurfaceDataProperty;
import org.citygml4j.core.model.appearance.AbstractTexture;
import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.appearance.X3DMaterial;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.visitor.ObjectWalker;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.log.Logger;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.CityGMLOutputVersion;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.util.InputFiles;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.reader.*;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import org.xmlobjects.gml.model.GMLObject;
import org.xmlobjects.model.Child;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@CommandLine.Command(name = "remove-apps",
        description = "Removes appearances from city objects.")
public class RemoveAppsCommand extends CityGMLTool {
    @CommandLine.Option(names = {"-t", "--theme"}, paramLabel = "<name>", split = ",",
            description = "Only remove appearances of the given theme(s). Use '" + nullTheme + "' to remove appearances " +
                    "without a theme attribute.")
    private List<String> themes;

    @CommandLine.Option(names = "--only-textures",
            description = "Just remove textures.")
    private boolean onlyTextures;

    @CommandLine.Option(names = "--only-materials",
            description = "Just remove materials.")
    private boolean onlyMaterials;

    @CommandLine.Option(names = "--only-global",
            description = "Just remove global appearances.")
    private boolean onlyGlobal;

    @CommandLine.Mixin
    private CityGMLOutputVersion version;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Option(names = {"-O", "--overwrite"},
            description = "Overwrite input file(s).")
    private boolean overwrite;

    @CommandLine.Mixin
    private InputOptions inputOptions;

    private final Logger log = Logger.getInstance();
    private final String suffix = "__removed_apps";
    private final String nullTheme = "null";

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
        CityGMLOutputFactory out = createCityGMLOutputFactory(version.getVersion());

        AppearanceRemover remover = new AppearanceRemover();

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            Path outputFile = getOutputFile(inputFile, suffix, overwrite);

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile.toAbsolutePath() + ".");

            try (CityGMLReader reader = createFilteredCityGMLReader(in, inputFile, inputOptions, "Appearance")) {
                FeatureInfo info = null;
                if (reader.hasNext()) {
                    if (!version.isSetVersion()) {
                        CityGMLVersion version = CityGMLModules.getCityGMLVersion(reader.getName().getNamespaceURI());
                        if (version != null) {
                            log.debug("Using CityGML " + version + " for the output file.");
                            out.withCityGMLVersion(version);
                        }
                    }

                    info = reader.getParentInfo();
                }

                if (overwrite) {
                    log.debug("Writing temporary output file " + outputFile.toAbsolutePath() + ".");
                } else {
                    log.info("Writing output to file " + outputFile.toAbsolutePath() + ".");
                }

                try (CityGMLChunkWriter writer = createCityGMLChunkWriter(out, outputFile, outputOptions)
                        .withCityModelInfo(info)) {
                    Map<String, Integer> counter = new TreeMap<>();

                    String themes = this.themes != null ? " with theme(s) " + this.themes.stream()
                            .map(theme -> "'" + theme + "'")
                            .collect(Collectors.joining(", ")) : "";
                    log.debug("Reading city objects and removing appearances" + themes + ".");

                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();

                        if (feature instanceof Appearance) {
                            if (!shouldRemove((Appearance) feature, counter)) {
                                writer.writeMember(feature);
                            }
                        } else {
                            if (!onlyGlobal) {
                                remover.process(feature, counter);
                            }

                            writer.writeMember(feature);
                        }

                    }

                    if (!counter.isEmpty()) {
                        counter.forEach((key, value) -> log.debug("Removed " + key + " element(s): " + value));
                    } else {
                        log.debug("No appearance elements removed based on input parameters.");
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

    private boolean shouldRemove(Appearance appearance, Map<String, Integer> counter) {
        if (themes == null
                || (appearance.getTheme() == null && themes.contains(nullTheme))
                || themes.contains(appearance.getTheme())) {
            if (!onlyTextures && !onlyMaterials) {
                count(appearance, counter);
                return true;
            }

            Class<?> target = onlyMaterials ? X3DMaterial.class : AbstractTexture.class;
            List<AbstractSurfaceDataProperty> deleteList = new ArrayList<>();
            appearance.getSurfaceData().stream()
                    .filter(property -> target.isInstance(property.getObject()))
                    .forEach(property -> {
                        count(property.getObject(), counter);
                        deleteList.add(property);
                    });

            appearance.getSurfaceData().removeAll(deleteList);
            if (!appearance.isSetSurfaceData()) {
                count(appearance, counter);
                return true;
            }
        }

        return false;
    }

    private void count(Object object, Map<String, Integer> counter) {
        counter.merge(object.getClass().getSimpleName(), 1, Integer::sum);
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (onlyTextures && onlyMaterials) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --only-textures and --only-materials are mutually exclusive (specify only one)");
        }
    }

    private class AppearanceRemover extends ObjectWalker {
        private Map<String, Integer> counter;

        void process(AbstractFeature feature, Map<String, Integer> counter) {
            this.counter = counter;
            feature.accept(this);
        }

        @Override
        public void visit(Appearance appearance) {
            if (shouldRemove(appearance, counter)) {
                Child property = appearance.getParent();
                Child parent = property.getParent();
                if (parent instanceof GMLObject) {
                    ((GMLObject) parent).unsetProperty(property, true);
                }
            }
        }
    }
}
