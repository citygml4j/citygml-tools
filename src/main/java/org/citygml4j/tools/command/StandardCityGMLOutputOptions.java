/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2019 Claus Nagel <claus.nagel@gmail.com>
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
