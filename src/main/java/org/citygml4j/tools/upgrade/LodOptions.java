/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2022 Claus Nagel <claus.nagel@gmail.com>
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

package org.citygml4j.tools.upgrade;

import org.citygml4j.tools.cli.Option;
import picocli.CommandLine;

public class LodOptions implements Option {
    @CommandLine.Option(names = {"-l", "--use-lod4-as-lod3"},
            description = "Use the LoD4 representation of city objects as LoD3, replacing an existing LoD3.")
    private boolean useLod4AsLod3;

    public boolean isUseLod4AsLod3() {
        return useLod4AsLod3;
    }
}
