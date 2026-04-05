/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.command.subset;

import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.command.Command;
import picocli.CommandLine;

@CommandLine.Command(name = "group",
        description = "Define an independent subset with its own filter criteria. " +
                "Each group is written to its own output file.")
public class GroupCommand implements Command {
    @CommandLine.Option(names = "--name",
            description = "Name of the group to use as filename suffix.")
    private String name;

    @CommandLine.Mixin
    private FilterOptions filterOptions;

    public GroupCommand() {
    }

    GroupCommand(String name, FilterOptions filterOptions) {
        this.name = name;
        this.filterOptions = filterOptions;
    }

    public String getName() {
        return name;
    }

    public FilterOptions getFilterOptions() {
        return filterOptions;
    }

    @Override
    public Integer call() throws ExecutionException {
        return CommandLine.ExitCode.OK;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (name != null && name.isBlank()) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: The group --name cannot be blank");
        }
    }
}
