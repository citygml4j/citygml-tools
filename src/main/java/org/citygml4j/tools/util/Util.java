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

import java.io.File;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Util {

    public static Path getRootDirectory(String file) {
        LinkedList<String> elements = parseInputFile(file);
        Path path = Paths.get(elements.pop());
        if (!Files.isDirectory(path))
            path = path.getParent();

        return path;
    }

    public static List<Path> listFiles(String file, String defaultGlob) throws IOException {
        LinkedList<String> elements = parseInputFile(file);
        Path path = Paths.get(elements.pop());

        // construct a glob pattern from the path and the truncated elements
        StringBuilder glob = new StringBuilder("glob:").append(path.toAbsolutePath().normalize());
        if (!elements.isEmpty())
            glob.append(File.separator).append(String.join(File.separator, elements));
        else if (Files.isDirectory(path) && defaultGlob != null && !defaultGlob.isEmpty())
            glob.append(File.separator).append(defaultGlob);

        // find files matching the glob pattern
        List<Path> files = new ArrayList<>();
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(glob.toString().replace("\\", "\\\\"));
        Files.walk(path).forEach((p) -> {
            if (matcher.matches(p.toAbsolutePath().normalize()))
                files.add(p);
        });

        return files;
    }

    public static Path replaceFileExtension(Path file, String extension) {
        if (!Files.isRegularFile(file ))
            throw new IllegalArgumentException(file.toAbsolutePath() + " is not a file.");

        if (!extension.startsWith("."))
            extension = "." + extension;

        String fileName = file.getFileName().toString();
        int index = fileName.lastIndexOf(".");
        if (index >= 0)
            fileName = fileName.substring(0, index) + extension;
        else
            fileName += extension;

        return file.resolveSibling(fileName);
    }

    public static Path addFileNameSuffix(Path file, String suffix) {
        if (!Files.isRegularFile(file ))
            throw new IllegalArgumentException(file.toAbsolutePath() + " is not a file.");

        String fileName = file.getFileName().toString();
        int index = fileName.lastIndexOf(".");
        if (index >= 0) {
            String extension = fileName.substring(index);
            fileName = fileName.substring(0, index);
            fileName += suffix + extension;
        } else
            fileName += suffix;

        return file.resolveSibling(fileName);
    }

    public static String formatElapsedTime(long millis) {
        long d = TimeUnit.MILLISECONDS.toDays(millis);
        long h = TimeUnit.MILLISECONDS.toHours(millis) % TimeUnit.DAYS.toHours(1);
        long m = TimeUnit.MILLISECONDS.toMinutes(millis) % TimeUnit.HOURS.toMinutes(1);
        long s = TimeUnit.MILLISECONDS.toSeconds(millis) % TimeUnit.MINUTES.toSeconds(1);

        if (d > 0)
            return String.format("%02d d, %02d h, %02d m, %02d s", d, h, m, s);
        if (h > 0)
            return String.format("%02d h, %02d m, %02d s", h, m, s);
        if (m > 0)
            return String.format("%02d m, %02d s", m, s);

        return String.format("%02d s", s);
    }

    private static LinkedList<String> parseInputFile(String file) {
        LinkedList<String> elements = new LinkedList<>();
        Path path = null;

        do {
            try {
                path = Paths.get(file);
            } catch (Exception e) {
                // the file is not a valid path, possibly because of glob patterns.
                // so, let's iteratively truncate the last path element and try again.
                int index = file.lastIndexOf(File.separator);
                String pathElement = file.substring(index + 1);
                file = file.substring(0, index != -1 ? index : 0);

                // remember the truncated element
                elements.addFirst(pathElement);
            }
        } while (path == null && file.length() > 0);

        // resolve path against the working directory
        path = path == null ? Constants.WORKING_DIR : Constants.WORKING_DIR.resolve(path);
        elements.addFirst(path.toAbsolutePath().toString());

        return elements;
    }

}
