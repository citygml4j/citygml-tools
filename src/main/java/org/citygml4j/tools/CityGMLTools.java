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

package org.citygml4j.tools;

import org.citygml4j.tools.command.MainCommand;
import org.citygml4j.tools.common.log.Logger;
import org.citygml4j.tools.util.Util;
import picocli.CommandLine;

import java.time.Duration;
import java.time.Instant;

public class CityGMLTools implements CommandLine.IExecutionExceptionHandler {
    private static final Logger log = Logger.getInstance();

    public static void main(String[] args) {
        Instant start = Instant.now();

        CommandLine cmd = new CommandLine(new MainCommand())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setExecutionExceptionHandler(new CityGMLTools())
                .setExecutionStrategy(new CommandLine.RunAll());

        int exitCode = cmd.execute(args);

        if (exitCode != 0) {
            if (exitCode != CommandLine.ExitCode.USAGE)
                log.warn("citygml-tools execution failed.");

            System.exit(exitCode);
        }

        log.info("Total execution time: " + Util.formatElapsedTime(
                Duration.between(start, Instant.now()).toMillis()) + ".");

        int warnings = log.getNumberOfWarnings();
        int errors = log.getNumberOfErrors();

        if (errors != 0 || warnings != 0)
            log.info("citygml-tools finished with " + warnings + " warning(s) and " + errors + " error(s).");
        else
            log.info("citygml-tools successfully completed.");
    }

    @Override
    public int handleExecutionException(Exception e, CommandLine commandLine, CommandLine.ParseResult parseResult) throws Exception {
        log.error("The following unexpected error occurred during execution.");
        throw e instanceof CommandLine.ExecutionException ?
                (CommandLine.ExecutionException) e :
                new CommandLine.ExecutionException(commandLine, "Error while executing command (" + commandLine.getCommand() + "):", e);
    }
}
