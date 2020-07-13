package org.citygml4j.tools.option;

import org.citygml4j.tools.CityGMLTools;
import org.citygml4j.tools.common.log.LogLevel;
import picocli.CommandLine;

public class LoggingOptions {
    private LogLevel logLevel = LogLevel.INFO;

    private @CommandLine.Spec(CommandLine.Spec.Target.MIXEE)
    CommandLine.Model.CommandSpec mixee;

    private static LoggingOptions getRootLoggingOptions(CommandLine.Model.CommandSpec commandSpec) {
        return ((CityGMLTools) commandSpec.root().userObject()).getLoggingOptions();
    }

    public LogLevel getLogLevel() {
        return getRootLoggingOptions(mixee).logLevel;
    }

    @CommandLine.Option(names = "--log", paramLabel = "<level>", description = "Log level: ${COMPLETION-CANDIDATES} (default: ${DEFAULT-VALUE}).")
    public void setLogLevel(LogLevel logLevel) {
        getRootLoggingOptions(mixee).logLevel = logLevel;
    }
}
