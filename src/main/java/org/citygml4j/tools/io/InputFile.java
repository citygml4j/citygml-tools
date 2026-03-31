/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.io;

import java.nio.file.Path;
import java.util.Objects;

public class InputFile {
    private final Path file;
    private final Path basePath;

    private InputFile(Path file, Path basePath) {
        this.file = Objects.requireNonNull(file, "The input file must not be null.").toAbsolutePath().normalize();
        this.basePath = Objects.requireNonNull(basePath, "The base path must not be null.").toAbsolutePath().normalize();
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
