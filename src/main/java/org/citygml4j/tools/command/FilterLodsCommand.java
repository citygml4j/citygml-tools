package org.citygml4j.tools.command;

import org.citygml4j.builder.jaxb.CityGMLBuilderException;
import org.citygml4j.model.citygml.CityGML;
import org.citygml4j.model.citygml.core.AbstractCityObject;
import org.citygml4j.model.citygml.core.CityModel;
import org.citygml4j.model.gml.feature.AbstractFeature;
import org.citygml4j.tools.common.helper.CityModelInfoReader;
import org.citygml4j.tools.common.log.Logger;
import org.citygml4j.tools.lodfilter.LodFilter;
import org.citygml4j.tools.lodfilter.LodFilterMode;
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
import java.util.List;
import java.util.UUID;

@CommandLine.Command(name = "filter-lods",
        description = "Filters the LoD representations of city objects.",
        versionProvider = MainCommand.class,
        mixinStandardHelpOptions = true)
public class FilterLodsCommand implements CityGMLTool {
    @CommandLine.Option(names = "--lod", paramLabel = "<lod>", required = true, split = ",", description = "LoD to filter: 0, 1, 2, 3, 4.")
    private List<Integer> lods;

    @CommandLine.Option(names = "--lod-mode", description = "Filter mode: ${COMPLETION-CANDIDATES}.")
    private LodFilterMode mode;

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
        String fileNameSuffix = "_filtered_lods";

        LodFilter lodFilter = new LodFilter();

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

            log.debug("Reading city objects from input file and filtering LoDs.");

            try (CityGMLReader reader = input.createCityGMLReader(inputFile, main.getCityGMLBuilder(), true);
                 CityModelWriter writer = cityGMLOutput.createCityModelWriter(outputFile, main.getCityGMLBuilder())) {
                boolean isInitialized = false;

                while (reader.hasNext()) {
                    CityGML cityGML = reader.nextFeature();

                    if (!isInitialized) {
                        writer.setCityModelInfo(CityModelInfoReader.getCityModelInfo(cityGML, reader.getParentInfo()));
                        writer.writeStartDocument();
                        isInitialized = true;
                    }

                    if (cityGML instanceof AbstractCityObject) {
                        AbstractCityObject cityObject = (AbstractCityObject) cityGML;
                        cityObject = lodFilter.filterLod(cityObject);
                        if (cityObject != null)
                            writer.writeFeatureMember(cityObject);
                    } else if (cityGML instanceof AbstractFeature && !(cityGML instanceof CityModel))
                        writer.writeFeatureMember((AbstractFeature) cityGML);
                }
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
}
