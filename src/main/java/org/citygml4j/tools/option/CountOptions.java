/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.option;

import picocli.CommandLine;

public class CountOptions implements Option {
    @CommandLine.Option(names = {"-l", "--limit"},
            description = "Maximum number of top-level city objects to process.")
    private Long count;

    @CommandLine.Option(names = "--start-index", paramLabel = "<index>",
            description = "Index within the result set from which top-level city objects are processed (0-based).")
    private Long startIndex;

    public long getCount() {
        return count != null ? count : Long.MAX_VALUE;
    }

    public long getStartIndex() {
        return startIndex != null ? startIndex : 0;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (count != null && count < 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: Count must be a non-negative integer but was '" + count + "'");
        }

        if (startIndex != null && startIndex < 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: Start index must be a non-negative integer but was '" + startIndex + "'");
        }
    }
}
