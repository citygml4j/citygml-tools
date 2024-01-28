/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2024 Claus Nagel <claus.nagel@gmail.com>
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
    @CommandLine.Option(names = {"-v", "--citygml-version"},
            description = "CityGML version to use for output file(s): 3.0, 2.0, 1.0. " +
                    "The default is to use the CityGML version of the input file.")
    private String version;

    private CityGMLVersion versionOption;

    public boolean isSetVersion() {
        return versionOption != null;
    }

    public CityGMLVersion getVersion() {
        return versionOption != null ? versionOption : CityGMLVersion.v3_0;
    }

    @Override
    public void preprocess(CommandLine commandLine) {
        if (version != null) {
            switch (version) {
                case "1.0":
                    versionOption = CityGMLVersion.v1_0;
                    break;
                case "2.0":
                    versionOption = CityGMLVersion.v2_0;
                    break;
                case "3.0":
                    versionOption = CityGMLVersion.v3_0;
                    break;
                default:
                    throw new CommandLine.ParameterException(commandLine,
                            "Invalid value for option '--citygml-version': expected one of [3.0, 2.0, 1.0] " +
                                    "but was '" + version + "'");
            }
        }
    }
}
