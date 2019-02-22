package org.citygml4j.tools.command;

import org.citygml4j.builder.jaxb.CityGMLBuilderException;
import org.citygml4j.model.citygml.CityGML;
import org.citygml4j.model.citygml.CityGMLClass;
import org.citygml4j.model.citygml.core.CityModel;
import org.citygml4j.model.gml.feature.AbstractFeature;
import org.citygml4j.tools.common.log.Logger;
import org.citygml4j.tools.reproject.ReprojectionBuilder;
import org.citygml4j.tools.reproject.ReprojectionBuilderException;
import org.citygml4j.tools.reproject.ReprojectionException;
import org.citygml4j.tools.reproject.Reprojector;
import org.citygml4j.tools.util.Util;
import org.citygml4j.xml.io.reader.CityGMLReadException;
import org.citygml4j.xml.io.reader.CityGMLReader;
import org.citygml4j.xml.io.reader.ParentInfo;
import org.citygml4j.xml.io.writer.CityGMLWriteException;
import org.citygml4j.xml.io.writer.CityModelInfo;
import org.citygml4j.xml.io.writer.CityModelWriter;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@CommandLine.Command(name = "reproject",
        description = "Reprojects city objects to a new spatial reference system.",
        versionProvider = MainCommand.class,
        mixinStandardHelpOptions = true)
public class ReprojectCommand implements CityGMLTool {

    @CommandLine.Option(names = "--target-crs", paramLabel = "<crs>", required = true, description = "Target CRS for the reprojection given as EPSG code, as GML srsName or as OGC WKT with escaped quotes.")
    private String targetCRS;

    @CommandLine.Option(names = "--target-name", paramLabel = "<name>", description = "GML srsName to be used in the CityGML output file.")
    private String targetSRSName;

    @CommandLine.Option(names = "--target-force-xy", description = "Force XY axis order for target CRS.")
    private boolean targetForceXY;

    @CommandLine.Option(names = "--keep-height-values", description = "Do not reproject height values.")
    private boolean keepHeightValues;

    @CommandLine.Option(names = "--source-crs", paramLabel = "<crs>", description = "If provided, the source CRS overrides any reference system in the input file(s). Given as EPSG code, as GML srsName or as OGC WKT with escaped quotes.")
    private String sourceCRS;

    @CommandLine.Option(names = "--source-swap-xy", description = "Swap XY axes for all geometries in the input file(s).")
    private boolean sourceSwapXY;

    @CommandLine.Option(names = "--overwrite-files", description = "Overwrite input file(s).")
    private boolean overwriteInputFiles;

    @CommandLine.Mixin
    private StandardCityGMLOutputOptions cityGMLOutput;

    @CommandLine.Mixin
    private StandardInputOptions input;

    @CommandLine.ParentCommand
    private MainCommand main;

    @Override
    public boolean execute() {
        Logger log = Logger.getInstance();
        String fileNameSuffix = "_reprojected";

        Reprojector reprojector;
        try {
            reprojector = ReprojectionBuilder.defaults()
                    .withTargetCRS(targetCRS)
                    .withTargetSRSName(targetSRSName)
                    .withSourceCRS(sourceCRS)
                    .forceXYAxisOrderForTargetCRS(targetForceXY)
                    .keepHeightValues(keepHeightValues)
                    .swapXYAxisOrderForSourceGeometries(sourceSwapXY)
                    .build();

            log.debug("Using the following target CRS definition:");
            log.print(reprojector.getTargetCRSAsWKT());

        } catch (ReprojectionBuilderException e) {
            log.error("Failed to create reprojection configuration.", e);
            return false;
        }

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

            log.debug("Reading city objects from input file and reprojecting coordinates.");

            try (CityGMLReader reader = input.createCityGMLReader(inputFile, main.getCityGMLBuilder(), true);
                 CityModelWriter writer = cityGMLOutput.createCityModelWriter(outputFile, main.getCityGMLBuilder())) {
                boolean isInitialized = false;

                while (reader.hasNext()) {
                    CityGML cityGML = reader.nextFeature();

                    if (!isInitialized) {
                        ParentInfo parentInfo = reader.getParentInfo();
                        if (parentInfo != null && parentInfo.getCityGMLClass() == CityGMLClass.CITY_MODEL) {
                            CityModelInfo cityModelInfo = new CityModelInfo(parentInfo);

                            if (cityModelInfo.isSetBoundedBy()) {
                                if (cityModelInfo.getBoundedBy().isSetEnvelope()
                                        &&cityModelInfo.getBoundedBy().getEnvelope().isSetSrsName())
                                    reprojector.setFallbackSRSName(cityModelInfo.getBoundedBy().getEnvelope().getSrsName());

                                reprojector.reproject(cityModelInfo.getBoundedBy());
                            }

                            writer.setCityModelInfo(cityModelInfo);
                            writer.writeStartDocument();
                            isInitialized = true;
                        }
                    }

                    if (cityGML instanceof AbstractFeature && !(cityGML instanceof CityModel)) {
                        AbstractFeature feature = (AbstractFeature) cityGML;
                        reprojector.reproject(feature);
                        writer.writeFeatureMember(feature);
                    }
                }
            } catch (ReprojectionException e) {
                log.error("Failed to reproject city objects.", e);
                return false;
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
