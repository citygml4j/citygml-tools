/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.option;

import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;

public class CityGMLOutputOptions implements Option {
    @CommandLine.Option(names = {"-o", "--output"}, paramLabel = "<dir>",
            description = "Store output files in this directory.")
    private Path outputDirectory;

    @CommandLine.Option(names = "--output-encoding", defaultValue = "UTF-8",
            description = "Encoding to use for output files (default: ${DEFAULT-VALUE}).")
    private String encoding;

    @CommandLine.Option(names = "--no-pretty-print", negatable = true, defaultValue = "true",
            description = "Format and indent output files (default: ${DEFAULT-VALUE}).")
    private boolean prettyPrint;

    public Path getOutputDirectory() {
        return outputDirectory;
    }

    public String getEncoding() {
        return encoding;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (outputDirectory != null) {
            outputDirectory = outputDirectory.toAbsolutePath().normalize();
            if (Files.isRegularFile(outputDirectory)) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: The --output '" + outputDirectory + "' exists but is not a directory");
            }
        }
    }
}
