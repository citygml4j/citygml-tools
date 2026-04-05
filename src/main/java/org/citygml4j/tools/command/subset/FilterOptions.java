/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.command.subset;

import org.citygml4j.tools.option.Option;
import picocli.CommandLine;

public class FilterOptions implements Option {
    private boolean isEmpty = true;

    @CommandLine.ArgGroup(exclusive = false)
    private TypeNameOptions typeNameOptions;

    @CommandLine.ArgGroup
    private IdOptions idOptions;

    @CommandLine.ArgGroup(exclusive = false)
    private BoundingBoxOptions boundingBoxOptions;

    @CommandLine.Option(names = "--invert",
            description = "Invert the filter result.")
    private boolean invert;

    @CommandLine.ArgGroup(exclusive = false)
    private CountOptions countOptions;

    @CommandLine.Option(names = "--no-remove-group-members", negatable = true, defaultValue = "true",
            description = "Remove non-matching group members (default: ${DEFAULT-VALUE}).")
    private boolean removeGroupMembers;

    public TypeNameOptions getTypeNameOptions() {
        return typeNameOptions;
    }

    public IdOptions getIdOptions() {
        return idOptions;
    }

    public BoundingBoxOptions getBoundingBoxOptions() {
        return boundingBoxOptions;
    }

    public boolean isInvert() {
        return invert;
    }

    public CountOptions getCountOptions() {
        return countOptions;
    }

    public boolean isRemoveGroupMembers() {
        return removeGroupMembers;
    }

    public boolean isEmpty() {
        return isEmpty;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (typeNameOptions != null) {
            typeNameOptions.preprocess(commandLine);
        }

        if (idOptions != null) {
            idOptions.preprocess(commandLine);
        }

        if (boundingBoxOptions != null) {
            boundingBoxOptions.preprocess(commandLine);
        }

        if (countOptions != null) {
            countOptions.preprocess(commandLine);
        }

        isEmpty = typeNameOptions == null
                && idOptions == null
                && boundingBoxOptions == null
                && countOptions == null
                && !commandLine.getParseResult().hasMatchedOption("--invert")
                && !commandLine.getParseResult().hasMatchedOption("--no-remove-group-members");
    }
}
