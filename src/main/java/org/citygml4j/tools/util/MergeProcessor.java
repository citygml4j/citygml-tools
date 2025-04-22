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
import org.citygml4j.tools.io.FileCopier;
import org.citygml4j.tools.io.FileHelper;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.tools.io.OutputFile;
import org.citygml4j.tools.log.Logger;
import org.xmlobjects.gml.model.base.AbstractGML;
import org.xmlobjects.gml.model.base.AbstractInlineOrByReferenceProperty;
import org.xmlobjects.gml.model.base.AbstractReference;
import org.xmlobjects.gml.model.base.ResolvableAssociation;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class MergeProcessor implements AutoCloseable {
    private final Logger log = Logger.getInstance();
    private final Path outputDir;
    private final Processor processor = new Processor();

    private String textureDir = "merged_textures";
    private String libraryObjectDir = "merged_library_objects";
    private String pointFileDir = "merged_point_files";
    private String timeseriesDir = "merged_timeseries";
    private IdMode idMode = IdMode.KEEP_ALL;
    private int buckets = 10;

    private volatile boolean shouldProcess = true;
    private Exception exception;

    public enum IdMode {
        KEEP_ALL,
        KEEP_TOPLEVEL,
        REPLACE_ALL;

        @Override
        public String toString() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    private MergeProcessor(Path outputDir) {
        this.outputDir = outputDir;
    }

    public static MergeProcessor of(OutputFile outputFile) {
        return new MergeProcessor(outputFile.getFile().getParent());
    }

    public MergeProcessor withTextureDir(String textureDir) {
        if (textureDir != null) {
            this.textureDir = textureDir;
        }

        return this;
    }

    public MergeProcessor withLibraryObjectDir(String libraryObjectDir) {
        if (libraryObjectDir != null) {
            this.libraryObjectDir = libraryObjectDir;
        }

        return this;
    }

    public MergeProcessor withPointFileDir(String pointFileDir) {
        if (pointFileDir != null) {
            this.pointFileDir = pointFileDir;
        }

        return this;
    }

    public MergeProcessor withTimeseriesDir(String timeseriesDir) {
        if (timeseriesDir != null) {
            this.timeseriesDir = timeseriesDir;
        }

        return this;
    }

    public MergeProcessor withIdMode(IdMode idMode) {
        if (idMode != null) {
            this.idMode = idMode;
        }

        return this;
    }

    public MergeProcessor withBuckets(int buckets) {
        if (buckets >= 0) {
            this.buckets = buckets;
        }

        return this;
    }

    public void process(AbstractFeature feature, InputFile inputFile, Set<String> topLevelIds) throws ExecutionException {
        if (shouldProcess) {
            processor.process(feature, inputFile, topLevelIds);
        } else if (exception != null) {
            throw new ExecutionException("Failed to process external resources.", exception);
        }
    }

    public void reset() {
        processor.reset();
    }

    @Override
    public void close() throws ExecutionException {
        try {
            processor.close();
        } catch (Exception e) {
            throw new ExecutionException("Failed to process external resources.", e);
        }
    }

    private class Processor extends ObjectWalker implements AutoCloseable {
        private final FileCopier fileCopier = new FileCopier();
        private final Map<String, String> copied = new HashMap<>();
        private final Map<String, String> idMappings = new HashMap<>();
        private final Map<Type, Long> counter = new HashMap<>();

        private Path inputDir;
        private Set<String> topLevelIds;

        private enum Type {
            TEXTURE("tex_"),
            LIBRARY_OBJECT("lib_"),
            POINT_CLOUD_FILE("pnt_"),
            TIMESERIES_FILE("tms_");

            private final String prefix;

            Type(String prefix) {
                this.prefix = prefix;
            }
        }

        void process(AbstractFeature feature, InputFile inputFile, Set<String> topLevelIds) {
            this.inputDir = inputFile.getFile().getParent();
            this.topLevelIds = topLevelIds != null ? topLevelIds : Set.of();
            feature.accept(this);
        }

        @Override
        public void visit(AbstractGML object) {
            if (idMode != IdMode.KEEP_ALL
                    && object.getId() != null
                    && !topLevelIds.contains(object.getId())) {
                object.setId(idMappings.computeIfAbsent(object.getId(), v -> CityObjects.createId()));
            }

            super.visit(object);
        }

        @Override
        public void visit(AbstractInlineOrByReferenceProperty<?> property) {
            if (idMode != IdMode.KEEP_ALL && !property.isSetInlineObject()) {
                updateReference(property, property.getHref());
            }

            super.visit(property);
        }

        @Override
        public void visit(AbstractReference<?> reference) {
            if (idMode != IdMode.KEEP_ALL) {
                updateReference(reference, reference.getHref());
            }
        }

        private void updateReference(ResolvableAssociation<?> association, String reference) {
            if (reference != null) {
                String id = CityObjects.getIdFromReference(reference);
                if (!topLevelIds.contains(id)) {
                    association.setHref("#" + idMappings.computeIfAbsent(id, v -> CityObjects.createId()));
                }
            }
        }

        @Override
        public void visit(ParameterizedTexture texture) {
            texture.setImageURI(process(texture.getImageURI(), Type.TEXTURE, textureDir));
            super.visit(texture);
        }

        @Override
        public void visit(GeoreferencedTexture texture) {
            String imageURI = texture.getImageURI();
            texture.setImageURI(process(imageURI, Type.TEXTURE, textureDir));
            copyWorldFiles(imageURI, texture.getImageURI());
            super.visit(texture);
        }

        @Override
        public void visit(ImplicitGeometry implicitGeometry) {
            implicitGeometry.setLibraryObject(
                    process(implicitGeometry.getLibraryObject(), Type.LIBRARY_OBJECT, libraryObjectDir));
            super.visit(implicitGeometry);
        }

        @Override
        public void visit(PointCloud pointCloud) {
            pointCloud.setPointFile(process(pointCloud.getPointFile(), Type.POINT_CLOUD_FILE, pointFileDir));
            super.visit(pointCloud);
        }

        @Override
        public void visit(StandardFileTimeseries timeseries) {
            timeseries.setFileLocation(process(timeseries.getFileLocation(), Type.TIMESERIES_FILE, timeseriesDir));
            super.visit(timeseries);
        }

        @Override
        public void reset() {
            copied.clear();
            idMappings.clear();
            topLevelIds = null;
        }

        @Override
        public void close() throws Exception {
            try {
                fileCopier.close();
            } finally {
                reset();
            }
        }

        private String process(String location, Type type, String resourceDir) {
            Path source = getSourcePath(location);
            if (source != null) {
                String targetURI = copied.get(source.toString());
                if (targetURI == null) {
                    Path target = getTargetPath(source, type, resourceDir);
                    targetURI = outputDir.relativize(target).toString().replace("\\", "/");
                    copied.put(source.toString(), targetURI);
                    copy(source, target);
                }

                return targetURI;
            } else {
                return location;
            }
        }

        private Path getSourcePath(String location) {
            return location != null
                    && !location.isEmpty()
                    && !FileHelper.isURL(location) ?
                    inputDir.resolve(location.replace("\\", "/")).toAbsolutePath().normalize() :
                    null;
        }

        private Path getTargetPath(Path source, Type type, String resourceDir) {
            Path target = outputDir.resolve(resourceDir);
            long counter = this.counter.merge(type, 1L, Long::sum);
            if (buckets > 0) {
                long bucket = Math.abs((counter - 1) % buckets + 1);
                target = target.resolve(String.valueOf(bucket));
            }

            return target.resolve(type.prefix + counter + "." + FileHelper.getFileExtension(source));
        }

        private void copyWorldFiles(String location, String targetURI) {
            Path source = getSourcePath(location);
            if (source != null) {
                for (String worldFile : FileHelper.getWorldFiles(source.getFileName().toString())) {
                    Path candidate = source.resolveSibling(worldFile);
                    if (copied.put(candidate.toString(), "") == null) {
                        copy(candidate, outputDir.resolve(targetURI + "w").toAbsolutePath());
                    }
                }
            }
        }

        private void copy(Path source, Path target) {
            if (Files.exists(source)) {
                log.debug("Copying external resource " + source + " to " + target + ".");
                try {
                    fileCopier.copy(source, target);
                } catch (Exception e) {
                    shouldProcess = false;
                    exception = e;
                }
            }
        }
    }
}
