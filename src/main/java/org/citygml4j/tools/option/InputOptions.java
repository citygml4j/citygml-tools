/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.option;

import picocli.CommandLine;

public class InputOptions implements Option {
    @CommandLine.Parameters(paramLabel = "<file>",
            description = "Input file or directory to process (glob patterns supported).")
    private String file;

    @CommandLine.Option(names = "--input-encoding",
            description = "Encoding of input files.")
    private String encoding;

    public String getFile() {
        return file;
    }

    public boolean isSetEncoding() {
        return encoding != null;
    }

    public String getEncoding() {
        return encoding;
    }
}
