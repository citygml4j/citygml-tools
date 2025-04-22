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

package org.citygml4j.tools;

import org.citygml4j.cityjson.ExtensionLoader;
import org.citygml4j.core.ade.ADEException;
import org.citygml4j.core.ade.ADERegistry;
import org.citygml4j.tools.command.*;
import org.citygml4j.tools.log.LogLevel;
import org.citygml4j.tools.log.Logger;
import org.citygml4j.tools.option.Option;
import org.citygml4j.tools.util.PidFile;
import org.citygml4j.tools.util.URLClassLoader;
import org.citygml4j.xml.CityGMLADELoader;
import picocli.CommandLine;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.stream.Stream;

@CommandLine.Command(
        name = CityGMLTools.APP_NAME,
        scope = CommandLine.ScopeType.INHERIT,
        description = "Collection of tools for processing CityGML files.",
        abbreviateSynopsis = true,
        mixinStandardHelpOptions = true,
        versionProvider = CityGMLTools.class,
        showAtFileInUsageHelp = true,
        sortOptions = false,
        subcommands = {
                CommandLine.HelpCommand.class,
                StatsCommand.class,
                ValidateCommand.class,
                ApplyXSLTCommand.class,
                ChangeHeightCommand.class,
                RemoveAppsCommand.class,
                ToLocalAppsCommand.class,
                ClipTexturesCommand.class,
                MergeCommand.class,
                SubsetCommand.class,
                FilterLodsCommand.class,
                ReprojectCommand.class,
                FromCityJSONCommand.class,
                ToCityJSONCommand.class,
                UpgradeCommand.class
        }
)
public class CityGMLTools implements Command, CommandLine.IVersionProvider {
    @CommandLine.Option(names = {"-L", "--log-level"}, scope = CommandLine.ScopeType.INHERIT, paramLabel = "<level>",
            defaultValue = "info", description = "Log level: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    private LogLevel logLevel;

    @CommandLine.Option(names = "--log-file", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<file>",
            description = "Write log messages to this file.")
    private Path logFile;

    @CommandLine.Option(names = "--pid-file", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<file>",
            description = "Create a file containing the process ID.")
    private Path pidFile;

    @CommandLine.Option(names = "--extensions", scope = CommandLine.ScopeType.INHERIT, paramLabel = "<dir>",
            description = "Load extensions from this directory.")
    private Path extensionsDir;

    public static final String APP_NAME = "citygml-tools";
    public static final String EXTENSIONS_DIR = "extensions";
    public static final Path APP_HOME;
    public static final Path WORKING_DIR;

    private final Logger log = Logger.getInstance();
    private String commandLine;
    private String subCommandName;

    static {
        String appHomeEnv = System.getenv("APP_HOME");
        if (appHomeEnv == null) {
            appHomeEnv = ".";
        }

        String workingDirEnv = System.getenv("WORKING_DIR");
        if (workingDirEnv == null) {
            workingDirEnv = ".";
        }

        APP_HOME = Paths.get(appHomeEnv).toAbsolutePath().normalize();
        WORKING_DIR = Paths.get(workingDirEnv).toAbsolutePath().normalize();
        System.setProperty("picocli.disable.closures", "true");
    }

    public static void main(String[] args) {
        CityGMLTools app = new CityGMLTools();
        try {
            System.exit(app.execute(args));
        } catch (Exception e) {
            app.logException(e);
            System.exit(CommandLine.ExitCode.SOFTWARE);
        }
    }

    private int execute(String[] args) throws Exception {
        Instant start = Instant.now();
        int exitCode = CommandLine.ExitCode.SOFTWARE;

        System.setProperty("picocli.disable.closures", "true");
        CommandLine cmd = new CommandLine(this);

        try {
            CommandLine.ParseResult parseResult = cmd.setCaseInsensitiveEnumValuesAllowed(true)
                    .setAbbreviatedOptionsAllowed(true)
                    .setAbbreviatedSubcommandsAllowed(true)
                    .setExecutionStrategy(new CommandLine.RunAll())
                    .parseArgs(args);

            List<CommandLine> commandLines = parseResult.asCommandLineList();
            for (CommandLine commandLine : commandLines) {
                if (commandLine.isUsageHelpRequested() || commandLine.isVersionHelpRequested()) {
                    return CommandLine.executeHelpRequest(parseResult);
                } else if (commandLine.getCommand() instanceof CommandLine.HelpCommand) {
                    return cmd.getExecutionStrategy().execute(parseResult);
                }
            }

            if (!parseResult.hasSubcommand()) {
                throw new CommandLine.ParameterException(cmd, "Missing required subcommand.");
            }

            for (CommandLine commandLine : commandLines) {
                Object command = commandLine.getCommand();

                for (Field field : command.getClass().getDeclaredFields()) {
                    if (Option.class.isAssignableFrom(field.getType())) {
                        field.setAccessible(true);
                        Option option = (Option) field.get(command);
                        if (option != null) {
                            option.preprocess(commandLine);
                        }
                    }
                }

                if (command instanceof Command) {
                    ((Command) command).preprocess(commandLine);
                }
            }

            commandLine = APP_NAME + " " + String.join(" ", args);
            subCommandName = commandLines.get(1).getCommandName();
            exitCode = cmd.getExecutionStrategy().execute(parseResult);

            log.info("Total execution time: " + formatElapsedTime(Duration.between(start, Instant.now())) + ".");
            int warnings = log.getNumberOfWarnings();
            int errors = log.getNumberOfErrors();

            if (exitCode == 1) {
                log.warn(APP_NAME + " execution failed.");
            } else if (warnings != 0 || errors != 0) {
                log.info(APP_NAME + " finished with " + (warnings != 0 ? warnings + " warning(s)" : "") +
                        (warnings != 0 && errors != 0 ? " and " : "") +
                        (errors != 0 ? errors + " error(s)" : "") + ".");
            } else {
                log.info(APP_NAME + " successfully completed.");
            }
        } catch (CommandLine.ParameterException e) {
            cmd.getParameterExceptionHandler().handleParseException(e, args);
            exitCode = CommandLine.ExitCode.USAGE;
        } catch (CommandLine.ExecutionException e) {
            logException(e.getCause());
        } catch (Exception e) {
            logException(e);
        } finally {
            log.close();
        }

        return exitCode;
    }

    @Override
    public Integer call() throws ExecutionException {
        initializeLogging();

        log.info("Starting " + APP_NAME + ".");
        loadADEExtensions(extensionsDir);
        createPidFile();

        log.info("Executing '" + subCommandName + "' command.");
        return CommandLine.ExitCode.OK;
    }

    private void initializeLogging() throws ExecutionException {
        log.setLogLevel(logLevel);

        if (logFile != null) {
            try {
                if (logFile.getParent() != null && !Files.exists(logFile.getParent())) {
                    Files.createDirectories(logFile);
                } else if (Files.isDirectory(logFile)) {
                    logFile = logFile.resolve(APP_NAME + ".log");
                }

                log.debug("Writing log messages to " + logFile.toAbsolutePath() + ".");
                log.setLogFile(logFile).writeToFile("# " + commandLine);
            } catch (IOException e) {
                throw new ExecutionException("Failed to create log file " + logFile.toAbsolutePath() + ".", e);
            }
        }

        // use default logging configuration for external loggers
        Path loggingProperties = Files.exists(APP_HOME.resolve("lib")) ?
                APP_HOME.resolve("lib").resolve("logging.properties") :
                APP_HOME.resolve("resources").resolve("logging.properties");

        if (Files.exists(loggingProperties)) {
            System.setProperty("java.util.logging.config.file", loggingProperties.toString());
        }
    }

    private void loadADEExtensions(Path extensionsDir) throws ExecutionException {
        extensionsDir = extensionsDir != null ?
                WORKING_DIR.resolve(extensionsDir) :
                APP_HOME.resolve(EXTENSIONS_DIR);

        if (Files.exists(extensionsDir) && Files.isDirectory(extensionsDir)) {
            URLClassLoader classLoader = new URLClassLoader(Thread.currentThread().getContextClassLoader());
            try {
                try (Stream<Path> stream = Files.walk(extensionsDir)
                        .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar"))) {
                    stream.forEach(classLoader::addPath);
                }

                if (classLoader.getURLs().length > 0) {
                    log.info("Loading extensions from " + extensionsDir.toAbsolutePath() + ".");

                    ADERegistry registry = ADERegistry.getInstance();
                    registry.loadADEs(classLoader);

                    registry.getADELoader(CityGMLADELoader.class).getADEModules().forEach(
                            module -> log.debug("Loaded CityGML ADE " + module.getNamespaceURI() +
                                    " for CityGML version " + module.getCityGMLVersion() + "."));

                    registry.getADELoader(ExtensionLoader.class).getExtensions().forEach(
                            extension -> log.debug("Loaded CityJSON Extension " + extension.getName() +
                                    " for CityJSON version " + extension.getCityJSONVersion() + "."));
                }
            } catch (ADEException | IOException e) {
                throw new ExecutionException("Failed to load ADE extensions.", e);
            }
        } else if (this.extensionsDir != null) {
            log.warn("The ADE extensions folder " + extensionsDir.toAbsolutePath() + " does not exist.");
        }
    }

    private void createPidFile() throws ExecutionException {
        if (pidFile != null) {
            try {
                log.debug("Creating PID file at " + pidFile.toAbsolutePath() + ".");
                PidFile.create(pidFile, true);
            } catch (IOException e) {
                throw new ExecutionException("Failed to create PID file.", e);
            }
        }
    }

    private void logException(Throwable e) {
        if (e instanceof ExecutionException) {
            if (log.getLogLevel() == LogLevel.DEBUG) {
                log.error(e.getMessage());
                log.logStackTrace(e.getCause());
            } else {
                log.error(e.getMessage(), e.getCause());
            }
        } else {
            log.error("An unexpected error occurred during execution.");
            log.logStackTrace(e);
        }

        log.warn(APP_NAME + " execution failed.");
    }

    private String formatElapsedTime(Duration elapsed) {
        long d = elapsed.toDaysPart();
        long h = elapsed.toHoursPart();
        long m = elapsed.toMinutesPart();
        long s = elapsed.toSecondsPart();

        if (d > 0) {
            return String.format("%02d d, %02d h, %02d m, %02d s", d, h, m, s);
        } else if (h > 0) {
            return String.format("%02d h, %02d m, %02d s", h, m, s);
        } else if (m > 0) {
            return String.format("%02d m, %02d s", m, s);
        } else {
            return String.format("%02d s", s);
        }
    }

    @Override
    public String[] getVersion() {
        try (InputStream stream = getClass().getResourceAsStream("/org/citygml4j/tools/application.properties")) {
            Properties properties = new Properties();
            properties.load(stream);
            return new String[]{
                    properties.getProperty("name") + " version " + properties.getProperty("version"),
                    "(C) 2018-" + LocalDate.now().getYear() + " Claus Nagel <claus.nagel@gmail.com>\n"
            };
        } catch (IOException e) {
            return new String[]{};
        }
    }
}
