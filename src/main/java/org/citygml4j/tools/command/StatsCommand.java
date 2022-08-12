package org.citygml4j.tools.command;

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.core.AbstractGenericAttribute;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.log.LogLevel;
import org.citygml4j.tools.option.IdOption;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.util.*;
import org.citygml4j.xml.reader.ChunkOptions;
import org.citygml4j.xml.schema.CityGMLSchemaHandler;
import org.xmlobjects.gml.adapter.feature.BoundingShapeAdapter;
import org.xmlobjects.gml.model.feature.BoundingShape;
import org.xmlobjects.gml.model.geometry.AbstractGeometry;
import org.xmlobjects.gml.util.GMLConstants;
import org.xmlobjects.schema.SchemaHandler;
import org.xmlobjects.stream.EventType;
import org.xmlobjects.stream.XMLReadException;
import org.xmlobjects.stream.XMLReader;
import org.xmlobjects.stream.XMLReaderFactory;
import org.xmlobjects.xml.Attributes;
import picocli.CommandLine;

import javax.xml.namespace.QName;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

@CommandLine.Command(name = "stats",
        description = "Generates statistics about the content of CityGML files.")
public class StatsCommand extends CityGMLTool {
    @CommandLine.Option(names = {"-c", "--compute-extent"},
            description = "Compute the extent for the entire CityGML file. By default, the extent is taken from " +
                    "the CityModel and calculated only as fallback. No coordinate transformation is applied in " +
                    "the calculation.")
    private boolean computeEnvelope;

    @CommandLine.ArgGroup
    private IdOption idOption;

    @CommandLine.Option(names = {"-t", "--only-top-level"},
            description = "Only count top-level city objects.")
    private boolean onlyTopLevelFeatures;

    @CommandLine.Option(names = {"-H", "--object-hierarchy"},
            description = "Generate an aggregated overview of the nested hierarchies of the city objects.")
    private boolean generateObjectHierarchy;

    @CommandLine.Option(names = "--no-json-report", negatable = true, defaultValue = "true",
            description = "Save the statistics as separate JSON report for each input file " +
                    "(default: ${DEFAULT-VALUE}).")
    private boolean jsonReport;

    @CommandLine.Option(names = {"-s", "--summary-report"}, paramLabel = "<file>",
            description = "Write the overall statistics over all input file(s) as JSON report to the " +
                    "specified output file.")
    private Path summaryFile;

    @CommandLine.Mixin
    private InputOptions inputOptions;

    private final String suffix = "__statistics";

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

        SchemaHandler schemaHandler;
        try {
             schemaHandler = CityGMLSchemaHandler.newInstance();
        } catch (Exception e) {
            throw new ExecutionException("Failed to create schema handler.", e);
        }

        ObjectMapper objectMapper = new ObjectMapper();
        ChunkOptions chunkOptions = ChunkOptions.defaults();
        SchemaHelper schemaHelper = SchemaHelper.of(schemaHandler);
        Statistics summary = null;

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile.toAbsolutePath() + ".");

            Statistics statistics = Statistics.of(inputFile);
            StatisticsProcessor processor = StatisticsProcessor.of(statistics, getCityGMLContext())
                    .computeEnvelope(computeEnvelope)
                    .onlyTopLevelFeatures(onlyTopLevelFeatures)
                    .generateObjectHierarchy(generateObjectHierarchy);

            if (idOption != null) {
                statistics.withCityObjectIds(idOption.getIds());

                log.debug("Reading global appearances from input file.");
                processor.withGlobalAppearances(GlobalObjectsReader.onlyAppearances()
                        .read(inputFile, getCityGMLContext())
                        .getAppearances());
            }

            log.debug("Reading city objects and generating statistics.");

            try (XMLReader reader = XMLReaderFactory.newInstance(getCityGMLContext().getXMLObjects())
                    .withSchemaHandler(schemaHandler)
                    .createReader(inputFile, inputOptions.getEncoding())) {
                Deque<QName> elements = new ArrayDeque<>();
                Deque<Integer> features = new ArrayDeque<>();
                boolean isTopLevel = false;

                while (reader.hasNext()) {
                    EventType event = reader.nextTag();
                    QName lastElement = elements.peek();
                    int depth = reader.getDepth();
                    int lastFeature = Objects.requireNonNullElseGet(features.peek(),
                            () -> idOption == null ? 0 : Integer.MAX_VALUE);

                    if (event == EventType.START_ELEMENT) {
                        QName element = reader.getName();
                        if (chunkOptions.containsProperty(element)) {
                            isTopLevel = true;
                        } else {
                            if (schemaHelper.isAppearance(element) && depth > lastFeature) {
                                processor.process(element, reader.getObject(Appearance.class), isTopLevel);
                            } else if (schemaHelper.isFeature(element)
                                    && (idOption == null || depth > lastFeature || hasMatchingIdentifier(reader))) {
                                processor.process(element, reader.getPrefix(), isTopLevel, depth, statistics);
                                features.push(depth);
                            } else if (schemaHelper.isBoundingShape(element)) {
                                boolean isCityModel = lastElement != null && schemaHelper.isCityModel(lastElement);
                                if (isCityModel || depth > lastFeature) {
                                    BoundingShape boundedBy = reader.getObjectUsingBuilder(BoundingShapeAdapter.class);
                                    processor.process(boundedBy, depth - 1, isCityModel, statistics);
                                }
                            } else if (depth > lastFeature) {
                                if (schemaHelper.isGeometry(element)) {
                                    processor.process(element, reader.getObject(AbstractGeometry.class), lastElement);
                                } else if (schemaHelper.isImplicitGeometry(element)) {
                                    processor.process(element, reader.getObject(ImplicitGeometry.class), lastElement);
                                } else if (schemaHelper.isGenericAttribute(element)) {
                                    processor.process(reader.getObject(AbstractGenericAttribute.class));
                                }
                            }

                            isTopLevel = false;
                        }

                        if (reader.getDepth() == depth) {
                            elements.push(element);
                        }
                    } else if (event == EventType.END_ELEMENT) {
                        processor.updateDepth(depth);
                        elements.pop();
                        if (lastFeature == depth + 1) {
                            features.pop();
                        }
                    }
                }
            } catch (Exception e) {
                throw new ExecutionException("Failed to read file " + inputFile.toAbsolutePath() + ".", e);
            }

            if (jsonReport) {
                Path outputFile = inputFile.resolveSibling(appendFileNameSuffix(inputFile.resolveSibling(
                        replaceFileExtension(inputFile, "json")), suffix));

                log.info("Writing statistics as JSON report to file " + outputFile + ".");
                writeStatistics(outputFile, statistics, objectMapper);
            }

            if (summary == null) {
                summary = statistics;
            } else {
                summary.merge(statistics);
            }
        }

        if (summary != null) {
            if (summaryFile != null) {
                log.info("Writing overall statistics over all input file(s) as JSON report to file " + summaryFile + ".");
                writeStatistics(summaryFile, summary, objectMapper);
            }

            summary.print(objectMapper, (msg) -> log.print(LogLevel.INFO, msg));
        }

        return CommandLine.ExitCode.OK;
    }

    private boolean hasMatchingIdentifier(XMLReader reader) {
        try {
            Attributes attributes = reader.getAttributes();
            String id = attributes.getValue(GMLConstants.GML_3_2_NAMESPACE, "id").get();
            if (id == null) {
                id = attributes.getValue(GMLConstants.GML_3_1_NAMESPACE, "id").get();
            }

            return id != null && idOption.getIds().contains(id);
        } catch (XMLReadException e) {
            return false;
        }
    }

    private void writeStatistics(Path outputFile, Statistics statistics, ObjectMapper objectMapper) throws ExecutionException {
        try (JsonGenerator writer = objectMapper.createGenerator(outputFile.toFile(), JsonEncoding.UTF8)) {
            writer.setPrettyPrinter(new DefaultPrettyPrinter());
            writer.writeObject(statistics.toJson(objectMapper));
        } catch (Exception e) {
            throw new ExecutionException("Failed to write JSON file " + outputFile.toAbsolutePath() + ".", e);
        }
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (onlyTopLevelFeatures && generateObjectHierarchy) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --only-top-level and --object-hierarchy are mutually exclusive (specify only one)");
        }
    }
}
