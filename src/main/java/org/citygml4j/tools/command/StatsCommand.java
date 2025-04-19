/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2025 Claus Nagel <claus.nagel@gmail.com>
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

import com.fasterxml.jackson.core.JsonEncoding;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.citygml4j.core.model.appearance.Appearance;
import org.citygml4j.core.model.core.AbstractGenericAttribute;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.tools.log.LogLevel;
import org.citygml4j.tools.option.IdOptions;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.util.GlobalObjectsReader;
import org.citygml4j.tools.util.SchemaHelper;
import org.citygml4j.tools.util.Statistics;
import org.citygml4j.tools.util.StatisticsProcessor;
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
import java.util.*;

@CommandLine.Command(name = "stats",
        description = "Generate statistics about the content of CityGML files.")
public class StatsCommand extends CityGMLTool {
    @CommandLine.Mixin
    private InputOptions inputOptions;

    @CommandLine.Option(names = {"-c", "--compute-extent"},
            description = "Compute the extent of the entire CityGML file. By default, the extent is taken from " +
                    "the CityModel and only computed if missing.")
    private boolean computeEnvelope;

    @CommandLine.ArgGroup
    private IdOptions idOptions;

    @CommandLine.Option(names = {"-t", "--only-top-level"},
            description = "Only count top-level city objects.")
    private boolean onlyTopLevelFeatures;

    @CommandLine.Option(names = {"-H", "--object-hierarchy"},
            description = "Generate an overview of the nested city object hierarchies.")
    private boolean generateObjectHierarchy;

    @CommandLine.Option(names = "--no-json-report", negatable = true, defaultValue = "true",
            description = "Save per-file statistics as individual JSON reports (default: ${DEFAULT-VALUE}).")
    private boolean jsonReport;

    @CommandLine.Option(names = {"-r", "--summary-report"}, paramLabel = "<file>",
            description = "Write aggregated statistics across all input files as a JSON report.")
    private Path summaryFile;

    @CommandLine.Option(names = {"-f", "--fail-on-missing-schema"},
            description = "Fail if input elements lack an associated XML schema.")
    private boolean failOnMissingSchema;

    @CommandLine.Option(names = {"-s", "--schema"}, split = ",", paramLabel = "<URI>",
            description = "One or more XML schema files or URLs to include. Official CityGML schemas cannot " +
                    "be overridden.")
    private Set<String> schemas;

    private final String suffix = "__statistics";

    @Override
    public Integer call() throws ExecutionException {
        List<InputFile> inputFiles = getInputFiles(inputOptions, suffix);
        if (inputFiles.isEmpty()) {
            return CommandLine.ExitCode.OK;
        }

        SchemaHandler schemaHandler;
        try {
            schemaHandler = CityGMLSchemaHandler.newInstance();
            loadSchemas(schemas, schemaHandler);
        } catch (Exception e) {
            throw new ExecutionException("Failed to create schema handler.", e);
        }

        Statistics summary = null;
        ObjectMapper objectMapper = new ObjectMapper();
        ChunkOptions chunkOptions = ChunkOptions.defaults();
        SchemaHelper schemaHelper = SchemaHelper.of(schemaHandler)
                .failOnMissingSchema(failOnMissingSchema);

        for (int i = 0; i < inputFiles.size(); i++) {
            InputFile inputFile = inputFiles.get(i);

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile + ".");

            Statistics statistics = Statistics.of(inputFile);
            StatisticsProcessor processor = StatisticsProcessor.of(statistics, getCityGMLContext())
                    .computeEnvelope(computeEnvelope)
                    .onlyTopLevelFeatures(onlyTopLevelFeatures)
                    .generateObjectHierarchy(generateObjectHierarchy);

            if (idOptions != null) {
                statistics.withCityObjectIds(idOptions.getIds());

                log.debug("Reading global appearances from input file.");
                processor.withGlobalAppearances(GlobalObjectsReader.onlyAppearances()
                        .read(inputFile, getCityGMLContext())
                        .getAppearances());
            }

            log.debug("Reading city objects and generating statistics.");

            try (XMLReader reader = XMLReaderFactory.newInstance(getCityGMLContext().getXMLObjects())
                    .withSchemaHandler(schemaHandler)
                    .createReader(inputFile.getFile(), inputOptions.getEncoding())) {
                Deque<QName> elements = new ArrayDeque<>();
                Deque<Integer> features = new ArrayDeque<>();
                boolean isTopLevel = false;

                while (reader.hasNext()) {
                    EventType event = reader.nextTag();
                    QName lastElement = elements.peek();
                    int depth = reader.getDepth();
                    int lastFeature = Objects.requireNonNullElseGet(features.peek(),
                            () -> idOptions == null ? 0 : Integer.MAX_VALUE);

                    if (event == EventType.START_ELEMENT) {
                        QName element = reader.getName();
                        if (chunkOptions.containsProperty(element)) {
                            isTopLevel = true;
                        } else {
                            if (schemaHelper.isAppearance(element) && depth > lastFeature) {
                                processor.process(element, reader.getObject(Appearance.class), isTopLevel);
                            } else if (schemaHelper.isFeature(element)
                                    && (idOptions == null || depth > lastFeature || hasMatchingIdentifier(reader))) {
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
                throw new ExecutionException("Failed to read file " + inputFile + ".", e);
            }

            if (schemaHelper.hasMissingSchemas()) {
                schemaHelper.getAndResetMissingSchemas().forEach(statistics::addMissingSchema);
            }

            if (jsonReport) {
                Path outputFile = inputFile.getFile().resolveSibling(appendFileNameSuffix(inputFile.getFile()
                        .resolveSibling(replaceFileExtension(inputFile.getFile(), "json")), suffix));

                log.info("Writing statistics as JSON report to file " + outputFile + ".");
                writeStatistics(outputFile, statistics, objectMapper);
            }

            if (summary == null) {
                summary = statistics;
            } else {
                summary.merge(statistics);
            }
        }

        int exitCode = CommandLine.ExitCode.OK;
        if (summary != null) {
            if (summaryFile != null) {
                log.info("Writing overall statistics over all input file(s) as JSON report to file " + summaryFile + ".");
                writeStatistics(summaryFile, summary, objectMapper);
            }

            if (summary.hasMissingSchemas()) {
                log.warn("The statistics might be incomplete due to missing XML schemas in the input file(s).");
                exitCode = 3;
            }

            summary.print(objectMapper, (msg) -> log.print(LogLevel.INFO, msg));
        }

        return exitCode;
    }

    private boolean hasMatchingIdentifier(XMLReader reader) {
        try {
            Attributes attributes = reader.getAttributes();
            String id = attributes.getValue(GMLConstants.GML_3_2_NAMESPACE, "id").get();
            if (id == null) {
                id = attributes.getValue(GMLConstants.GML_3_1_NAMESPACE, "id").get();
            }

            return id != null && idOptions.getIds().contains(id);
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
