package org.citygml4j.tools.command;

import org.citygml4j.CityGMLContext;
import org.citygml4j.builder.cityjson.CityJSONBuilder;
import org.citygml4j.builder.cityjson.json.io.reader.CityJSONInputFactory;
import org.citygml4j.builder.cityjson.json.io.reader.CityJSONReader;
import org.citygml4j.model.citygml.core.CityModel;
import org.citygml4j.model.module.citygml.CityGMLModuleType;
import org.citygml4j.model.module.citygml.CityGMLVersion;
import org.citygml4j.tools.common.log.Logger;
import org.citygml4j.tools.util.Util;
import org.citygml4j.xml.io.CityGMLOutputFactory;
import org.citygml4j.xml.io.writer.CityGMLWriter;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(name = "from-cityjson",
        description = "Converts CityJSON files into CityGML.",
        mixinStandardHelpOptions = true)
public class FromCityJSONCommand implements CityGMLTool {

    @CommandLine.Option(names = "--overwrite-files", description = "Overwrite output file(s).")
    private boolean overwriteOutputFiles;

    @CommandLine.Mixin
    private StandardCityGMLOutputOptions cityGMLOutput;

    @CommandLine.Mixin
    private StandardInputOptions input;

    @CommandLine.ParentCommand
    private MainCommand main;

    @Override
    public boolean execute() throws Exception {
        Logger log = Logger.getInstance();
        log.info("Executing command 'from-cityjson'.");

        CityJSONBuilder builder = CityGMLContext.getInstance().createCityJSONBuilder();
        CityJSONInputFactory in = builder.createCityJSONInputFactory();

        CityGMLVersion targetVersion = cityGMLOutput.getVersion();
        CityGMLOutputFactory out = main.getCityGMLBuilder().createCityGMLOutputFactory(targetVersion);

        log.debug("Searching for CityJSON input files.");
        List<Path> inputFiles = new ArrayList<>();
        try {
            inputFiles.addAll(Util.listFiles(input.getFile(), "**.{json,cityjson}"));
            log.info("Found " + inputFiles.size() + " file(s) at '" + input.getFile() + "'.");
        } catch (IOException e) {
            log.warn("Failed to find file(s) at '" + input.getFile() + "'.");
        }

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file '" + inputFile.toAbsolutePath() + "'.");

            Path outputFile = outputFile = Util.replaceFileExtension(inputFile, ".gml");
            log.info("Writing output to file '" + outputFile.toAbsolutePath() + "'.");

            if (!overwriteOutputFiles && Files.exists(outputFile)) {
                log.error("The output file '" + outputFile.toAbsolutePath() + "' already exists. Remove it first.");
                continue;
            }

            try (CityJSONReader reader = in.createCityJSONReader(inputFile.toFile());
                 CityGMLWriter writer = out.createCityGMLWriter(outputFile.toFile())) {

                log.debug("Reading CityJSON input file into main memory.");
                CityModel cityModel = reader.read();

                writer.setPrefixes(targetVersion);
                writer.setSchemaLocations(targetVersion);
                writer.setDefaultNamespace(targetVersion.getCityGMLModule(CityGMLModuleType.CORE));
                writer.setIndentString("  ");

                writer.write(cityModel);
            }

            log.debug("Successfully converted CityJSON file into CityGML.");
        }

        return true;
    }

    @Override
    public void validate() throws CommandLine.ParameterException {
        // nothing to do
    }
}
