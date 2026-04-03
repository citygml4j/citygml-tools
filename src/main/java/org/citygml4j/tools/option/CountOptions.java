/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.option;

import picocli.CommandLine;

public class CountOptions implements Option {
    @CommandLine.Option(names = {"-l", "--limit"},
            description = "Maximum number of top-level city objects to process.")
    private Long limit;

    @CommandLine.Option(names = "--start-index", paramLabel = "<index>",
            description = "Index within the result set from which top-level city objects are processed (0-based).")
    private Long startIndex;

    public long getLimit() {
        return limit != null ? limit : Long.MAX_VALUE;
    }

    public long getStartIndex() {
        return startIndex != null ? startIndex : 0;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (limit != null && limit < 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: Count must be a non-negative integer but was '" + limit + "'");
        }

        if (startIndex != null && startIndex < 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: Start index must be a non-negative integer but was '" + startIndex + "'");
        }
    }
}
