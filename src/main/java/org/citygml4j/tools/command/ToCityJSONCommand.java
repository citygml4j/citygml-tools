package org.citygml4j.tools.command;

import org.citygml4j.CityGMLContext;
import org.citygml4j.builder.cityjson.CityJSONBuilder;
import org.citygml4j.builder.cityjson.json.io.writer.CityJSONOutputFactory;
import org.citygml4j.builder.cityjson.json.io.writer.CityJSONWriter;
import org.citygml4j.builder.cityjson.marshal.util.DefaultTextureVerticesBuilder;
import org.citygml4j.builder.cityjson.marshal.util.DefaultVerticesBuilder;
import org.citygml4j.builder.cityjson.marshal.util.DefaultVerticesTransformer;
import org.citygml4j.builder.jaxb.CityGMLBuilderException;
import org.citygml4j.model.citygml.CityGML;
import org.citygml4j.model.citygml.core.CityModel;
import org.citygml4j.tools.common.log.Logger;
import org.citygml4j.tools.util.Util;
import org.citygml4j.xml.io.CityGMLInputFactory;
import org.citygml4j.xml.io.reader.CityGMLReader;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(name = "to-cityjson",
        description = "Converts CityGML files into CityJSON.",
        mixinStandardHelpOptions = true)
public class ToCityJSONCommand implements CityGMLTool {

    @CommandLine.Option(names = "--vertices-digits", paramLabel = "<digits>", description = "Number of digits to keep for geometry vertices (default: ${DEFAULT-VALUE}).")
    private int verticesDigites = 3;

    @CommandLine.Option(names = "--template-digits", paramLabel = "<digits>", description = "Number of digits to keep for template vertices (default: ${DEFAULT-VALUE}).")
    private int templateDigites = 3;

    @CommandLine.Option(names = "--texture-vertices-digits", paramLabel = "<digits>", description = "Number of digits to keep for texture vertices (default: ${DEFAULT-VALUE}).")
    private int textureVerticesDigites = 7;

    @CommandLine.Option(names = {"-c", "--compress"}, description = "Compress file by storing vertices with integers.")
    private boolean compress;

    @CommandLine.Option(names = "--compress-digits", paramLabel = "<digits>", description = "Number of digits to keep in compression (default: ${DEFAULT-VALUE}).")
    private int compressDigits = 3;

    @CommandLine.Option(names = "--overwrite-files", description = "Overwrite output file(s).")
    private boolean overwriteOutputFiles;

    @CommandLine.Mixin
    private StandardInputOptions input;

    @CommandLine.ParentCommand
    private MainCommand main;

    @Override
    public boolean execute() throws Exception {
        Logger log = Logger.getInstance();
        log.info("Executing command 'to-cityjson'.");

        CityGMLInputFactory in;
        try {
            in = main.getCityGMLBuilder().createCityGMLInputFactory();
        } catch (CityGMLBuilderException e) {
            log.error("Failed to create CityGML input factory", e);
            return false;
        }

        CityJSONBuilder builder = CityGMLContext.getInstance().createCityJSONBuilder();
        CityJSONOutputFactory out = builder.createCityJSONOutputFactory();

        // set builder for geometry, template and texture vertices
        out.setVerticesBuilder(new DefaultVerticesBuilder().withSignificantDigits(verticesDigites));
        out.setTemplatesVerticesBuilder(new DefaultVerticesBuilder().withSignificantDigits(templateDigites));
        out.setTextureVerticesBuilder(new DefaultTextureVerticesBuilder().withSignificantDigits(textureVerticesDigites));

        // apply compression if requested
        if (compress)
            out.setVerticesTransformer(new DefaultVerticesTransformer().withSignificantDigits(compressDigits));

        log.debug("Searching for CityGML input files.");
        List<Path> inputFiles = new ArrayList<>();
        try {
            inputFiles.addAll(Util.listFiles(input.getFile(), "**.{gml,xml}"));
            log.info("Found " + inputFiles.size() + " file(s) at '" + input.getFile() + "'.");
        } catch (IOException e) {
            log.warn("Failed to find file(s) at '" + input.getFile() + "'.");
        }

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file '" + inputFile.toAbsolutePath() + "'.");

            Path outputFile = Util.replaceFileExtension(inputFile, ".json");
            log.info("Writing output to file '" + outputFile.toAbsolutePath() + "'.");

            if (!overwriteOutputFiles && Files.exists(outputFile)) {
                log.error("The output file '" + outputFile.toAbsolutePath() + "' already exists. Remove it first.");
                continue;
            }

            try (CityGMLReader reader = in.createCityGMLReader(inputFile.toFile());
                 CityJSONWriter writer = out.createCityJSONWriter(outputFile.toFile())) {

                log.debug("Reading CityJSON input file into main memory.");
                CityGML cityGML = reader.nextFeature();

                if (cityGML instanceof CityModel)
                    writer.write((CityModel) cityGML);

                log.debug("Successfully converted CityGML file into CityJSON.");
            }
        }

        return true;
    }

    @Override
    public void validate() throws CommandLine.ParameterException {
        // nothing to do
    }
}
