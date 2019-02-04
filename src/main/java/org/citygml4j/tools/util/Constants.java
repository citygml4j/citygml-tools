/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2013-2019 Claus Nagel <claus.nagel@gmail.com>
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

package org.citygml4j.tools.util;

import java.nio.file.Path;
import java.nio.file.Paths;

public class Constants {
    public static final Path APP_HOME;
    public static final Path WORKING_DIR;

    public static final String ADE_EXTENSIONS_DIR = "ade-extensions";

    static {
        String appHomeEnv = System.getenv("APP_HOME");
        if (appHomeEnv == null)
            appHomeEnv = ".";

        String workingDirEnv = System.getenv("WORKING_DIR");
        if (workingDirEnv == null)
            workingDirEnv = ".";

        APP_HOME = Paths.get(appHomeEnv).normalize().toAbsolutePath();
        WORKING_DIR = Paths.get(workingDirEnv).normalize().toAbsolutePath();
    }
}
