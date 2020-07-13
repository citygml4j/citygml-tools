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

import org.citygml4j.CityGMLContext;
import org.citygml4j.builder.jaxb.CityGMLBuilder;
import org.citygml4j.model.citygml.ade.binding.ADEContext;
import org.citygml4j.tools.command.ChangeHeightCommand;
import org.citygml4j.tools.command.CityGMLTool;
import org.citygml4j.tools.command.ClipTexturesCommand;
import org.citygml4j.tools.command.FilterLodsCommand;
import org.citygml4j.tools.command.FromCityJSONCommand;
import org.citygml4j.tools.command.MoveGlobalAppsCommand;
import org.citygml4j.tools.command.RemoveAppsCommand;
import org.citygml4j.tools.command.ReprojectCommand;
import org.citygml4j.tools.command.ToCityJSONCommand;
import org.citygml4j.tools.command.ValidateCommand;
import org.citygml4j.tools.option.LoggingOptions;
import org.citygml4j.tools.common.log.Logger;
import org.citygml4j.tools.util.Constants;
import org.citygml4j.tools.util.ObjectRegistry;
import org.citygml4j.tools.util.URLClassLoader;
import org.citygml4j.tools.util.Util;
import picocli.CommandLine;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.ServiceLoader;
import java.util.concurrent.Callable;
import java.util.stream.Stream;

@CommandLine.Command(name = "citygml-tools",
        description = "Collection of tools for processing CityGML files.",
        versionProvider = CityGMLTools.class,
        mixinStandardHelpOptions = true,
        synopsisSubcommandLabel = "COMMAND",
        showAtFileInUsageHelp = true,
        subcommands = {
                CommandLine.HelpCommand.class,
                ValidateCommand.class,
                ChangeHeightCommand.class,
                RemoveAppsCommand.class,
                MoveGlobalAppsCommand.class,
                ClipTexturesCommand.class,
                FilterLodsCommand.class,
                ReprojectCommand.class,
                FromCityJSONCommand.class,
                ToCityJSONCommand.class
        })
public class CityGMLTools implements Callable<Integer>, CommandLine.IVersionProvider {
    private static final Logger log = Logger.getInstance();
    private CommandLine subcommand;

    @CommandLine.Mixin
    LoggingOptions logging;

    public static void main(String[] args) throws Exception {
        CityGMLTools cityGMLTools = new CityGMLTools();
        Instant start = Instant.now();
        int exitCode = 1;

        CommandLine cmd = new CommandLine(cityGMLTools)
                .setCaseInsensitiveEnumValuesAllowed(true)
                .setExecutionStrategy(new CommandLine.RunAll())
                .setAbbreviatedOptionsAllowed(true)
                .setAbbreviatedSubcommandsAllowed(true);

        try {
            CommandLine.ParseResult parseResult = cmd.parseArgs(args);
            if (CommandLine.printHelpIfRequested(parseResult))
                return;

            // check for required subcommand
            List<CommandLine> commandLines = parseResult.asCommandLineList();
            if (commandLines.size() == 1)
                throw new CommandLine.ParameterException(cmd, "Missing required subcommand.");

            // validate commands
            for (CommandLine commandLine : commandLines) {
                Object command = commandLine.getCommand();
                if (command instanceof CityGMLTool)
                    ((CityGMLTool) command).validate();
            }

            // execute commands
            cityGMLTools.subcommand = commandLines.get(1);
            exitCode = cmd.getExecutionStrategy().execute(parseResult);

            log.info("Total execution time: " + Util.formatElapsedTime(Duration.between(start, Instant.now()).toMillis()) + ".");
            int warnings = log.getNumberOfWarnings();
            int errors = log.getNumberOfErrors();

            if (exitCode == 1)
                log.warn("citygml-tools execution failed.");
            else if (errors != 0 || warnings != 0)
                log.info("citygml-tools finished with " + warnings + " warning(s) and " + errors + " error(s).");
            else
                log.info("citygml-tools successfully completed.");

        } catch (CommandLine.ParameterException e) {
            cmd.getParameterExceptionHandler().handleParseException(e, args);
            exitCode = 2;
        } catch (Throwable e) {
            log.error("The following unexpected error occurred during execution.");
            log.logStackTrace(e);
            log.warn("citygml-tools execution failed.");
        }

        System.exit(exitCode);
    }

    @Override
    public Integer call() throws Exception {
        log.setLogLevel(logging.getLogLevel());
        log.info("Starting citygml-tools.");

        CityGMLContext context = CityGMLContext.getInstance();

        // search for ADE extensions
        URLClassLoader classLoader = new URLClassLoader(CityGMLTools.class.getClassLoader());
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
            log.error("Failed to initialize ADE extensions.");
            throw e;
        }

        log.info("Initializing application environment.");
        CityGMLBuilder cityGMLBuilder = context.createCityGMLBuilder(classLoader);
        ObjectRegistry.getInstance().put(cityGMLBuilder);

        log.info("Executing command '" + subcommand.getCommandName() + "'.");
        return 0;
    }

    @Override
    public String[] getVersion() {
        return new String[]{
                getClass().getPackage().getImplementationTitle() + ", version " +
                        getClass().getPackage().getImplementationVersion() + "\n" +
                        "(c) 2018-" + LocalDate.now().getYear() + " Claus Nagel <claus.nagel@gmail.com>\n"
        };
    }

    public LoggingOptions getLoggingOptions() {
        return logging;
    }
}
