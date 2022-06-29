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

import org.citygml4j.cityjson.adapter.appearance.serializer.AppearanceSerializer;
import org.citygml4j.cityjson.adapter.geometry.serializer.GeometrySerializer;
import org.citygml4j.cityjson.model.metadata.Metadata;
import org.citygml4j.cityjson.model.metadata.ReferenceSystem;
import org.citygml4j.cityjson.writer.AbstractCityJSONWriter;
import org.citygml4j.cityjson.writer.CityJSONOutputFactory;
import org.citygml4j.cityjson.writer.CityJSONWriteException;
import org.citygml4j.tools.cli.CityGMLInputOptions;
import org.citygml4j.tools.cli.CityGMLTool;
import org.citygml4j.tools.cli.CityJSONOutputOptions;
import org.citygml4j.tools.cli.ExecutionException;
import org.citygml4j.tools.log.Logger;
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
    @CommandLine.Option(names = "--epsg", paramLabel = "<code>",
            description = "EPSG code to use as CRS in the metadata.")
    private int epsg;

    @CommandLine.Option(names = "--compute-extent",
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
                    "transformation is always applied for CityJSON 1.1.")
    private boolean transformCoordinates;

    @CommandLine.Option(names = {"-r", "--replace-templates"},
            description = "Replace template geometries with their absolute coordinates.")
    private boolean replaceTemplates;

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
    private CityGMLInputOptions inputOptions;

    private final Logger log = Logger.getInstance();

    @Override
    public Integer call() throws ExecutionException {
        log.debug("Searching for CityGML input files.");
        List<Path> inputFiles = InputFiles.of(inputOptions.getFiles()).find();

        if (inputFiles.isEmpty()) {
            log.warn("No files found at " + inputOptions.joinFiles() + ".");
            return 0;
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
                .transformTemplateGeometries(replaceTemplates)
                .useMaterialDefaults(useMaterialDefaults)
                .withFallbackTheme(fallbackTheme)
                .writeGenericAttributeTypes(addGenericAttributeTypes);

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            Path outputFile = inputFile.resolveSibling(replaceFileExtension(inputFile, "json"));

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile.toAbsolutePath() + ".");

            log.debug("Reading global appearances, groups and implicit geometries from input file.");
            GlobalObjects globalObjects = GlobalObjectsReader.defaults()
                    .read(inputFile, getCityGMLContext());

            try (CityGMLReader reader = createFilteredCityGMLReader(in, inputFile, inputOptions, "CityObjectGroup")) {
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
                    globalObjects.getTemplateGeometries().forEach(writer::withGlobalTemplateGeometry);

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

        return 0;
    }

    private void populateMetadata(Metadata metadata, FeatureInfo info) {
        Envelope envelope = null;
        String srsName = null;

        if (info != null
                && info.getBoundedBy() != null
                && info.getBoundedBy().getEnvelope() != null) {
            envelope = info.getBoundedBy().getEnvelope();
            srsName = envelope.getSrsName();
        }

        metadata.setGeographicalExtent(envelope);
        metadata.setReferenceSystem(epsg > 0 ?
                new ReferenceSystem(epsg) :
                ReferenceSystem.parse(srsName));
    }
}
