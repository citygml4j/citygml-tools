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

package org.citygml4j.tools.util;

import org.citygml4j.core.model.appearance.GeoreferencedTexture;
import org.citygml4j.core.model.appearance.ParameterizedTexture;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.model.core.ImplicitGeometry;
import org.citygml4j.core.model.dynamizer.StandardFileTimeseries;
import org.citygml4j.core.model.pointcloud.PointCloud;
import org.citygml4j.core.visitor.ObjectWalker;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.concurrent.CountLatch;
import org.citygml4j.tools.concurrent.ExecutorHelper;
import org.citygml4j.tools.io.FileHelper;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.tools.io.OutputFile;
import org.citygml4j.tools.log.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;

public class ResourceProcessor implements AutoCloseable {
    private final Logger log = Logger.getInstance();
    private final Path inputDir;
    private final Path basePath;
    private final Path outputDir;
    private final boolean shouldProcess;
    private final Set<Type> skipTypes = new HashSet<>();
    private final Processor processor = new Processor();
    private final CountLatch countLatch = new CountLatch();
    private final ExecutorService service = ExecutorHelper.newFixedAndBlockingThreadPool(
            Math.max(2, Runtime.getRuntime().availableProcessors()), 100000);

    private volatile boolean shouldRun = true;
    private Throwable exception;

    public enum Type {
        PARAMETERIZED_TEXTURE,
        GEOREFERENCED_TEXTURE,
        LIBRARY_OBJECT,
        POINT_CLOUD_FILE,
        TIMESERIES_FILE
    }

    private ResourceProcessor(InputFile inputFile, OutputFile outputFile) {
        inputDir = inputFile.getFile().getParent();
        basePath = inputFile.getBasePath();
        outputDir = outputFile.getFile().getParent();
        shouldProcess = !inputDir.equals(outputDir);
    }

    public static ResourceProcessor of(InputFile inputFile, OutputFile outputFile) {
        return new ResourceProcessor(inputFile, outputFile);
    }

    public ResourceProcessor skip(Type... types) {
        if (types != null) {
            skipTypes.addAll(Arrays.asList(types));
        }

        return this;
    }

    public void process(AbstractFeature feature) throws ExecutionException {
        if (shouldProcess) {
            feature.accept(processor);
            if (exception != null) {
                throw new ExecutionException("Failed to process external resources.", exception);
            }
        }
    }

    @Override
    public void close() {
        countLatch.await();
        service.shutdown();
        processor.reset();
    }

    private class Processor extends ObjectWalker {
        private final Set<String> copiedFiles = new HashSet<>();
        private final Set<String> createdDirs = ConcurrentHashMap.newKeySet();
        private final Object lock = new Object();

        @Override
        public void visit(ParameterizedTexture texture) {
            if (!skipTypes.contains(Type.PARAMETERIZED_TEXTURE)) {
                texture.setImageURI(process(texture.getImageURI()));
            }
        }

        @Override
        public void visit(GeoreferencedTexture texture) {
            if (!skipTypes.contains(Type.GEOREFERENCED_TEXTURE)) {
                String imageURI = process(texture.getImageURI());
                if (imageURI != null) {
                    process(imageURI + "w");

                    String[] fileName = FileHelper.splitFileName(imageURI);
                    if (fileName[1].length() == 3) {
                        process(fileName[0] + "." + fileName[1].charAt(0) + fileName[1].charAt(2) + "w");
                    }
                }
            }
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            if (!skipTypes.contains(Type.LIBRARY_OBJECT)) {
                implicitGeometry.setLibraryObject(process(implicitGeometry.getLibraryObject()));
            }

            super.visit(implicitGeometry);
        }

        @Override
        public void visit(PointCloud pointCloud) {
            if (!skipTypes.contains(Type.POINT_CLOUD_FILE)) {
                pointCloud.setPointFile(process(pointCloud.getPointFile()));
            }
        }

        @Override
        public void visit(StandardFileTimeseries timeseries) {
            if (!skipTypes.contains(Type.TIMESERIES_FILE)) {
                timeseries.setFileLocation(process(timeseries.getFileLocation()));
            }
        }

        @Override
        public void reset() {
            copiedFiles.clear();
            createdDirs.clear();
        }

        private String process(String location) {
            if (location != null && !location.isEmpty() && !FileHelper.isURL(location)) {
                if ("/".equals(inputDir.getFileSystem().getSeparator())) {
                    location = location.replace("\\", "/");
                }

                Path source = inputDir.resolve(location).toAbsolutePath().normalize();
                if (source.startsWith(basePath)) {
                    Path target = outputDir.resolve(inputDir.relativize(source)).normalize();
                    if (shouldRun
                            && copiedFiles.add(source.toString())
                            && Files.exists(source)) {
                        countLatch.increment();
                        service.submit(() -> {
                            try {
                                log.debug("Copying external resource " + source + " to " + target + ".");
                                copy(source, target);
                            } catch (Throwable e) {
                                shouldRun = false;
                                exception = e;
                            } finally {
                                countLatch.decrement();
                            }
                        });
                    }

                    return outputDir.relativize(target).toString().replaceAll("\\\\", "/");
                } else {
                    return source.toUri().toString();
                }
            } else {
                return location;
            }
        }

        private void copy(Path source, Path target) throws IOException {
            if (!createdDirs.contains(target.getParent().toString())) {
                synchronized (lock) {
                    if (createdDirs.add(target.getParent().toString()) && !Files.exists(target.getParent())) {
                        Files.createDirectories(target.getParent());
                    }
                }
            }

            FileHelper.copy(source, target);
        }
    }
}
