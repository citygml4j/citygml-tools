package org.citygml4j.tools.command;

import picocli.CommandLine;

@CommandLine.Command(name = "reproject",
        description = "Reprojects city objects to a new spatial reference system.",
        versionProvider = MainCommand.class,
        mixinStandardHelpOptions = true)
public class ReprojectCommand implements CityGMLTool {

    @CommandLine.Option(names = "--overwrite-files", description = "Overwrite input file(s).")
    private boolean overwriteInputFiles;

    @CommandLine.Mixin
    private StandardCityGMLOutputOptions cityGMLOutput;

    @CommandLine.Mixin
    private StandardInputOptions input;

    @CommandLine.ParentCommand
    private MainCommand main;

    @Override
    public boolean execute() throws Exception {
        return false;
    }
}
