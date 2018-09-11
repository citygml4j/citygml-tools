package org.citygml4j.tools.command;

import picocli.CommandLine;

public class StandardInputOptions {

    @CommandLine.Parameters(paramLabel = "<file>", description = "File(s) or directory to process (glob patterns allowed).")
    private String file;

    public String getFile() {
        return file;
    }
}
