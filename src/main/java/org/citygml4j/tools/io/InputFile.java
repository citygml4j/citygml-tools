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

package org.citygml4j.tools.io;

import java.nio.file.Path;
import java.util.Objects;

public class InputFile {
    private final Path file;
    private final Path basePath;

    private InputFile(Path file, Path basePath) {
        this.file = Objects.requireNonNull(file, "The input file must not be null.");
        this.basePath = Objects.requireNonNull(basePath, "The base path must not be null.");
    }

    public static InputFile of(Path file) {
        Objects.requireNonNull(file, "The input file must not be null.");
        return new InputFile(file, file.getParent());
    }

    public static InputFile of(Path file, Path basePath) {
        return new InputFile(file, basePath);
    }

    public Path getFile() {
        return file;
    }

    public Path getBasePath() {
        return basePath;
    }

    @Override
    public String toString() {
        return file.toString();
    }
}
