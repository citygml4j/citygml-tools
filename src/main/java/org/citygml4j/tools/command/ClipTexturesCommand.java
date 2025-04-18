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

import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.tools.io.InputFiles;
import org.citygml4j.tools.io.OutputFile;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.CityGMLOutputVersion;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.option.OverwriteInputOptions;
import org.citygml4j.tools.util.ResourceProcessor;
import org.citygml4j.tools.util.TextureClipper;
import org.citygml4j.xml.reader.ChunkOptions;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import picocli.CommandLine;

import java.util.List;

@CommandLine.Command(name = "clip-textures",
        description = "Clips texture images to the extent of the target surface.")
public class ClipTexturesCommand extends CityGMLTool {
    @CommandLine.Option(names = {"-j", "--force-jpeg"},
            description = "Force JPEG as format for the texture images.")
    private boolean forceJpeg;

    @CommandLine.Option(names = {"-q", "--jpeg-compression-quality"}, paramLabel = "<0..1>", defaultValue = "1.0",
            description = "Compression quality to use for JPEG images (default: ${DEFAULT-VALUE}).")
    private float jpegCompressionQuality;

    @CommandLine.Option(names = {"-c", "--clamp-texture-coordinates"},
            description = "Clamp texture coordinates to lie within [0, 1].")
    private boolean clampTextureCoordinates;

    @CommandLine.Option(names = "--texture-vertex-precision", paramLabel = "<digits>", defaultValue = "7",
            description = "Number of decimal places to keep for texture vertices (default: ${DEFAULT-VALUE}).")
    private int textureVertexPrecision;

    @CommandLine.Option(names = {"-f", "--texture-folder"}, paramLabel = "<name>", defaultValue = "clipped_textures",
            description = "Name of the relative folder where to save the texture files (default: ${DEFAULT-VALUE}).")
    private String textureFolder;

    @CommandLine.Option(names = "--texture-prefix", paramLabel = "<prefix>", defaultValue = "tex",
            description = "Prefix to use for texture file names (default: ${DEFAULT-VALUE}).")
    private String texturePrefix;

    @CommandLine.Option(names = "--texture-buckets", paramLabel = "<number>",
            description = "Number of subfolders (\"buckets\") to create under the texture folder" +
                    " (default: ${DEFAULT-VALUE}).")
    private int textureBuckets = 10;

    @CommandLine.Mixin
    private CityGMLOutputVersion version;

    @CommandLine.Mixin
    private CityGMLOutputOptions outputOptions;

    @CommandLine.Mixin
    OverwriteInputOptions overwriteOptions;

    @CommandLine.Mixin
    private InputOptions inputOptions;

    private final String suffix = "__clipped_textures";

    @Override
    public Integer call() throws ExecutionException {
        log.debug("Searching for CityGML input files.");
        List<InputFile> inputFiles = InputFiles.of(inputOptions.getFile())
                .withFilter(path -> !stripFileExtension(path).endsWith(suffix))
                .find();

        if (inputFiles.isEmpty()) {
            log.warn("No files found at " + inputOptions.getFile() + ".");
            return CommandLine.ExitCode.OK;
        } else if (inputFiles.size() > 1) {
            log.info("Found " + inputFiles.size() + " file(s) at " + inputOptions.getFile() + ".");
        }

        CityGMLInputFactory in = createCityGMLInputFactory().withChunking(ChunkOptions.defaults());
        CityGMLOutputFactory out = createCityGMLOutputFactory(version.getVersion());

        for (int i = 0; i < inputFiles.size(); i++) {
            InputFile inputFile = inputFiles.get(i);
            OutputFile outputFile = getOutputFile(inputFile, suffix, outputOptions, overwriteOptions);

            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file " + inputFile + ".");

            try (CityGMLReader reader = createCityGMLReader(in, inputFile, inputOptions);
                 ResourceProcessor resourceProcessor = ResourceProcessor.of(inputFile, outputFile)
                         .skip(ResourceProcessor.Type.PARAMETERIZED_TEXTURE)
                         .skip(ResourceProcessor.Type.GEOREFERENCED_TEXTURE)) {
                if (!version.isSetVersion()) {
                    setCityGMLVersion(reader, out);
                }

                if (outputFile.isTemporary()) {
                    log.debug("Writing temporary output file " + outputFile + ".");
                } else {
                    log.info("Writing output to file " + outputFile + ".");
                }

                TextureClipper textureClipper = TextureClipper.of(inputFile, outputFile, out.getVersion())
                        .forceJpeg(forceJpeg)
                        .withJpegCompressionQuality(jpegCompressionQuality)
                        .clampTextureCoordinates(clampTextureCoordinates)
                        .withTextureVertexPrecision(textureVertexPrecision)
                        .withTextureFolder(textureFolder)
                        .withTexturePrefix(texturePrefix)
                        .withTextureBuckets(textureBuckets);

                try (CityGMLChunkWriter writer = createCityGMLChunkWriter(out, outputFile, outputOptions)
                        .withCityModelInfo(getFeatureInfo(reader))) {
                    log.debug("Reading city objects and clipping texture images.");
                    while (reader.hasNext()) {
                        AbstractFeature feature = reader.next();
                        textureClipper.clipTextures(feature);
                        resourceProcessor.process(feature);
                        writer.writeMember(feature);
                    }
                }
            } catch (CityGMLReadException e) {
                throw new ExecutionException("Failed to read file " + inputFile + ".", e);
            } catch (CityGMLWriteException e) {
                throw new ExecutionException("Failed to write file " + outputFile + ".", e);
            }

            if (outputFile.isTemporary()) {
                log.debug("Replacing input file with temporary output file.");
                replaceInputFile(inputFile, outputFile);
            }
        }

        return CommandLine.ExitCode.OK;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (jpegCompressionQuality < 0 || jpegCompressionQuality > 1) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: The JPEG compression quality must be between 0 and 1 " +
                            "but was '" + jpegCompressionQuality + "'");
        }

        if (textureFolder.isEmpty()) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: The texture folder must not be empty");
        }

        if (texturePrefix.isEmpty()) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: The texture prefix must not be empty");
        }

        if (textureBuckets < 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: The number of texture buckets must be a non-negative integer " +
                            "but was '" + textureBuckets + "'");
        }
    }
}
