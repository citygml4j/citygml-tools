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

import org.citygml4j.tools.command.CityGMLTool;
import org.citygml4j.tools.command.MainCommand;
import org.citygml4j.tools.common.log.Logger;
import org.citygml4j.tools.util.Util;
import picocli.CommandLine;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class CityGMLTools implements CommandLine.IParseResultHandler2<List<Object>> {

    public static void main(String[] args) {
        Logger log = Logger.getInstance();
        List<Object> result = null;
        Instant start = Instant.now();

        try {
            CommandLine cmd = new CommandLine(new MainCommand());
            cmd.setCaseInsensitiveEnumValuesAllowed(true);

            result = cmd.parseWithHandlers(
                    new CityGMLTools(),
                    CommandLine.defaultExceptionHandler(),
                    args);
        } catch (CommandLine.ExecutionException e) {
            log.error("The following unexpected error occurred during execution.");
            log.logStackTrace(e);
            log.warn("citygml-tools execution failed.");
            System.exit(1);
        }

        if (result == null)
            System.exit(0);

        log.info("Total execution time: " + Util.formatElapsedTime(
                Duration.between(start, Instant.now()).toMillis()) + ".");

        if (result.stream().anyMatch(r -> r == Boolean.FALSE)) {
            log.warn("citygml-tools execution failed.");
            System.exit(1);
        }

        int warnings = log.getNumberOfWarnings();
        int errors = log.getNumberOfErrors();

        if (errors != 0 || warnings != 0)
            log.info("citygml-tools finished with " + warnings + " warning(s) and " + errors + " error(s).");
        else
            log.info("citygml-tools successfully completed.");
    }

    @Override
    public List<Object> handleParseResult(CommandLine.ParseResult parseResult) throws CommandLine.ExecutionException {
        if (CommandLine.printHelpIfRequested(parseResult))
            return null;

        List<CommandLine> commandLines = parseResult.asCommandLineList();
        if (commandLines.size() == 1) {
            System.err.println("Missing required subcommand.");
            commandLines.get(0).usage(System.err);
            System.exit(1);
        }

        // validate commands before executing them
        for (CommandLine commandLine : commandLines) {
            Object command = commandLine.getCommand();
            if (!(command instanceof CityGMLTool))
                throw new CommandLine.ExecutionException(commandLine,
                        "Parsed command (" + command + ") is not a valid CityGML tool.");

            ((CityGMLTool) command).validate();
        }

        // execute commands
        Logger log = Logger.getInstance();
        List<Object> executionResult = new ArrayList<>();
        for (CommandLine commandLine : commandLines) {
            Object command = commandLine.getCommand();
            try {
                CityGMLTool cityGMLTool = (CityGMLTool) command;
                if (!(cityGMLTool instanceof MainCommand))
                    log.info("Executing command '" + commandLine.getCommandName() + "'.");

                executionResult.add(cityGMLTool.execute());
            } catch (CommandLine.ParameterException | CommandLine.ExecutionException e) {
                throw e;
            } catch (Exception e) {
                throw new CommandLine.ExecutionException(commandLine,
                        "Error while executing command (" + command + "): " + e, e);
            }
        }

        return executionResult;
    }

}
