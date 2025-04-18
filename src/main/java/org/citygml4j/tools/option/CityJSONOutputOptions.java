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

package org.citygml4j.tools.option;

import org.citygml4j.cityjson.model.CityJSONVersion;
import org.citygml4j.cityjson.writer.OutputEncoding;
import picocli.CommandLine;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class CityJSONOutputOptions implements Option {
    @CommandLine.Option(names = {"-v", "--cityjson-version"}, paramLabel = "<version>", defaultValue = "2.0",
            description = "CityJSON version to use for output file(s): 2.0, 1.1, 1.0 (default: ${DEFAULT-VALUE}).")
    private String versionString;

    @CommandLine.Option(names = {"-l", "--json-lines"},
            description = "Write output as CityJSON Sequence in JSON Lines format. " +
                    "This option requires CityJSON 1.1 or later.")
    private boolean jsonLines;

    @CommandLine.Option(names = {"-o", "--output"}, paramLabel = "<dir>",
            description = "Store output file(s) in this directory.")
    private Path outputDirectory;

    @CommandLine.Option(names = "--output-encoding", defaultValue = "UTF-8",
            description = "Encoding to use for output file(s): UTF-8, UTF-16, UTF-32 (default: ${DEFAULT-VALUE}).")
    private String encoding;

    @CommandLine.Option(names = "--pretty-print",
            description = "Format and indent output file(s).")
    private boolean prettyPrint;

    @CommandLine.Option(names = "--html-safe",
            description = "Write JSON that is safe to embed into HTML.")
    private boolean htmlSafe;

    private CityJSONVersion version;
    private OutputEncoding outputEncoding;

    public CityJSONVersion getVersion() {
        return version;
    }

    public boolean isJsonLines() {
        return jsonLines;
    }

    public Path getOutputDirectory() {
        return outputDirectory;
    }

    public OutputEncoding getEncoding() {
        return outputEncoding;
    }

    public boolean isPrettyPrint() {
        return prettyPrint;
    }

    public boolean isHtmlSafe() {
        return htmlSafe;
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        version = CityJSONVersion.fromValue(versionString);
        if (version == null) {
            throw new CommandLine.ParameterException(commandLine,
                    "Invalid value for option '--cityjson-version': expected one of [2.0, 1.1, 1.0] " +
                            "but was '" + versionString + "'");
        }

        if (jsonLines) {
            if (!version.isGreaterThanOrEqual(CityJSONVersion.v1_1)) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: --write-cityjson-features can only be used with CityJSON > 1.0");
            } else if (prettyPrint) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: --write-cityjson-features and --pretty-print are mutually exclusive (specify only one)");
            }
        }

        if (outputDirectory != null) {
            outputDirectory = outputDirectory.toAbsolutePath().normalize();
            if (Files.isRegularFile(outputDirectory)) {
                throw new CommandLine.ParameterException(commandLine,
                        "Error: The --output '" + outputDirectory + "' exists but is not a directory");
            }
        }

        outputEncoding = switch (encoding.toUpperCase(Locale.ROOT)) {
            case "UTF-8" -> OutputEncoding.UTF8;
            case "UTF-16" -> OutputEncoding.UTF16_BE;
            case "UTF-32" -> OutputEncoding.UTF32_BE;
            default -> throw new CommandLine.ParameterException(commandLine,
                    "Invalid value for option '--output-encoding': expected one of [UTF-8, UTF-16, UTF-32] " +
                            "(case-insensitive) but was '" + encoding + "'");
        };
    }
}
