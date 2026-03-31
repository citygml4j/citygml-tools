/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
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
