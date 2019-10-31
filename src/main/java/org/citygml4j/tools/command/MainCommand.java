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

import org.citygml4j.CityGMLContext;
import org.citygml4j.builder.jaxb.CityGMLBuilder;
import org.citygml4j.model.citygml.ade.binding.ADEContext;
import org.citygml4j.tools.common.log.LogLevel;
import org.citygml4j.tools.common.log.Logger;
import org.citygml4j.tools.util.Constants;
import org.citygml4j.tools.util.URLClassLoader;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.ServiceLoader;
import java.util.stream.Stream;

@CommandLine.Command(name = "citygml-tools",
        description = "Collection of tools for processing CityGML files.",
        versionProvider = MainCommand.class,
        mixinStandardHelpOptions = true,
        synopsisSubcommandLabel = "COMMAND",
        subcommands = {
                CommandLine.HelpCommand.class,
                ChangeHeightCommand.class,
                RemoveAppsCommand.class,
                MoveGlobalAppsCommand.class,
                ClipTexturesCommand.class,
                FilterLodsCommand.class,
                ReprojectCommand.class,
                FromCityJSONCommand.class,
                ToCityJSONCommand.class
        })
public class MainCommand implements CityGMLTool, CommandLine.IVersionProvider {

    @CommandLine.Option(names = "--log", paramLabel = "<level>", description = "Log level: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private LogLevel logLevel = LogLevel.INFO;

    @CommandLine.Spec
    private CommandLine.Model.CommandSpec spec;

    private CityGMLBuilder cityGMLBuilder;

    @Override
    public Integer call() throws Exception {
        // check whether at least one subcommand is given
        CommandLine.ParseResult parseResult = spec.commandLine().getParseResult();
        List<CommandLine> commandLines = parseResult.asCommandLineList();
        if (commandLines.size() == 1)
            throw new CommandLine.ParameterException(spec.commandLine(), "Missing required subcommand.");

        // validate subcommand
        for (CommandLine commandLine : commandLines) {
            Object command = commandLine.getCommand();
            if (command instanceof CityGMLTool)
                ((CityGMLTool) command).validate();
        }

        Logger log = Logger.getInstance();
        log.setLogLevel(logLevel);

        log.info("Starting citygml-tools.");
        CityGMLContext context = CityGMLContext.getInstance();

        // search for ADE extensions
        URLClassLoader classLoader = new URLClassLoader(MainCommand.class.getClassLoader());
        try {
            Path adeExtensionsDir = Constants.APP_HOME.resolve(Constants.ADE_EXTENSIONS_DIR);
            if (Files.exists(adeExtensionsDir)) {
                try (Stream<Path> stream = Files.walk(adeExtensionsDir)
                        .filter(path -> path.getFileName().toString().toLowerCase().endsWith(".jar"))) {
                    stream.forEach(classLoader::addPath);
                }
            }

            if (classLoader.getURLs().length > 0) {
                log.info("Loading ADE extensions.");
                ServiceLoader<ADEContext> adeLoader = ServiceLoader.load(ADEContext.class, classLoader);

                for (ADEContext adeContext : adeLoader) {
                    log.debug("Registering ADE extension '" + adeContext.getClass().getTypeName() + "'.");
                    context.registerADEContext(adeContext);
                }
            }
        } catch (IOException e) {
            log.error("Failed to initialize ADE extensions.", e);
            return 1;
        }

        log.info("Initializing application environment.");
        cityGMLBuilder = context.createCityGMLBuilder(classLoader);

        log.info("Executing command '" + commandLines.get(0).getCommandName() + "'.");
        return 0;
    }

    public CityGMLBuilder getCityGMLBuilder() {
        return cityGMLBuilder;
    }

    @Override
    public String[] getVersion() throws Exception {
        return new String[] {
                getClass().getPackage().getImplementationTitle() + ", version " +
                        getClass().getPackage().getImplementationVersion() + "\n" +
                        "(c) 2018-" + LocalDate.now().getYear() + " Claus Nagel <claus.nagel@gmail.com>\n"
        };
    }
}
