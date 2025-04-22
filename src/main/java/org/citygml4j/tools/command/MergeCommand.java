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

import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.CityModel;
import org.citygml4j.tools.CityGMLTools;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.tools.io.InputFiles;
import org.citygml4j.tools.io.OutputFile;
import org.citygml4j.tools.option.CityGMLOutputVersion;
import org.citygml4j.tools.util.MergeProcessor;
import org.citygml4j.xml.reader.*;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import org.xmlobjects.gml.model.feature.BoundingShape;
import org.xmlobjects.gml.model.geometry.Envelope;
import org.xmlobjects.gml.util.EnvelopeOptions;
import picocli.CommandLine;

import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

@CommandLine.Command(name = "merge",
        description = "Merge multiple CityGML files into a single file.")
public class MergeCommand extends CityGMLTool {
    @CommandLine.Parameters(paramLabel = "<file>", arity = "1",
            description = "Input files or directories to process (glob patterns supported).")
    private List<String> files;

    @CommandLine.Option(names = "--input-encoding", paramLabel = "<encoding>",
            description = "Encoding of input files.")
    private String inputEncoding;

    @CommandLine.Option(names = {"-o", "--output"}, paramLabel = "<file>", required = true,
            description = "Name of the output file.")
    private Path file;

    @CommandLine.Option(names = "--output-encoding", paramLabel = "<encoding>", defaultValue = "UTF-8",
            description = "Encoding to use for output file (default: ${DEFAULT-VALUE}).")
    private String outputEncoding;

    @CommandLine.Option(names = {"-c", "--compute-extent"},
            description = "Compute the extent over all input files.")
    private boolean computeExtent;

    @CommandLine.Option(names = {"-i", "--id-mode"}, defaultValue = "keep_all",
            description = "Mode for handling gml:ids: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private MergeProcessor.IdMode idMode;

    @CommandLine.Mixin
    private CityGMLOutputVersion version;

    @CommandLine.Option(names = {"-t", "--textures"}, paramLabel = "<dir>", defaultValue = "merged_textures",
            description = "Relative directory to store texture files (default: ${DEFAULT-VALUE}).")
    private String textureDir;

    @CommandLine.Option(names = {"-l", "--library-objects"}, paramLabel = "<dir>",
            defaultValue = "merged_library_objects",
            description = "Relative directory to store library objects (default: ${DEFAULT-VALUE}).")
    private String libraryObjectDir;

    @CommandLine.Option(names = {"-p", "--point-files"}, paramLabel = "<dir>", defaultValue = "merged_point_files",
            description = "Relative directory to store point cloud files (default: ${DEFAULT-VALUE}).")
    private String pointFileDir;

    @CommandLine.Option(names = {"-s", "--timeseries"}, paramLabel = "<dir>", defaultValue = "merged_timeseries",
            description = "Relative directory to store timeseries files (default: ${DEFAULT-VALUE}).")
    private String timeseriesDir;

    @CommandLine.Option(names = {"-b", "--buckets"}, paramLabel = "<number>",
            description = "Number of subdirectories to create within the resource directories " +
                    "(default: ${DEFAULT-VALUE}).")
    private int buckets = 10;

    @CommandLine.Option(names = "--no-pretty-print", negatable = true, defaultValue = "true",
            description = "Format and indent output files (default: ${DEFAULT-VALUE}).")
    private boolean prettyPrint;

    @Override
    public Integer call() throws ExecutionException {
        List<InputFile> inputFiles = getInputFiles(InputFiles.of(files));
        if (inputFiles.isEmpty()) {
            return CommandLine.ExitCode.OK;
        }

        OutputFile outputFile = OutputFile.of(CityGMLTools.WORKING_DIR.resolve(file));
        getOrCreateOutputDirectory(outputFile.getFile().getParent());
        log.info("Writing output to file " + outputFile + ".");

        CityGMLInputFactory in = createCityGMLInputFactory().withChunking(ChunkOptions.defaults());
        Metadata metadata = processMetadata(inputFiles, in);
        CityGMLOutputFactory out = createCityGMLOutputFactory(metadata.version());

        try (CityGMLChunkWriter writer = createCityGMLChunkWriter(out, outputFile, outputEncoding, prettyPrint)
                .withCityModelInfo(metadata.cityModel());
             MergeProcessor processor = MergeProcessor.of(outputFile)
                     .withTextureDir(textureDir)
                     .withLibraryObjectDir(libraryObjectDir)
                     .withLibraryObjectDir(pointFileDir)
                     .withTimeseriesDir(timeseriesDir)
                     .withIdMode(idMode)
                     .withBuckets(buckets)) {
            for (int i = 0; i < inputFiles.size(); i++) {
                InputFile inputFile = inputFiles.get(i);
                log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Merging file " + inputFile + ".");

                try (CityGMLReader reader = createCityGMLReader(in, inputFile, inputEncoding)) {
                    Set<String> topLevelIds = metadata.topLevelIds().getOrDefault(inputFile.toString(), Set.of());
                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        processor.process(feature, inputFile, topLevelIds);
                        writer.writeMember(feature);
                    }
                } catch (CityGMLReadException e) {
                    throw new ExecutionException("Failed to read file " + inputFile + ".", e);
                } finally {
                    processor.reset();
                }
            }
        } catch (CityGMLWriteException e) {
            throw new ExecutionException("Failed to write file " + outputFile + ".", e);
        }

        return CommandLine.ExitCode.OK;
    }

    private record Metadata(CityGMLVersion version, CityModel cityModel, Map<String, Set<String>> topLevelIds) {
    }

    private Metadata processMetadata(List<InputFile> inputFiles, CityGMLInputFactory in) throws ExecutionException {
        CityModel cityModel = new CityModel();
        CityGMLVersion outputVersion = version.getVersion();
        Map<String, Set<String>> topLevelIds = new HashMap<>();

        if (!version.isSetVersion() || computeExtent || idMode == MergeProcessor.IdMode.KEEP_TOPLEVEL) {
            log.debug("Reading metadata from input files.");
            Set<CityGMLVersion> versions = new HashSet<>();
            Envelope extent = new Envelope();
            Set<String> srsNames = new HashSet<>();

            for (InputFile inputFile : inputFiles) {
                try (CityGMLReader reader = createSkippingCityGMLReader(in, inputFile, inputEncoding, "Appearance")) {
                    if (!version.isSetVersion()) {
                        CityGMLVersion version = getCityGMLVersion(reader);
                        if (version != null) {
                            versions.add(version);
                        }
                    }

                    boolean foundExtent = false;
                    if (computeExtent) {
                        FeatureInfo featureInfo = getFeatureInfo(reader);
                        if (featureInfo != null
                                && featureInfo.getBoundedBy() != null
                                && featureInfo.getBoundedBy().isSetEnvelope()) {
                            foundExtent = true;
                            extent.include(featureInfo.getBoundedBy().getEnvelope());
                            if (featureInfo.getBoundedBy().getEnvelope().getSrsName() != null) {
                                srsNames.add(featureInfo.getBoundedBy().getEnvelope().getSrsName());
                            }
                        }
                    }

                    if (idMode == MergeProcessor.IdMode.KEEP_TOPLEVEL || (computeExtent && !foundExtent)) {
                        Set<String> ids = topLevelIds.computeIfAbsent(inputFile.toString(), v -> new HashSet<>());
                        while (reader.hasNext()) {
                            AbstractFeature feature = reader.next();
                            if (computeExtent) {
                                Envelope envelope = feature.computeEnvelope(EnvelopeOptions.defaults());
                                extent.include(envelope);
                                if (envelope.getSrsName() != null) {
                                    srsNames.add(envelope.getSrsName());
                                }
                            }

                            if (idMode == MergeProcessor.IdMode.KEEP_TOPLEVEL && feature.getId() != null) {
                                ids.add(feature.getId());
                            }
                        }
                    }
                } catch (CityGMLReadException e) {
                    throw new ExecutionException("Failed to read file " + inputFile + ".", e);
                }
            }

            if (!version.isSetVersion()) {
                if (versions.size() == 1) {
                    outputVersion = versions.iterator().next();
                    log.debug("Using CityGML " + version + " for the output file.");
                } else {
                    String message = versions.isEmpty() ?
                            "Failed to detect CityGML version from input files." :
                            "The input files use multiple CityGML versions: " + versions.stream()
                                    .map(CityGMLVersion::toString)
                                    .sorted()
                                    .collect(Collectors.joining(", ")) + ".";

                    log.warn(message + " Using CityGML " + outputVersion + " for the output file.");
                }
            }

            if (computeExtent && extent.isValid()) {
                if (srsNames.size() < 2) {
                    extent.setSrsDimension(extent.getDimension());
                    srsNames.stream().findFirst().ifPresent(extent::setSrsName);
                    cityModel.setBoundedBy(new BoundingShape(extent));
                } else {
                    log.error("Failed to compute extent due to multiple CRS names used in the input files: '" +
                            String.join("', '", srsNames) + "'.");
                }
            }
        }

        return new Metadata(outputVersion, cityModel, topLevelIds);
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (buckets < 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: The number of buckets must be a non-negative integer but was '" + buckets + "'");
        }
    }
}
