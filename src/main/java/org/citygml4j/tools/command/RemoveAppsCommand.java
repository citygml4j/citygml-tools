/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2013-2019 Claus Nagel <claus.nagel@gmail.com>
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

import org.citygml4j.builder.jaxb.CityGMLBuilderException;
import org.citygml4j.model.citygml.CityGML;
import org.citygml4j.model.citygml.appearance.AbstractTexture;
import org.citygml4j.model.citygml.appearance.Appearance;
import org.citygml4j.model.citygml.appearance.GeoreferencedTexture;
import org.citygml4j.model.citygml.appearance.ParameterizedTexture;
import org.citygml4j.model.citygml.appearance.SurfaceDataProperty;
import org.citygml4j.model.citygml.appearance.X3DMaterial;
import org.citygml4j.model.citygml.core.AbstractCityObject;
import org.citygml4j.model.citygml.core.CityModel;
import org.citygml4j.model.gml.feature.AbstractFeature;
import org.citygml4j.tools.common.helper.CityModelInfoReader;
import org.citygml4j.tools.common.log.Logger;
import org.citygml4j.tools.util.Util;
import org.citygml4j.util.walker.FeatureWalker;
import org.citygml4j.xml.io.reader.CityGMLReadException;
import org.citygml4j.xml.io.reader.CityGMLReader;
import org.citygml4j.xml.io.writer.CityGMLWriteException;
import org.citygml4j.xml.io.writer.CityModelWriter;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@CommandLine.Command(name = "remove-apps",
        description = "Removes appearances from city objects.",
        versionProvider = MainCommand.class,
        mixinStandardHelpOptions = true)
public class RemoveAppsCommand implements CityGMLTool {

    @CommandLine.Option(names = "--theme", paramLabel = "<name>", split = ",", description = "Only remove appearances of the given theme(s). Use 'null' as name for the null theme.")
    private List<String> theme;

    @CommandLine.Option(names = "--only-textures", description = "Only remove textures.")
    private boolean onlyTextures;

    @CommandLine.Option(names = "--only-materials", description = "Only remove materials.")
    private boolean onlyMaterials;

    @CommandLine.Option(names = "--only-global", description = "Only remove global appearances.")
    private boolean onlyGlobal;

    @CommandLine.Option(names = "--overwrite-files", description = "Overwrite input file(s).")
    private boolean overwriteInputFiles;

    @CommandLine.Mixin
    private StandardCityGMLOutputOptions cityGMLOutput;

    @CommandLine.Mixin
    private StandardInputOptions input;

    @CommandLine.ParentCommand
    private MainCommand main;

    @Override
    public boolean execute() throws Exception {
        Logger log = Logger.getInstance();
        String fileNameSuffix = "_wo-app";

        log.debug("Searching for CityGML input files.");
        List<Path> inputFiles = new ArrayList<>();
        try {
            inputFiles.addAll(Util.listFiles(input.getFile(), "**.{gml,xml}", fileNameSuffix));
            log.info("Found " + inputFiles.size() + " file(s) at '" + input.getFile() + "'.");
        } catch (IOException e) {
            log.warn("Failed to find file(s) at '" + input.getFile() + "'.");
        }

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file '" + inputFile.toAbsolutePath() + "'.");

            Path outputFile;
            if (!overwriteInputFiles) {
                outputFile = Util.addFileNameSuffix(inputFile, fileNameSuffix);
                log.info("Writing output to file '" + outputFile.toAbsolutePath() + "'.");
            } else {
                outputFile = inputFile.resolveSibling("tmp-" + UUID.randomUUID());
                log.debug("Writing temporary output file '" + outputFile.toAbsolutePath() + "'.");
            }

            log.debug("Reading city objects from input file and removing appearances.");

            try (CityGMLReader reader = input.createCityGMLReader(inputFile, main.getCityGMLBuilder(), true);
                 CityModelWriter writer = cityGMLOutput.createCityModelWriter(outputFile, main.getCityGMLBuilder())) {
                boolean isInitialized = false;
                Map<Class<?>, Integer> counter = new HashMap<>();

                while (reader.hasNext()) {
                    CityGML cityGML = reader.nextFeature();

                    // write city model
                    if (!isInitialized) {
                        writer.setCityModelInfo(CityModelInfoReader.getCityModelInfo(cityGML, reader.getParentInfo()));
                        writer.writeStartDocument();
                        isInitialized = true;
                    }

                    if (cityGML instanceof AbstractCityObject) {
                        AbstractCityObject cityObject = (AbstractCityObject) cityGML;

                        if (!onlyGlobal) {
                            cityObject.accept(new FeatureWalker() {
                                public void visit(AbstractCityObject cityObject) {
                                    cityObject.getAppearance().removeIf(p -> process(p.getAppearance(), counter));
                                    super.visit(cityObject);
                                }
                            });
                        }

                        writer.writeFeatureMember(cityObject);
                    }

                    else if (cityGML instanceof Appearance) {
                        Appearance appearance = (Appearance) cityGML;
                        if (!process(appearance, counter))
                            writer.writeFeatureMember(appearance);
                    }

                    else if (cityGML instanceof AbstractFeature && !(cityGML instanceof CityModel))
                        writer.writeFeatureMember((AbstractFeature) cityGML);
                }

                if (onlyTextures) {
                    log.debug("Removed ParameterizedTexture elements: " + counter.getOrDefault(ParameterizedTexture.class, 0));
                    log.debug("Removed GeoreferencedTexture elements: " + counter.getOrDefault(GeoreferencedTexture.class, 0));
                }

                if (onlyMaterials)
                    log.debug("Removed X3DMaterial elements: " + counter.getOrDefault(X3DMaterial.class, 0));

                log.debug("Removed Appearance elements: " + counter.getOrDefault(Appearance.class, 0));

            } catch (CityGMLBuilderException | CityGMLReadException e) {
                log.error("Failed to read city objects.", e);
                return false;
            } catch (CityGMLWriteException e) {
                log.error("Failed to write city objects.", e);
                return false;
            }

            if (overwriteInputFiles) {
                try {
                    log.debug("Replacing input file with temporary file.");
                    Files.delete(inputFile);
                    Files.move(outputFile, outputFile.resolveSibling(inputFile.getFileName()));
                } catch (IOException e) {
                    log.error("Failed to overwrite input file.", e);
                    return false;
                }
            }
        }

        return true;
    }

    private boolean process(Appearance appearance, Map<Class<?>, Integer> counter) {
        if (appearance != null && satisfiesTheme(appearance)) {
            if (onlyMaterials == onlyTextures) {
                counter.merge(Appearance.class, 1, Integer::sum);
                return true;
            }

            for (Iterator<SurfaceDataProperty> iter = appearance.getSurfaceDataMember().iterator(); iter.hasNext(); ) {
                SurfaceDataProperty property = iter.next();
                if (onlyTextures && property.getSurfaceData() instanceof AbstractTexture) {
                    counter.merge(property.getSurfaceData().getClass(), 1, Integer::sum);
                    iter.remove();
                } else if (onlyMaterials && property.getSurfaceData() instanceof X3DMaterial) {
                    counter.merge(X3DMaterial.class, 1, Integer::sum);
                    iter.remove();
                }
            }

            if (!appearance.isSetSurfaceDataMember()) {
                counter.merge(Appearance.class, 1, Integer::sum);
                return true;
            }
        }

        return false;
    }

    private boolean satisfiesTheme(Appearance appearance) {
        return theme == null
                || (!appearance.isSetTheme() && theme.contains("null"))
                || theme.contains(appearance.getTheme());
    }
}
