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

import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.formats.tiff.TiffImageParser;
import org.apache.commons.imaging.formats.tiff.TiffImagingParameters;
import org.apache.commons.imaging.formats.tiff.constants.TiffConstants;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.core.model.appearance.*;
import org.citygml4j.core.model.core.AbstractFeature;
import org.citygml4j.core.visitor.ObjectWalker;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.io.FileHelper;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.tools.io.OutputFile;
import org.citygml4j.tools.log.Logger;
import org.xmlobjects.gml.model.geometry.DirectPosition;
import org.xmlobjects.gml.model.geometry.Envelope;
import org.xmlobjects.gml.util.id.DefaultIdCreator;
import org.xmlobjects.util.copy.CopyBuilder;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.*;

public class TextureClipper {
    private final Logger log = Logger.getInstance();
    private final Path inputDir;
    private final Path outputDir;
    private final CityGMLVersion version;
    private final ClippingProcessor clippingProcessor = new ClippingProcessor();

    private boolean forceJpeg;
    private float jpegCompressionQuality;
    private boolean clampTextureCoordinates;
    private int textureVertexPrecision;
    private String textureFolder;
    private String texturePrefix;
    private int textureBuckets;

    private Path targetFolder;

    private TextureClipper(Path inputDir, Path outputDir, CityGMLVersion version) {
        this.inputDir = inputDir;
        this.outputDir = outputDir;
        this.version = version;
    }

    public static TextureClipper of(InputFile inputFile, OutputFile outputFile, CityGMLVersion version) {
        return new TextureClipper(inputFile.getFile().getParent(), outputFile.getFile().getParent(), version);
    }

    public TextureClipper forceJpeg(boolean forceJpeg) {
        this.forceJpeg = forceJpeg;
        return this;
    }

    public TextureClipper withJpegCompressionQuality(float jpegCompressionQuality) {
        this.jpegCompressionQuality = jpegCompressionQuality;
        return this;
    }

    public TextureClipper clampTextureCoordinates(boolean clampTextureCoordinates) {
        this.clampTextureCoordinates = clampTextureCoordinates;
        return this;
    }

    public TextureClipper withTextureVertexPrecision(int textureVertexPrecision) {
        this.textureVertexPrecision = textureVertexPrecision;
        return this;
    }

    public TextureClipper withTextureFolder(String textureFolder) {
        this.textureFolder = textureFolder;
        return this;
    }

    public TextureClipper withTexturePrefix(String texturePrefix) {
        this.texturePrefix = texturePrefix;
        return this;
    }

    public TextureClipper withTextureBuckets(int textureBuckets) {
        this.textureBuckets = textureBuckets;
        return this;
    }

    public void clipTextures(AbstractFeature feature) throws ExecutionException {
        try {
            targetFolder = outputDir.resolve(textureFolder);
        } catch (InvalidPathException e) {
            throw new ExecutionException("Failed to create target texture folder " +
                    outputDir + File.separator + textureFolder + ".", e);
        }

        feature.accept(clippingProcessor);
    }

    private class ClippingProcessor extends ObjectWalker {
        private final CopyBuilder copyBuilder = new CopyBuilder().failOnError(true);
        private final Map<String, String> copiedImages = new HashMap<>();
        private final Set<String> folders = new HashSet<>();

        private Appearance appearance;
        private int counter;

        @Override
        public void visit(Appearance appearance) {
            this.appearance = appearance;
            super.visit(appearance);
        }

        @Override
        public void visit(ParameterizedTexture texture) {
            Path imageURI = getImageURI(texture);
            if (imageURI == null) {
                return;
            }

            BufferedImage image = null;
            ImageInfo imageInfo = null;
            try {
                image = readImage(imageURI.toFile());
                imageInfo = Imaging.getImageInfo(imageURI.toFile());
            } catch (Exception e) {
                if (!copiedImages.containsKey(imageURI.toString())) {
                    log.error("Failed to read texture image at " + imageURI + ".", e);
                }
            }

            if (image != null) {
                Iterator<TextureAssociationProperty> iterator = texture.getTextureParameterizations().iterator();
                while (iterator.hasNext()) {
                    TextureAssociationProperty property = iterator.next();
                    AbstractTextureParameterization parameterization = property.getObject() != null
                            && property.getObject().getTextureParameterization() != null ?
                            property.getObject().getTextureParameterization().getObject() :
                            null;

                    if (parameterization instanceof TexCoordList texCoordList) {
                        GeometryReference target = property.getObject().getTarget();

                        Envelope textureSpace = getTextureSpace(texCoordList, target, texture);
                        if (isSubRegion(textureSpace)) {
                            ClippedImage clippedImage = getClippedImage(textureSpace, image, target, texture);
                            if (clippedImage != null) {
                                adaptTextureCoordinates(texCoordList, image, clippedImage);
                                String targetURI = saveImage(clippedImage, imageInfo, imageURI);

                                ParameterizedTexture copy = copyBuilder.shallowCopy(texture);
                                copy.setId(DefaultIdCreator.getInstance().createId());
                                copy.setImageURI(targetURI);
                                copy.setTextureParameterizations(Collections.singletonList(property));
                                appearance.getSurfaceData().add(new AbstractSurfaceDataProperty(copy));
                            }
                        } else if (textureSpace != null) {
                            continue;
                        }

                        iterator.remove();
                    } else if (parameterization instanceof TexCoordGen) {
                        log.warn("A TexCoordGen is used for target '" + property.getObject().getTarget().getHref() +
                                "' of " + CityObjects.getObjectSignature(texture) + ". " +
                                "Cannot clip the texture image.");
                    }
                }
            }

            if (image == null
                    || texture.isSetTextureParameterizations()
                    || (version != CityGMLVersion.v3_0
                    && texture.hasDeprecatedProperties())) {
                String targetURI = copyImage(imageURI);
                texture.setImageURI(targetURI);
                setTextureVertexPrecision(texture);
            } else {
                appearance.getSurfaceData().remove(texture.getParent(AbstractSurfaceDataProperty.class));
            }
        }

        @Override
        public void visit(GeoreferencedTexture texture) {
            Path imageURI = getImageURI(texture);
            if (imageURI == null) {
                return;
            }

            String targetURI = copyImage(imageURI);
            texture.setImageURI(targetURI);
            copyWorldFile(imageURI, outputDir.resolve(targetURI));
        }

        private Path getImageURI(AbstractTexture texture) {
            try {
                return inputDir.resolve(texture.getImageURI().replaceAll("\\\\", "/"));
            } catch (Exception e) {
                log.warn("Skipping " + CityObjects.getObjectSignature(texture) + " due to an invalid " +
                        "image URI " + texture.getImageURI() + ".");
                return null;
            }
        }

        private BufferedImage readImage(File imageURI) throws Exception {
            BufferedImage textureImage;
            try {
                textureImage = Imaging.getBufferedImage(imageURI);
            } catch (Exception e) {
                textureImage = ImageIO.read(imageURI);
            }

            if (textureImage == null) {
                throw new IOException("Unsupported file format of texture image " + imageURI + ".");
            } else {
                return textureImage;
            }
        }

        private Envelope getTextureSpace(TexCoordList texCoordList, GeometryReference target, ParameterizedTexture texture) {
            Envelope textureSpace = new Envelope();
            boolean reported = false;

            for (TextureCoordinates textureCoordinates : texCoordList.getTextureCoordinates()) {
                List<Double> coordinates = textureCoordinates.getValue();

                for (int i = 0; i < coordinates.size(); i += 2) {
                    double s = round(coordinates.get(i));
                    double t = round(coordinates.get(i + 1));

                    if (s < 0 || s > 1 || t < 0 || t > 1) {
                        if (clampTextureCoordinates) {
                            s = clampTextureCoordinate(s);
                            t = clampTextureCoordinate(t);
                            coordinates.set(i, s);
                            coordinates.set(i + 1, t);

                            if (!reported) {
                                log.debug("Clamped texture coordinates to [0, 1] for target '" + target.getHref() +
                                        "' of " + CityObjects.getObjectSignature(texture) + ".");
                                reported = true;
                            }
                        } else {
                            log.warn("Texture coordinates for target '" + target.getHref() + "' of " +
                                    CityObjects.getObjectSignature(texture) + " are " + "outside [0, 1]. " +
                                    "Cannot clip the texture image.");
                            return new Envelope(new DirectPosition(0, 0), new DirectPosition(1, 1));
                        }
                    }

                    textureSpace.include(s, t);
                }
            }

            if (!textureSpace.isValid()) {
                log.error("Failed to calculate texture space region for target '" + target.getHref() + "' of " +
                        CityObjects.getObjectSignature(texture) + ".");
                return null;
            } else {
                return textureSpace;
            }
        }

        private boolean isSubRegion(Envelope textureSpace) {
            return textureSpace != null && (textureSpace.getSpan(1) != 1 || textureSpace.getSpan(2) != 1);
        }

        private ClippedImage getClippedImage(Envelope textureSpace, BufferedImage textureImage, GeometryReference target, ParameterizedTexture texture) {
            List<Double> coordinates = textureSpace.toCoordinateList3D();
            double x = Math.floor(textureImage.getWidth() * coordinates.get(0));
            double y = Math.ceil(textureImage.getHeight() * (1 - coordinates.get(1)));
            double width = Math.ceil(textureImage.getWidth() * coordinates.get(3)) - x;
            double height = y - Math.floor(textureImage.getHeight() * (1 - coordinates.get(4)));

            if (width <= 0 || height <= 0) {
                log.warn("Clipping texture image results in invalid bounds for target '" + target.getHref() + "' of " +
                        CityObjects.getObjectSignature(texture) + ".");
                return null;
            } else {
                return new ClippedImage(
                        textureImage.getSubimage((int) x, (int) y - (int) height, (int) width, (int) height),
                        x, y, width, height);
            }
        }

        private void adaptTextureCoordinates(TexCoordList texCoordList, BufferedImage textureImage, ClippedImage clippedImage) {
            for (TextureCoordinates textureCoordinates : texCoordList.getTextureCoordinates()) {
                List<Double> coordinates = textureCoordinates.getValue();
                for (int i = 0; i < coordinates.size(); i += 2) {
                    double s = textureImage.getWidth() * coordinates.get(i);
                    double t = textureImage.getHeight() * (1 - coordinates.get(i + 1));
                    s = (s - clippedImage.x()) / clippedImage.width();
                    t = (clippedImage.y() - t) / clippedImage.height();
                    coordinates.set(i, round(s));
                    coordinates.set(i + 1, round(t));
                }
            }
        }

        private void setTextureVertexPrecision(ParameterizedTexture texture) {
            for (TextureAssociationProperty property : texture.getTextureParameterizations()) {
                AbstractTextureParameterization parameterization = property.getObject() != null
                        && property.getObject().getTextureParameterization() != null ?
                        property.getObject().getTextureParameterization().getObject() :
                        null;
                if (parameterization instanceof TexCoordList texCoordList) {
                    for (TextureCoordinates textureCoordinates : texCoordList.getTextureCoordinates()) {
                        textureCoordinates.getValue().replaceAll(this::round);
                    }
                }
            }
        }

        private String saveImage(ClippedImage clippedImage, ImageInfo imageInfo, Path source) {
            try {
                Path target;
                if (!forceJpeg && imageInfo.getFormat() == ImageFormats.TIFF) {
                    target = getTargetPath(source, "tif");
                    TiffImagingParameters parameters = new TiffImagingParameters();
                    parameters.setCompression(TiffConstants.TIFF_COMPRESSION_UNCOMPRESSED);
                    try (OutputStream stream = Files.newOutputStream(target)) {
                        new TiffImageParser().writeImage(clippedImage.image(), stream, parameters);
                    }
                } else {
                    String format = !forceJpeg && imageInfo.isTransparent() ? "png" : "jpg";
                    target = getTargetPath(source, format);

                    ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
                    ImageWriteParam parameters = writer.getDefaultWriteParam();
                    if ("jpg".equals(format)) {
                        parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                        parameters.setCompressionQuality(jpegCompressionQuality);
                    }

                    try (ImageOutputStream stream = ImageIO.createImageOutputStream(target.toFile())) {
                        writer.setOutput(stream);
                        writer.write(null, new IIOImage(clippedImage.image(), null, null), parameters);
                    }
                }

                return outputDir.relativize(target).toString().replaceAll("\\\\", "/");
            } catch (Exception e) {
                throw new RuntimeException("Failed to save clipped texture image from " + source + ".", e);
            }
        }

        private String copyImage(Path source) {
            String imageURI = copiedImages.get(source.toString());
            if (imageURI == null) {
                try {
                    Path target = getTargetPath(source, null);
                    imageURI = outputDir.relativize(target).toString().replaceAll("\\\\", "/");
                    copiedImages.put(source.toString(), imageURI);
                    FileHelper.copy(source, target);
                } catch (Exception e) {
                    log.error("Failed to copy texture image " + source + ".", e);
                }
            }

            return imageURI;
        }

        private void copyWorldFile(Path source, Path target) {
            String fileName = source.getFileName().toString();
            List<String> worldFiles = new ArrayList<>();
            worldFiles.add(fileName + "w");

            String extension = FileHelper.getFileExtension(fileName);
            if (extension.length() == 3) {
                worldFiles.add(FileHelper.replaceFileExtension(fileName,
                        Character.toString(extension.charAt(0)) + extension.charAt(2) + "w"));
            }

            for (String worldFile : worldFiles) {
                Path candidate = source.resolveSibling(worldFile);
                if (Files.exists(candidate)) {
                    try {
                        fileName = target.getFileName().toString() + "w";
                        FileHelper.copy(candidate, target.resolveSibling(fileName));
                    } catch (IOException e) {
                        log.error("Failed to copy world file " + candidate + ".", e);
                    }
                }
            }
        }

        private Path getTargetPath(Path imageURI, String extension) throws Exception {
            Path target = targetFolder;
            if (textureBuckets > 0) {
                int bucket = Math.abs(counter % textureBuckets + 1);
                target = target.resolve(String.valueOf(bucket));
            }

            if (folders.add(target.toString())) {
                Files.createDirectories(target);
            }

            if (extension == null) {
                extension = FileHelper.getFileExtension(imageURI);
            }

            return target.resolve(texturePrefix + (++counter) + "." + extension);
        }

        private double clampTextureCoordinate(double coordinate) {
            if (coordinate < 0) {
                return 0;
            } else if (coordinate > 1) {
                return 1;
            } else {
                return coordinate;
            }
        }

        private double round(double coordinate) {
            return BigDecimal.valueOf(coordinate)
                    .setScale(textureVertexPrecision, RoundingMode.HALF_UP)
                    .doubleValue();
        }
    }

    private record ClippedImage(BufferedImage image, double x, double y, double width, double height) {
    }
}
