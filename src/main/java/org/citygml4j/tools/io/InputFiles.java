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

import org.citygml4j.tools.CityGMLTools;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.log.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class InputFiles {
    private final List<Path> inputFiles;
    private final Path basePath;

    private InputFiles(List<Path> inputFiles, Path basePath) {
        this.inputFiles = inputFiles != null ? inputFiles : Collections.emptyList();
        this.basePath = basePath != null ? basePath : CityGMLTools.WORKING_DIR;
    }

    private static InputFiles of(List<Path> inputFiles, Path basePath) {
        return new InputFiles(inputFiles, basePath);
    }

    private static InputFiles of(Path inputFile) {
        return new InputFiles(List.of(inputFile), inputFile.getParent());
    }

    private static InputFiles empty() {
        return new InputFiles(null, null);
    }

    public static InputFilesBuilder of(String file) {
        return new InputFilesBuilder(file);
    }

    public List<Path> getInputFiles() {
        return inputFiles;
    }

    public Path get(int index) {
        return inputFiles.get(index);
    }

    public Path getBasePath() {
        return basePath;
    }

    public boolean isEmpty() {
        return inputFiles.isEmpty();
    }

    public int size() {
        return inputFiles.size();
    }

    public static class InputFilesBuilder {
        private final String file;
        private String defaultGlob = "**.{gml,xml}";
        private Predicate<Path> filter;

        private InputFilesBuilder(String file) {
            this.file = file;
        }

        public InputFilesBuilder withDefaultGlob(String defaultGlob) {
            if (defaultGlob != null) {
                this.defaultGlob = defaultGlob;
            }

            return this;
        }

        public InputFilesBuilder withFilter(Predicate<Path> filter) {
            this.filter = filter;
            return this;
        }

        public List<InputFile> find() throws ExecutionException {
            if (file != null) {
                LinkedList<String> elements = parse(file);
                Path path = Paths.get(elements.pop()).toAbsolutePath().normalize();

                if (elements.isEmpty() && Files.isRegularFile(path)) {
                    return List.of(InputFile.of(path));
                } else {
                    // construct a glob pattern from the path and the truncated elements
                    String glob = "glob:" + path;
                    if (!elements.isEmpty()) {
                        glob += File.separator + String.join(File.separator, elements);
                    } else if (Files.isDirectory(path) && defaultGlob != null) {
                        glob += File.separator + defaultGlob;
                    }

                    // find files matching the glob pattern
                    List<InputFile> inputFiles = new ArrayList<>();
                    PathMatcher matcher = FileSystems.getDefault().getPathMatcher(glob.replace("\\", "\\\\"));
                    try (Stream<Path> stream = Files.walk(path, FileVisitOption.FOLLOW_LINKS)) {
                        stream.filter(Files::isRegularFile).forEach(p -> {
                            if (matcher.matches(p.toAbsolutePath().normalize())) {
                                if (filter != null && !filter.test(p)) {
                                    Logger.getInstance().debug("Skipping file " + p.toAbsolutePath() + ".");
                                } else {
                                    inputFiles.add(InputFile.of(p, path));
                                }
                            }
                        });
                    } catch (IOException e) {
                        throw new ExecutionException("Failed to create list of input files.", e);
                    }

                    return inputFiles;
                }
            }

            return Collections.emptyList();
        }

        private LinkedList<String> parse(String file) {
            Matcher matcher = Pattern.compile("[^*?{}!\\[\\]]+").matcher("");
            LinkedList<String> elements = new LinkedList<>();
            Path path = null;

            if (file.startsWith("~" + File.separator)) {
                file = System.getProperty("user.home") + file.substring(1);
            }

            do {
                if (matcher.reset(file).matches()) {
                    try {
                        path = Paths.get(file);
                    } catch (Exception e) {
                        //
                    }
                }

                if (path == null) {
                    // the file is not a valid path, possibly because of glob patterns.
                    // so, let's iteratively truncate the last path element and try again.
                    int index = file.lastIndexOf(File.separator);
                    String pathElement = file.substring(index + 1);
                    file = file.substring(0, index != -1 ? index : 0);

                    // remember the truncated element
                    elements.addFirst(pathElement);
                }
            } while (path == null && !file.isEmpty());

            // resolve path against the working directory
            path = path == null ?
                    CityGMLTools.WORKING_DIR :
                    CityGMLTools.WORKING_DIR.resolve(path);

            elements.addFirst(path.toAbsolutePath().toString());
            return elements;
        }
    }
}
