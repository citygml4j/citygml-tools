/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2024 Claus Nagel <claus.nagel@gmail.com>
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

import org.citygml4j.cityjson.model.metadata.Metadata;
import org.citygml4j.cityjson.reader.CityJSONInputFactory;
import org.citygml4j.cityjson.reader.CityJSONReadException;
import org.citygml4j.cityjson.reader.CityJSONReader;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.CityModel;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.util.InputFiles;
import org.citygml4j.tools.util.LodMapper;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import org.xmlobjects.gml.model.deprecated.StringOrRef;
import org.xmlobjects.gml.model.feature.BoundingShape;
import org.xmlobjects.gml.model.geometry.DirectPosition;
import org.xmlobjects.gml.model.geometry.Envelope;
import org.xmlobjects.gml.util.EnvelopeOptions;
import org.xmlobjects.util.xml.XMLPatterns;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@CommandLine.Command(name = "from-cityjson",
        description = "Converts CityJSON files into CityGML.")
public class FromCityJSONCommand extends CityGMLTool {
    @CommandLine.Option(names = "--no-map-unknown-types", negatable = true, defaultValue = "true",
            description = "Map unknown object types onto generic city objects (default: ${DEFAULT-VALUE}).")
    private boolean mapUnknownTypes;

    @CommandLine.Option(names = {"-r", "--replace-templates"},
            description = "Replace geometry templates with real coordinates.")
    private boolean replaceTemplates;

    @CommandLine.Option(names = {"-a", "--assign-appearances-to-implicit-geometries"},
            description = "Assign appearances to implicit geometries instead of using global appearances. " +
                    "This option can only be used with CityGML 3.0.")
    private boolean assignAppearancesToImplicitGeometries;

    @CommandLine.Option(names = {"-m", "--lod-mode"}, defaultValue = "maximum",
            description = "Default mode for selecting a CityJSON LoD formatted as X.Y if a city object " +
                    "has multiple CityJSON LoDs for the same CityGML LoD: ${COMPLETION-CANDIDATES} " +
                    "(default: ${DEFAULT-VALUE}).")
    private LodMapper.Mode mode;

    @CommandLine.Option(names = {"-l", "--lod-mapping"}, split = ",", paramLabel = "<lod=x.y>",
            description = "CityJSON LoD formatted as X.Y to use for the corresponding CityGML LoD " +
                    "(e.g. '2=2.3'). The default selection mode is used as fallback if a city object " +
                    "lacks the defined CityJSON LoD.")
    private Map<Integer, Double> lodMappings;

    @CommandLine.Option(names = {"-v", "--citygml-version"}, defaultValue = "3.0",
            description = "CityGML version to use for output file(s): 3.0, 2.0, 1.0 (default: ${DEFAULT-VALUE}).")
    private String version;

    @CommandLine.Option(names = {"--srs-name"},
            description = "Name of the CRS to use in the output file.")
    private String srsName;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Mixin
    InputOptions inputOptions;

    private CityGMLVersion versionOption;

    @Override
    public Integer call() throws ExecutionException {
        log.debug("Searching for CityJSON input files.");
        List<Path> inputFiles = InputFiles.of(inputOptions.getFiles())
                .withDefaultGlob("**.{json,cityjson}")
                .find();

        if (inputFiles.isEmpty()) {
            log.warn("No files found at " + inputOptions.joinFiles() + ".");
            return CommandLine.ExitCode.OK;
        }

        log.info("Found " + inputFiles.size() + " file(s) at " + inputOptions.joinFiles() + ".");
        log.debug("Using CityGML " + versionOption + " for the output file(s).");

        LodMapper lodMapper = LodMapper.newInstance()
                .withMode(mode)
                .withMapping(lodMappings);

        CityJSONInputFactory in = createCityJSONInputFactory()
                .chunkByTopLevelCityObjects(true)
                .mapUnsupportedTypesToGenerics(mapUnknownTypes)
                .transformTemplateGeometries(replaceTemplates)
                .assignAppearancesToImplicitGeometries(assignAppearancesToImplicitGeometries)
                .withLodMapper(lodMapper)
                .withTargetCityGMLVersion(versionOption);
        CityGMLOutputFactory out = createCityGMLOutputFactory(versionOption);

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            Path outputFile = inputFile.resolveSibling(replaceFileExtension(inputFile, "gml"));

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile.toAbsolutePath() + ".");

            try (CityJSONReader reader = createCityJSONReader(in, inputFile, inputOptions)) {
                String srsName = null;
                CityModel cityModel = new CityModel();
                if (reader.hasNext()) {
                    Metadata metadata = reader.getMetadata();
                    srsName = getSrsName(metadata);
                    setMetadata(cityModel, metadata, srsName);
                }

                log.info("Writing output to file " + outputFile.toAbsolutePath() + ".");

                try (CityGMLChunkWriter writer = createCityGMLChunkWriter(out, outputFile, outputOptions)
                        .withCityModelInfo(cityModel)) {
                    log.debug("Reading city objects and converting them into CityGML " + versionOption + ".");
                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        if (srsName != null && cityModel.getBoundedBy() == null) {
                            setSrsName(feature, srsName);
                        }

                        writer.writeMember(feature);
                    }
                }
            } catch (CityJSONReadException e) {
                throw new ExecutionException("Failed to read file " + inputFile.toAbsolutePath() + ".", e);
            } catch (CityGMLWriteException e) {
                throw new ExecutionException("Failed to write file " + outputFile.toAbsolutePath() + ".", e);
            }
        }

        return CommandLine.ExitCode.OK;
    }

    private void setMetadata(CityModel cityModel, Metadata metadata, String srsName) {
        if (metadata != null) {
            if (metadata.getIdentifier() != null) {
                cityModel.setId(XMLPatterns.NCNAME.matcher(metadata.getIdentifier()).matches() ?
                        metadata.getIdentifier() :
                        "ID_" + metadata.getIdentifier());
            }

            if (metadata.getTitle() != null) {
                cityModel.setDescription(new StringOrRef(metadata.getTitle()));
            }

            if (metadata.isSetGeographicalExtent()) {
                Envelope envelope = new Envelope(
                        new DirectPosition(metadata.getGeographicalExtent().subList(0, 3)),
                        new DirectPosition(metadata.getGeographicalExtent().subList(3, 6)));
                envelope.setSrsDimension(3);
                envelope.setSrsName(srsName);
                cityModel.setBoundedBy(new BoundingShape(envelope));
            }
        }
    }

    private String getSrsName(Metadata metadata) {
        if (srsName != null) {
            return srsName;
        } else if (metadata != null && metadata.getReferenceSystem() != null) {
            return metadata.getReferenceSystem().toURL();
        } else {
            return null;
        }
    }

    private void setSrsName(AbstractFeature feature, String srsName) {
        if (feature.getBoundedBy() != null
                && feature.getBoundedBy().isSetEnvelope()) {
            feature.getBoundedBy().getEnvelope().setSrsName(srsName);
        } else {
            Envelope envelope = feature.computeEnvelope(EnvelopeOptions.defaults()
                    .reuseExistingEnvelopes(true));
            if (envelope.isValid()) {
                envelope.setSrsName(srsName);
                feature.setBoundedBy(new BoundingShape(envelope));
            }
        }
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        switch (version) {
            case "1.0":
                versionOption = CityGMLVersion.v1_0;
                break;
            case "2.0":
                versionOption = CityGMLVersion.v2_0;
                break;
            case "3.0":
                versionOption = CityGMLVersion.v3_0;
                break;
            default:
                throw new CommandLine.ParameterException(commandLine,
                        "Invalid value for option '--citygml-version': expected one of [3.0, 2.0, 1.0] " +
                                "but was '" + version + "'");
        }

        if (assignAppearancesToImplicitGeometries && versionOption != CityGMLVersion.v3_0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --assign-appearances-to-implicit-geometries can only be used with CityGML version 3.0");
        }

        if (assignAppearancesToImplicitGeometries && replaceTemplates) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --assign-appearances-to-implicit-geometries and --replace-templates are mutually " +
                            "exclusive (specify only one)");
        }

        if (lodMappings != null) {
            for (Map.Entry<Integer, Double> entry : lodMappings.entrySet()) {
                if (entry.getKey() < 0 || entry.getKey() > 4) {
                    throw new CommandLine.ParameterException(commandLine,
                            "Invalid value for option '--lod-mapping' (<lod=x.y>): LOD must be an " +
                                    "integer between 0 and 4 but was '" + entry.getKey() + "'.");
                }

                if (entry.getValue().intValue() != entry.getKey()) {
                    throw new CommandLine.ParameterException(commandLine,
                            "Invalid value for option '--lod-mapping' (<lod=x.y>): X must match the LOD value '" +
                                    entry.getKey() + "' but was '" + entry.getValue().intValue() + "'.");
                }
            }
        }
    }
}
