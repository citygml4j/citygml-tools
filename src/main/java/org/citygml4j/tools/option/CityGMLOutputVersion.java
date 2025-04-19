/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2025 Claus Nagel <claus.nagel@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
