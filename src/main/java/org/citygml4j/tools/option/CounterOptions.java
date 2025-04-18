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

import picocli.CommandLine;

public class CounterOptions implements Option {
    @CommandLine.Option(names = {"-c", "--count"},
            description = "Maximum number of top-level city objects to process.")
    private Long count;

    @CommandLine.Option(names = "--start-index", paramLabel = "<index>",
            description = "Index within the result set to process top-level city objects from (0-based).")
    private Long startIndex;

    public long getCount() {
        return count != null ? count : Long.MAX_VALUE;
    }

    public long getStartIndex() {
        return startIndex != null ? startIndex : 0;
    }

    @Override
    public void preprocess(CommandLine commandLine) throws Exception {
        if (count != null && count < 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: Count must be a non-negative integer but was '" + count + "'");
        }

        if (startIndex != null && startIndex < 0) {
            throw new CommandLine.ParameterException(commandLine,
                    "Error: Start index must be a non-negative integer but was '" + startIndex + "'");
        }
    }
}
