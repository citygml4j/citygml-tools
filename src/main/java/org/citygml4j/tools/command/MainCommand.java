package org.citygml4j.tools.command;

import org.citygml4j.CityGMLContext;
import org.citygml4j.builder.jaxb.CityGMLBuilder;
import org.citygml4j.tools.common.log.LogLevel;
import org.citygml4j.tools.common.log.Logger;
import picocli.CommandLine;

@CommandLine.Command(name = "citygml-tools",
        description = "Collection of tools for processing CityGML files.",
        version = {"hallo", "1.0", "bla"},
        mixinStandardHelpOptions = true,
        subcommands = {
                CommandLine.HelpCommand.class,
                GlobalAppMoverCommand.class
        })
public class MainCommand implements CityGMLTool {

    @CommandLine.Option(names = "--log", description = "Log level: debug, info, warn, error (default: ${DEFAULT-VALUE}).")
    private String logLevel = "info";

    private CityGMLBuilder cityGMLBuilder;

    @Override
    public boolean execute() throws Exception {
        Logger log = Logger.getInstance();
        log.setLogLevel(LogLevel.fromValue(logLevel));
        log.info("Starting citygml-tools.");

        // TODO: add support for ADEs

        log.info("Initializing application environment.");
        CityGMLContext context = CityGMLContext.getInstance();
        cityGMLBuilder = context.createCityGMLBuilder();

        return true;
    }

    public CityGMLBuilder getCityGMLBuilder() {
        return cityGMLBuilder;
    }

}
