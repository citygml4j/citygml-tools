/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2019 Claus Nagel <claus.nagel@gmail.com>
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

import org.citygml4j.builder.jaxb.CityGMLBuilder;
import org.citygml4j.model.module.citygml.CityGMLVersion;
import org.citygml4j.tools.CityGMLTools;
import org.citygml4j.tools.command.options.CityGMLOutputOptions;
import org.citygml4j.tools.command.options.InputOptions;
import org.citygml4j.tools.command.options.LoggingOptions;
import org.citygml4j.tools.common.log.Logger;
import org.citygml4j.tools.textureclipper.TextureClipper;
import org.citygml4j.tools.textureclipper.TextureClippingException;
import org.citygml4j.tools.util.Constants;
import org.citygml4j.tools.util.ObjectRegistry;
import org.citygml4j.tools.util.Util;
import picocli.CommandLine;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@CommandLine.Command(name = "clip-textures",
        description = "Clips texture images to the extent of the target surface.",
        versionProvider = CityGMLTools.class,
        mixinStandardHelpOptions = true,
        showAtFileInUsageHelp = true)
public class ClipTexturesCommand implements CityGMLTool {
    @CommandLine.Option(names = {"-o", "--output"}, required = true, paramLabel = "<dir>", description = "Output directory in which to write the result files.")
    private String output;

    @CommandLine.Option(names = "--clean-output", description = "Clean output directory before processing input files.")
    private boolean cleanOutput;

    @CommandLine.Option(names = "--jpeg-compression", paramLabel = "<float>", description = "Compression quality for JPEG files: value between 0.0 and 1.0 (default: ${DEFAULT-VALUE}).")
    private float jpegCompression = 1.0f;

    @CommandLine.Option(names = "--force-jpeg", description = "Force JPEG as output format for clipped texture files.")
    private boolean forceJPEG;

    @CommandLine.Option(names = "--adapt-texture-coords", description = "Adapt texture coordinates to lie within [0, 1].")
    private boolean adaptTexCoords;

    @CommandLine.Option(names = "--texture-coords-digits", paramLabel = "<digits>", description = "Number of digits to keep for texture coordinates (default: ${DEFAULT-VALUE}).")
    private int texCoordsDigits = 7;

    @CommandLine.Option(names = "--appearance-dir", paramLabel = "<path>", description = "Relative path to be used as appearance directory (default: ${DEFAULT-VALUE}).")
    private String appearanceDir = "appearance";

    @CommandLine.Option(names = "--appearance-subdirs", paramLabel = "<int>", description = "Number of appearance subdirs to create (default: ${DEFAULT-VALUE}).")
    private int noOfBuckets = 10;

    @CommandLine.Option(names = "--texture-prefix", paramLabel = "<prefix>", description = "Prefix to be used for texture file names (default: ${DEFAULT-VALUE}).")
    private String texturePrefix = "tex";

    @CommandLine.Mixin
    private CityGMLOutputOptions cityGMLOutput;

    @CommandLine.Mixin
    private InputOptions input;

    @CommandLine.Mixin
    LoggingOptions logging;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        Logger log = Logger.getInstance();
        CityGMLBuilder cityGMLBuilder = ObjectRegistry.getInstance().get(CityGMLBuilder.class);

        CityGMLVersion targetVersion = cityGMLOutput.getVersion();

        log.debug("Searching for CityGML input files.");
        List<Path> inputFiles;
        try {
            inputFiles = new ArrayList<>(Util.listFiles(input.getFile(), "**.{gml,xml}"));
            log.info("Found " + inputFiles.size() + " file(s) at '" + input.getFile() + "'.");
        } catch (IOException e) {
            log.warn("Failed to find file(s) at '" + input.getFile() + "'.");
            return 0;
        }

        // check output directory
        Path outputDir = Constants.WORKING_DIR.resolve(Paths.get(output));
        if (Files.isRegularFile(outputDir)) {
            log.error("The output '" + output + "' is a file but must be a directory.");
            return 1;
        }

        // check that output and input directories are different
        Path rootDir = Util.getRootDirectory(input.getFile());
        if (outputDir.startsWith(rootDir)) {
            log.error("The output directory must not be a subfolder of or equal to the input directory.");
            return 1;
        }

        // clean output folder
        if (cleanOutput && Files.exists(outputDir)) {
            log.debug("Cleaning output directory '" + output + "'.");
            try (Stream<Path> paths = Files.walk(outputDir).sorted(Comparator.reverseOrder())) {
                paths.map(Path::toFile).forEach(File::delete);
            }
        }

        TextureClipper clipper = TextureClipper.defaults(cityGMLBuilder)
                .withJPEGCompression(jpegCompression)
                .forceJPEG(forceJPEG)
                .adaptTextureCoordinates(adaptTexCoords)
                .withSignificantDigits(texCoordsDigits)
                .withAppearanceDirectory(appearanceDir)
                .withNumberOfBuckets(noOfBuckets)
                .withTextureFileNamePrefix(texturePrefix)
                .withTargetVersion(targetVersion)
                .withInputEncoding(input.getEncoding())
                .withOutputEncoding(cityGMLOutput.getEncoding());

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Processing file '" + inputFile.toAbsolutePath() + "'.");

            Path outputFile = outputDir.resolve(rootDir.relativize(inputFile));
            log.info("Writing output to file '" + outputFile.toAbsolutePath() + "'.");

            try {
                clipper.clipTextures(inputFile, outputFile);
            } catch (TextureClippingException e) {
                log.error("Failed to clip textures.", e);
            }
        }

        return 0;
    }

    @Override
    public void validate() throws CommandLine.ParameterException {
        try {
            Paths.get(output);
        } catch (InvalidPathException e) {
            throw new CommandLine.ParameterException(spec.commandLine(), "The output directory '" + output + "' is not a valid path.", e);
        }

        try {
            Path path = Paths.get(appearanceDir);
            if (path.isAbsolute())
                throw new CommandLine.ParameterException(spec.commandLine(), "The appearance directory must be given by a local path.");

        } catch (InvalidPathException e) {
            throw new CommandLine.ParameterException(spec.commandLine(), "The appearance directory '" + appearanceDir + "' is not a valid path.", e);
        }

        if (jpegCompression < 0 || jpegCompression > 1)
            throw new CommandLine.ParameterException(spec.commandLine(), "The JPEG compression must be a value between 0.0 and 1.0.");
    }
}
