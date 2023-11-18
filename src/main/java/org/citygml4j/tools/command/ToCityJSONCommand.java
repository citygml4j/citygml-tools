/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2023 Claus Nagel <claus.nagel@gmail.com>
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

import org.citygml4j.cityjson.adapter.appearance.serializer.AppearanceSerializer;
import org.citygml4j.cityjson.adapter.geometry.serializer.GeometrySerializer;
import org.citygml4j.cityjson.model.metadata.Metadata;
import org.citygml4j.cityjson.model.metadata.ReferenceSystem;
import org.citygml4j.cityjson.writer.AbstractCityJSONWriter;
import org.citygml4j.cityjson.writer.CityJSONOutputFactory;
import org.citygml4j.cityjson.writer.CityJSONWriteException;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.option.CityJSONOutputOptions;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.util.GlobalObjects;
import org.citygml4j.tools.util.GlobalObjectsReader;
import org.citygml4j.tools.util.InputFiles;
import org.citygml4j.xml.reader.*;
import org.xmlobjects.gml.model.geometry.Envelope;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;

@CommandLine.Command(name = "to-cityjson",
        description = "Converts CityGML files into CityJSON.")
public class ToCityJSONCommand extends CityGMLTool {
    @CommandLine.Option(names = {"-e", "--epsg"}, paramLabel = "<code>",
            description = "EPSG code to use as CRS in the metadata.")
    private int epsg;

    @CommandLine.Option(names = {"-c", "--compute-extent"},
            description = "Compute city model extent to use in the metadata.")
    private boolean computeExtent;

    @CommandLine.Option(names = "--vertex-precision", paramLabel = "<digits>",
            description = "Number of decimal places to keep for geometry vertices (default: ${DEFAULT-VALUE}).")
    private int vertexPrecision = GeometrySerializer.DEFAULT_VERTEX_PRECISION;

    @CommandLine.Option(names = "--template-precision", paramLabel = "<digits>",
            description = "Number of decimal places to keep for template vertices (default: ${DEFAULT-VALUE}).")
    private int templatePrecision = GeometrySerializer.DEFAULT_TEMPLATE_PRECISION;

    @CommandLine.Option(names = "--texture-vertex-precision", paramLabel = "<digits>",
            description = "Number of decimal places to keep for texture vertices (default: ${DEFAULT-VALUE}).")
    private int textureVertexPrecision = AppearanceSerializer.DEFAULT_TEXTURE_VERTEX_PRECISION;

    @CommandLine.Option(names = {"-t", "--transform-coordinates"},
            description = "Transform the coordinates of vertices to integers to reduce the file size. The " +
                    "transformation is always applied for CityJSON 1.1 and later.")
    private boolean transformCoordinates;

    @CommandLine.Option(names = {"-r", "--replace-implicit-geometries"},
            description = "Replace implicit geometries with their absolute coordinates.")
    private boolean replaceImplicitGeometries;

    @CommandLine.Option(names = "--no-material-defaults", negatable = true, defaultValue = "true",
            description = "Use CityGML default values for material properties (default: ${DEFAULT-VALUE}).")
    private boolean useMaterialDefaults;

    @CommandLine.Option(names = "--fallback-theme", paramLabel = "<theme>",
            description = "Theme to use for materials and textures if not defined in the input file(s) " +
                    "(default: ${DEFAULT-VALUE}).")
    private String fallbackTheme = AppearanceSerializer.FALLBACK_THEME;

    @CommandLine.Option(names = "--add-generic-attribute-types",
            description = "Add data types of generic attributes as extension property.")
    private boolean addGenericAttributeTypes;

    @CommandLine.Mixin
    private CityJSONOutputOptions outputOptions;

    @CommandLine.Mixin
    private InputOptions inputOptions;

    @Override
    public Integer call() throws ExecutionException {
        log.debug("Searching for CityGML input files.");
        List<Path> inputFiles = InputFiles.of(inputOptions.getFiles()).find();

        if (inputFiles.isEmpty()) {
            log.warn("No files found at " + inputOptions.joinFiles() + ".");
            return CommandLine.ExitCode.OK;
        }

        log.info("Found " + inputFiles.size() + " file(s) at " + inputOptions.joinFiles() + ".");
        log.debug("Using CityJSON " + outputOptions.getVersion() + " for the output file(s).");

        CityGMLInputFactory in = createCityGMLInputFactory().withChunking(ChunkOptions.defaults());
        CityJSONOutputFactory out = createCityJSONOutputFactory(outputOptions.getVersion())
                .computeCityModelExtent(computeExtent)
                .withVertexPrecision(vertexPrecision)
                .withTemplatePrecision(templatePrecision)
                .withTextureVertexPrecision(textureVertexPrecision)
                .applyTransformation(transformCoordinates)
                .transformTemplateGeometries(replaceImplicitGeometries)
                .useMaterialDefaults(useMaterialDefaults)
                .withFallbackTheme(fallbackTheme)
                .writeGenericAttributeTypes(addGenericAttributeTypes);

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            Path outputFile = inputFile.resolveSibling(replaceFileExtension(inputFile,
                    outputOptions.isJsonLines() ? "jsonl" : "json"));

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile.toAbsolutePath() + ".");

            log.debug("Reading global appearances, groups and implicit geometries from input file.");
            GlobalObjects globalObjects = GlobalObjectsReader.defaults()
                    .read(inputFile, getCityGMLContext());

            try (CityGMLReader reader = createSkippingCityGMLReader(in, inputFile, inputOptions,
                    "CityObjectGroup", "Appearance")) {
                Metadata metadata = new Metadata();
                if (reader.hasNext()) {
                    populateMetadata(metadata, reader.getParentInfo());
                }

                log.info("Writing output to file " + outputFile.toAbsolutePath() + ".");

                try (AbstractCityJSONWriter<?> writer = createCityJSONWriter(out, outputFile, outputOptions)
                        .withMetadata(metadata)) {
                    log.debug("Reading city objects and converting them into CityJSON " + outputOptions.getVersion() + ".");
                    globalObjects.getAppearances().forEach(writer::withGlobalAppearance);
                    globalObjects.getCityObjectGroups().forEach(writer::withGlobalCityObjectGroup);
                    globalObjects.getTemplateGeometries().values().forEach(geometry ->
                            writer.withGlobalTemplateGeometry(geometry, geometry.getLocalProperties()
                                    .getOrDefault(GlobalObjects.TEMPLATE_LOD, Integer.class, () -> 0)));

                    while (reader.hasNext()) {
                        writer.writeCityObject(reader.next());
                    }
                }
            } catch (CityGMLReadException e) {
                throw new ExecutionException("Failed to read file " + inputFile.toAbsolutePath() + ".", e);
            } catch (CityJSONWriteException e) {
                throw new ExecutionException("Failed to write file " + outputFile.toAbsolutePath() + ".", e);
            }
        }

        return CommandLine.ExitCode.OK;
    }

    private void populateMetadata(Metadata metadata, FeatureInfo info) {
        if (info != null) {
            if (info.getId() != null) {
                metadata.setIdentifier(info.getId());
            }

            if (info.getDescription() != null) {
                metadata.setTitle(info.getDescription().getValue());
            }

            if (info.getBoundedBy() != null
                    && info.getBoundedBy().getEnvelope() != null) {
                Envelope envelope = info.getBoundedBy().getEnvelope();
                metadata.setGeographicalExtent(envelope);
                metadata.setReferenceSystem(epsg > 0 ?
                        new ReferenceSystem(epsg) :
                        ReferenceSystem.parse(envelope.getSrsName()));
            }
        }
    }
}
