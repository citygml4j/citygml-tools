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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

public class OutputFile {
    private final Path file;
    private final boolean isTemporary;

    private OutputFile(Path file, boolean isTemporary) {
        this.file = Objects.requireNonNull(file, "The output file must not be null.").toAbsolutePath().normalize();
        this.isTemporary = isTemporary;
    }

    public static OutputFile of(Path file) {
        return new OutputFile(file, false);
    }

    public static OutputFile temp(String prefix, String suffix) throws IOException {
        Path file = Files.createTempFile(prefix, suffix);
        file.toFile().deleteOnExit();
        return new OutputFile(file, true);
    }

    public Path getFile() {
        return file;
    }

    public boolean isTemporary() {
        return isTemporary;
    }

    @Override
    public String toString() {
        return file.toString();
    }
}
