/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.option;

import picocli.CommandLine;

public class OverwriteInputOptions implements Option {
    @CommandLine.Option(names = {"-O", "--overwrite"},
            description = "Overwrite input files.")
    private boolean overwrite;

    public boolean isOverwrite() {
        return overwrite;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (overwrite && commandLine.getParseResult().hasMatchedOption("--output")) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: --overwrite and --output are mutually exclusive (specify only one)");
        }
    }
}
