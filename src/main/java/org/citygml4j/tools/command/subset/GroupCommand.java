/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.command.subset;

import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.command.Command;
import picocli.CommandLine;

import java.util.Objects;

@CommandLine.Command(name = "group",
        description = "Define an additional subset written to a separate output file.")
public class GroupCommand implements Command {
    @CommandLine.Option(names = {"-n", "--name"},
            description = "Output filename suffix (default: group index, starting at 1).")
    private String name;

    @CommandLine.ArgGroup(exclusive = false,
            heading = "Filter options (applied to this group only):%n")
    private FilterOptions filterOptions;

    GroupCommand() {
    }

    GroupCommand(String name, FilterOptions filterOptions) {
        this.name = name;
        this.filterOptions = Objects.requireNonNullElseGet(filterOptions, FilterOptions::new);
    }

    public String getName() {
        return name;
    }

    public FilterOptions getFilterOptions() {
        return filterOptions != null ? filterOptions : new FilterOptions();
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
