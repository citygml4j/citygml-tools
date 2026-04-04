/*
 * SPDX-License-Identifier: Apache-2.0
 * Copyright Claus Nagel <claus.nagel@gmail.com>
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
import org.citygml4j.tools.io.FileCopier;
import org.citygml4j.tools.io.FileHelper;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.tools.io.OutputFile;
import org.citygml4j.tools.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class ExternalResourceCopier implements AutoCloseable {
    private final Logger log = Logger.getInstance();
    private final Path inputDir;
    private final Path basePath;
    private final Path outputDir;
    private final Set<ResourceType> skipTypes = new HashSet<>();
    private final Processor processor = new Processor();

    private volatile boolean shouldProcess;
    private Exception exception;

    private ExternalResourceCopier(InputFile inputFile, OutputFile outputFile) {
        inputDir = inputFile.getFile().getParent();
        basePath = inputFile.getBasePath();
        outputDir = outputFile.getFile().getParent();
        shouldProcess = !outputFile.isTemporary() && !inputDir.equals(outputDir);
    }

    public static ExternalResourceCopier of(InputFile inputFile, OutputFile outputFile) {
        return new ExternalResourceCopier(inputFile, outputFile);
    }

    public ExternalResourceCopier skip(ResourceType... types) {
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
    public void close() throws ExecutionException {
        try {
            processor.close();
        } catch (Exception e) {
            throw new ExecutionException("Failed to process external resources.", exception);
        }
    }

    private class Processor extends ObjectWalker {
        private final FileCopier fileCopier = new FileCopier();
        private final Set<String> copied = new HashSet<>();

        @Override
        public void visit(ParameterizedTexture texture) {
            if (!skipTypes.contains(ResourceType.TEXTURE)) {
                texture.setImageURI(process(texture.getImageURI()));
            }
        }

        @Override
        public void visit(GeoreferencedTexture texture) {
            if (!skipTypes.contains(ResourceType.TEXTURE)) {
                String imageURI = texture.getImageURI();
                texture.setImageURI(process(imageURI));
                for (String worldFile : FileHelper.getWorldFiles(imageURI)) {
                    process(worldFile);
                }
            }
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            if (!skipTypes.contains(ResourceType.LIBRARY_OBJECT)) {
                implicitGeometry.setLibraryObject(process(implicitGeometry.getLibraryObject()));
            }

            super.visit(implicitGeometry);
        }

        @Override
        public void visit(PointCloud pointCloud) {
            if (!skipTypes.contains(ResourceType.POINT_CLOUD_FILE)) {
                pointCloud.setPointFile(process(pointCloud.getPointFile()));
            }
        }

        @Override
        public void visit(StandardFileTimeseries timeseries) {
            if (!skipTypes.contains(ResourceType.TIMESERIES_FILE)) {
                timeseries.setFileLocation(process(timeseries.getFileLocation()));
            }
        }

        public void close() throws Exception {
            try {
                fileCopier.close();
            } finally {
                copied.clear();
            }
        }

        private String process(String location) {
            if (location != null && !location.isEmpty() && !FileHelper.isURL(location)) {
                Path source = inputDir.resolve(location.replace("\\", "/")).toAbsolutePath().normalize();
                if (source.startsWith(basePath)) {
                    Path target = outputDir.resolve(inputDir.relativize(source)).normalize();
                    if (copied.add(source.toString()) && Files.exists(source)) {
                        try {
                            log.debug("Copying external resource " + source + " to " + target + ".");
                            fileCopier.copy(source, target);
                        } catch (Exception e) {
                            shouldProcess = false;
                            exception = e;
                        }
                    }

                    return outputDir.relativize(target).toString().replace("\\", "/");
                } else {
                    return source.toUri().toString();
                }
            }

            return location;
        }
    }
}
