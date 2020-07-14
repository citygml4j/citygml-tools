/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2020 Claus Nagel <claus.nagel@gmail.com>
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

import org.citygml4j.tools.CityGMLTools;
import org.citygml4j.tools.common.log.LogLevel;
import picocli.CommandLine;

import java.nio.file.Path;

public class LoggingOptions {
    private LogLevel logLevel = LogLevel.INFO;
    private Path logFile;

    private @CommandLine.Spec(CommandLine.Spec.Target.MIXEE)
    CommandLine.Model.CommandSpec mixee;

    private static LoggingOptions getRootLoggingOptions(CommandLine.Model.CommandSpec commandSpec) {
        return ((CityGMLTools) commandSpec.root().userObject()).getLoggingOptions();
    }

    public LogLevel getLogLevel() {
        return getRootLoggingOptions(mixee).logLevel;
    }

    @CommandLine.Option(names = "--log-level", paramLabel = "<level>", defaultValue = "info", description = "Log level: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    public void setLogLevel(LogLevel logLevel) {
        getRootLoggingOptions(mixee).logLevel = logLevel;
    }

    public Path getLogFile() {
        return logFile;
    }

    @CommandLine.Option(names = "--log-file", paramLabel = "<file>", description = "Write log messages to the specified file.")
    public void setLogFile(Path logFile) {
        getRootLoggingOptions(mixee).logFile = logFile;
    }
}
