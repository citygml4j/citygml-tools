/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.option;

import org.citygml4j.core.model.CityGMLVersion;
import picocli.CommandLine;

public class CityGMLOutputVersion implements Option {
    @CommandLine.Option(names = {"-v", "--citygml-version"}, paramLabel = "<version>",
            description = "CityGML version for output: 3.0, 2.0, 1.0.")
    private String versionString;

    private CityGMLVersion version;

    public boolean isSetVersion() {
        return version != null;
    }

    public CityGMLVersion getVersion() {
        return version != null ? version : CityGMLVersion.v3_0;
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        if (versionString != null) {
            version = switch (versionString) {
                case "1.0" -> CityGMLVersion.v1_0;
                case "2.0" -> CityGMLVersion.v2_0;
                case "3.0" -> CityGMLVersion.v3_0;
                default -> throw new CommandLine.ParameterException(commandLine,
                        "Invalid value for option '--citygml-version': expected one of [3.0, 2.0, 1.0] " +
                                "but was '" + versionString + "'");
            };
        }
    }
}
