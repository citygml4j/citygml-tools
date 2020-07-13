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
