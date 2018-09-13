package org.citygml4j.tools.command;

import picocli.CommandLine;

public interface CityGMLTool {
    boolean execute() throws Exception;
    void validate() throws CommandLine.ParameterException;
}