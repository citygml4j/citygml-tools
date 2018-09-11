package org.citygml4j.tools.command;

import org.citygml4j.model.module.citygml.CityGMLVersion;
import picocli.CommandLine;

public class StandardCityGMLOutputOptions {

    @CommandLine.Option(names = "--citygml", description = "CityGML version used for output file: 2.0, 1.0 (default: ${DEFAULT-VALUE}).")
    private String version = "2.0";

    public CityGMLVersion getVersion() {
        return version.equals("1.0") ? CityGMLVersion.v1_0_0 : CityGMLVersion.v2_0_0;
    }
}
