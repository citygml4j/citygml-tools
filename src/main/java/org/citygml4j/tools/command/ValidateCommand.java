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

package org.citygml4j.tools.command;

import org.citygml4j.tools.CityGMLTools;
import org.citygml4j.tools.command.validate.XMLSchemaCommand;
import org.citygml4j.tools.option.LoggingOptions;
import picocli.CommandLine;

@CommandLine.Command(name = "validate",
        description = "Validates CityGML files according to the given subcommand.",
        versionProvider = CityGMLTools.class,
        mixinStandardHelpOptions = true,
        synopsisSubcommandLabel = "COMMAND",
        showAtFileInUsageHelp = true,
        subcommands = {
                CommandLine.HelpCommand.class,
                XMLSchemaCommand.class
        })
public class ValidateCommand implements CityGMLTool {
    @CommandLine.Mixin
    private LoggingOptions logging;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    @Override
    public Integer call() throws Exception {
        return 0;
    }

    @Override
    public void validate() throws CommandLine.ParameterException {
        if (spec.commandLine().getParseResult().subcommands().isEmpty())
            throw new CommandLine.ParameterException(spec.commandLine(), "Missing required subcommand.");
    }
}
