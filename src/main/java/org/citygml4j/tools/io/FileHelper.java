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

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.file.Path;

public class FileHelper {

    public static String appendFileNameSuffix(Path file, String suffix) {
        return appendFileNameSuffix(file.getFileName().toString(), suffix);
    }

    public static String appendFileNameSuffix(String fileName, String suffix) {
        String[] parts = splitFileName(fileName);
        return parts[0] + suffix + "." + parts[1];
    }

    public static String replaceFileExtension(Path file, String extension) {
        return replaceFileExtension(file.getFileName().toString(), extension);
    }

    public static String replaceFileExtension(String fileName, String extension) {
        return splitFileName(fileName)[0] + (extension.startsWith(".") ? extension : "." + extension);
    }

    public static String stripFileExtension(Path file) {
        return stripFileExtension(file.getFileName().toString());
    }

    public static String stripFileExtension(String fileName) {
        return splitFileName(fileName)[0];
    }

    public static String getFileExtension(Path file) {
        return getFileExtension(file.getFileName().toString());
    }

    public static String getFileExtension(String fileName) {
        return splitFileName(fileName)[1];
    }

    public static String[] splitFileName(Path file) {
        return splitFileName(file.getFileName().toString());
    }

    public static String[] splitFileName(String fileName) {
        int index = fileName.lastIndexOf('.');
        return index > 0 ?
                new String[]{fileName.substring(0, index), fileName.substring(index + 1)} :
                new String[]{fileName, ""};
    }

    public static void copy(Path source, Path target) throws IOException {
        try (FileInputStream in = new FileInputStream(source.toFile());
             FileOutputStream out = new FileOutputStream(target.toFile());
             FileChannel sourceChannel = in.getChannel();
             FileChannel targetChannel = out.getChannel()) {
            sourceChannel.transferTo(0, sourceChannel.size(), targetChannel);
        }
    }
}
