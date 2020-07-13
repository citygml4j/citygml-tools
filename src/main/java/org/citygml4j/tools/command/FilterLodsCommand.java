/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2019 Claus Nagel <claus.nagel@gmail.com>
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

import org.citygml4j.builder.jaxb.CityGMLBuilder;
import org.citygml4j.builder.jaxb.CityGMLBuilderException;
import org.citygml4j.model.citygml.CityGML;
import org.citygml4j.model.citygml.appearance.Appearance;
import org.citygml4j.model.citygml.cityobjectgroup.CityObjectGroup;
import org.citygml4j.model.citygml.core.AbstractCityObject;
import org.citygml4j.model.gml.feature.AbstractFeature;
import org.citygml4j.tools.CityGMLTools;
import org.citygml4j.tools.command.options.CityGMLOutputOptions;
import org.citygml4j.tools.command.options.InputOptions;
import org.citygml4j.tools.command.options.LoggingOptions;
import org.citygml4j.tools.common.helper.CityModelInfoHelper;
import org.citygml4j.tools.common.helper.GlobalAppReader;
import org.citygml4j.tools.common.log.Logger;
import org.citygml4j.tools.lodfilter.LodFilter;
import org.citygml4j.tools.lodfilter.LodFilterMode;
import org.citygml4j.tools.util.ObjectRegistry;
import org.citygml4j.tools.util.Util;
import org.citygml4j.xml.io.reader.CityGMLReadException;
import org.citygml4j.xml.io.reader.CityGMLReader;
import org.citygml4j.xml.io.writer.CityGMLWriteException;
import org.citygml4j.xml.io.writer.CityModelWriter;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@CommandLine.Command(name = "filter-lods",
        description = "Filters the LoD representations of city objects.",
        versionProvider = CityGMLTools.class,
        mixinStandardHelpOptions = true,
        showAtFileInUsageHelp = true)
public class FilterLodsCommand implements CityGMLTool {
    @CommandLine.Option(names = "--lod", paramLabel = "<lod>", required = true, split = ",", description = "LoD to filter: 0, 1, 2, 3, 4.")
    private List<Integer> lods;

    @CommandLine.Option(names = "--mode", description = "Filter mode: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private LodFilterMode mode = LodFilterMode.KEEP;

    @CommandLine.Option(names = "--keep-cityobjects-without-lods", description = "Do not delete city objects that lack an LoD representation after filtering.")
    private boolean keepCityObjectsWithoutLods;

    @CommandLine.Option(names = "--overwrite-files", description = "Overwrite input file(s).")
    private boolean overwriteInputFiles;

    @CommandLine.Mixin
    private CityGMLOutputOptions cityGMLOutput;

    @CommandLine.Mixin
    private InputOptions input;

    @CommandLine.Mixin
    LoggingOptions logging;

    @Override
    public Integer call() throws Exception {
        Logger log = Logger.getInstance();
        String fileNameSuffix = "_filtered_lods";

        CityGMLBuilder cityGMLBuilder = ObjectRegistry.getInstance().get(CityGMLBuilder.class);
        GlobalAppReader globalAppReader = new GlobalAppReader(cityGMLBuilder);

        log.debug("Searching for CityGML input files.");
        List<Path> inputFiles;
        try {
            inputFiles = new ArrayList<>(Util.listFiles(input.getFile(), "**.{gml,xml}", fileNameSuffix));
            log.info("Found " + inputFiles.size() + " file(s) at '" + input.getFile() + "'.");
        } catch (IOException e) {
            log.warn("Failed to find file(s) at '" + input.getFile() + "'.");
            return 0;
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

            List<CityObjectGroup> groups = null;
            List<Appearance> appearances;
            try {
                log.debug("Reading global appearances from input file.");
                appearances = globalAppReader.readGlobalApps(inputFile);
            } catch (CityGMLBuilderException | CityGMLReadException e) {
                log.error("Failed to read global appearances.", e);
                return 1;
            }

            LodFilter lodFilter = new LodFilter()
                    .withFilterMode(mode)
                    .keepCityObjectsWithoutLods(keepCityObjectsWithoutLods)
                    .withGlobalApps(appearances);

            for (int lod : lods)
                lodFilter.filterLod(lod);

            log.debug("Reading city objects from input file and filtering LoDs.");

            try (CityGMLReader reader = input.createCityGMLReader(inputFile, input.createSkipFilter("CityModel", "Appearance"));
                 CityModelWriter writer = cityGMLOutput.createCityModelWriter(outputFile)) {
                boolean isInitialized = false;

                while (reader.hasNext()) {
                    CityGML cityGML = reader.nextFeature();

                    if (!isInitialized) {
                        writer.setCityModelInfo(CityModelInfoHelper.getCityModelInfo(cityGML, reader.getParentInfo()));
                        writer.writeStartDocument();
                        isInitialized = true;
                    }

                    if (cityGML instanceof CityObjectGroup) {
                        if (groups == null)
                            groups = new ArrayList<>();

                        groups.add((CityObjectGroup) cityGML);
                    } else if (cityGML instanceof AbstractCityObject) {
                        AbstractCityObject cityObject = (AbstractCityObject) cityGML;
                        cityObject = lodFilter.apply(cityObject);
                        if (cityObject != null)
                            writer.writeFeatureMember(cityObject);
                    } else if (cityGML instanceof AbstractFeature)
                        writer.writeFeatureMember((AbstractFeature) cityGML);
                }

                if (groups != null) {
                    cleanupGroups(groups, lodFilter.getRemovedCityObjectIds());
                    for (CityObjectGroup group : groups)
                        writer.writeFeatureMember(group);
                }

                if (lodFilter.hasRemainingGlobalApps()) {
                    for (Appearance appearance : lodFilter.getRemainingGlobalApps())
                        writer.writeFeatureMember(appearance);
                }

            } catch (CityGMLBuilderException | CityGMLReadException e) {
                log.error("Failed to read city objects.", e);
                return 1;
            } catch (CityGMLWriteException e) {
                log.error("Failed to write city objects.", e);
                return 1;
            }

            if (overwriteInputFiles) {
                try {
                    log.debug("Replacing input file with temporary file.");
                    Files.delete(inputFile);
                    Files.move(outputFile, outputFile.resolveSibling(inputFile.getFileName()));
                } catch (IOException e) {
                    log.error("Failed to overwrite input file.", e);
                    return 1;
                }
            }
        }

        return 0;
    }

    private void cleanupGroups(List<CityObjectGroup> groups, Set<String> ids) {
        for (CityObjectGroup group : groups) {
            if (group.isSetGroupMember())
                group.getGroupMember().removeIf(member -> member.isSetHref()
                        && ids.contains(member.getHref().replaceAll("^#", "")));

            if (group.isSetGroupParent()
                    && group.getGroupParent().isSetHref()
                    && ids.contains(group.getGroupParent().getHref().replaceAll("^#", "")))
                group.unsetGroupParent();
        }

        Set<String> groupIds = new HashSet<>();
        for (Iterator<CityObjectGroup> iter = groups.iterator(); iter.hasNext(); ) {
            CityObjectGroup group = iter.next();

            if (!group.isSetGroupMember()) {
                iter.remove();
                if (group.isSetId())
                    groupIds.add(group.getId());
            }
        }

        if (!groupIds.isEmpty())
            cleanupGroups(groups, groupIds);
    }
}
