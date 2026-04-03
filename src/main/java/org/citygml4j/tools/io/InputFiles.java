/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
 */

package org.citygml4j.tools.io;

import org.citygml4j.tools.CityGMLTools;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.log.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class InputFiles {
    private static final Pattern PATH_ELEMENT_PATTERN = Pattern.compile("[^*?{}!\\[\\]]+");
    private final List<String> files;
    private String defaultGlob = "**.{gml,xml}";
    private Predicate<Path> filter;

    private InputFiles(List<String> files) {
        this.files = files.stream().filter(Objects::nonNull).toList();
    }

    public static InputFiles of(List<String> files) {
        return new InputFiles(files != null ? files : Collections.emptyList());
    }

    public static InputFiles of(String... files) {
        return new InputFiles(files != null ? Arrays.asList(files) : Collections.emptyList());
    }

    public List<String> getFiles() {
        return files;
    }

    public InputFiles withDefaultGlob(String defaultGlob) {
        if (defaultGlob != null) {
            this.defaultGlob = defaultGlob;
        }

        return this;
    }

    public InputFiles withFilter(Predicate<Path> filter) {
        if (filter != null) {
            this.filter = filter;
        }

        return this;
    }

    public List<InputFile> find() throws ExecutionException {
        List<InputFile> inputFiles = new ArrayList<>();
        for (String file : files) {
            LinkedList<String> elements = parse(file);
            Path path = Paths.get(elements.pop()).toAbsolutePath().normalize();

            if (elements.isEmpty() && Files.isRegularFile(path)) {
                inputFiles.add(InputFile.of(path));
            } else {
                // construct a glob pattern from the path and the truncated elements
                String glob = "glob:" + path;
                if (!elements.isEmpty()) {
                    glob += File.separator + String.join(File.separator, elements);
                } else if (Files.isDirectory(path) && defaultGlob != null) {
                    glob += File.separator + defaultGlob;
                }

                try {
                    // find files matching the pattern
                    find(path, glob, inputFiles);
                } catch (IOException e) {
                    throw new ExecutionException("Failed to create list of input files.", e);
                }
            }
        }

        return inputFiles;
    }

    private void find(Path path, String glob, List<InputFile> inputFiles) throws IOException {
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(glob.replace("\\", "\\\\"));
        Files.walkFileTree(path, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE,
                new SimpleFileVisitor<>() {
                    @Override
                    public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                        if (attrs.isRegularFile() && matcher.matches(file.toAbsolutePath().normalize())) {
                            if (filter != null && !filter.test(file)) {
                                Logger.getInstance().debug("Skipping file " + file.toAbsolutePath() + ".");
                            } else {
                                inputFiles.add(InputFile.of(file, path));
                            }
                        }

                        return FileVisitResult.CONTINUE;
                    }
                });
    }

    private LinkedList<String> parse(String file) {
        Matcher matcher = PATH_ELEMENT_PATTERN.matcher("");
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
