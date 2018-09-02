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
            result = cmd.parseWithHandlers(
                    new CityGMLTools(),
                    CommandLine.defaultExceptionHandler().andExit(1),
                    args);
        } catch (CommandLine.ExecutionException e) {
            log.error("Aborting citygml-tools due to an unexpected error.");
            log.logStackTrace(e);
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

        List<Object> executionResult = new ArrayList<>();
        for (CommandLine commandLine : commandLines) {
            Object command = commandLine.getCommand();
            if (!(command instanceof CityGMLTool))
                throw new CommandLine.ExecutionException(commandLine,
                        "Parsed command (" + command + ") is not a valid CityGML tool.");

            try {
                CityGMLTool cityGMLTool = (CityGMLTool) command;
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
