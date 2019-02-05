/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2019 Claus Nagel <claus.nagel@gmail.com>
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

package org.citygml4j.tools.textureclipper;

import org.apache.commons.imaging.ImageFormats;
import org.apache.commons.imaging.ImageInfo;
import org.apache.commons.imaging.ImageReadException;
import org.apache.commons.imaging.ImageWriteException;
import org.apache.commons.imaging.Imaging;
import org.apache.commons.imaging.ImagingConstants;
import org.apache.commons.imaging.formats.tiff.constants.TiffConstants;
import org.citygml4j.builder.copy.CopyBuilder;
import org.citygml4j.builder.copy.ShallowCopyBuilder;
import org.citygml4j.builder.jaxb.CityGMLBuilder;
import org.citygml4j.builder.jaxb.CityGMLBuilderException;
import org.citygml4j.geometry.BoundingBox;
import org.citygml4j.model.citygml.CityGML;
import org.citygml4j.model.citygml.CityGMLClass;
import org.citygml4j.model.citygml.appearance.Appearance;
import org.citygml4j.model.citygml.appearance.GeoreferencedTexture;
import org.citygml4j.model.citygml.appearance.ParameterizedTexture;
import org.citygml4j.model.citygml.appearance.SurfaceDataProperty;
import org.citygml4j.model.citygml.appearance.TexCoordList;
import org.citygml4j.model.citygml.appearance.TextureAssociation;
import org.citygml4j.model.citygml.appearance.TextureCoordinates;
import org.citygml4j.model.citygml.core.CityModel;
import org.citygml4j.model.gml.feature.AbstractFeature;
import org.citygml4j.model.module.citygml.CityGMLModuleType;
import org.citygml4j.model.module.citygml.CityGMLVersion;
import org.citygml4j.tools.common.log.Logger;
import org.citygml4j.util.gmlid.DefaultGMLIdManager;
import org.citygml4j.util.walker.FeatureWalker;
import org.citygml4j.xml.io.CityGMLInputFactory;
import org.citygml4j.xml.io.CityGMLOutputFactory;
import org.citygml4j.xml.io.reader.CityGMLReadException;
import org.citygml4j.xml.io.reader.CityGMLReader;
import org.citygml4j.xml.io.reader.FeatureReadMode;
import org.citygml4j.xml.io.reader.ParentInfo;
import org.citygml4j.xml.io.writer.CityGMLWriteException;
import org.citygml4j.xml.io.writer.CityModelInfo;
import org.citygml4j.xml.io.writer.CityModelWriter;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TextureClipper {
    private final Logger log = Logger.getInstance();
    private final CityGMLBuilder builder;
    private float jpegCompression = 1.0f;
    private boolean forceJPEG;

    private boolean adaptTexCoords;
    private int significantDigits = 7;

    private String appearanceDir = "appearance";
    private String fileNamePrefix = "tex";
    private int noOfBuckets = 0;

    private CityGMLVersion targetVersion;

    private TextureClipper(CityGMLBuilder builder) {
        this.builder = builder;
    }

    public static TextureClipper defaults(CityGMLBuilder builder) {
        return new TextureClipper(builder);
    }

    public TextureClipper withJPEGCompression(float jpegCompression) {
        if (jpegCompression >= 0 && jpegCompression <= 1)
            this.jpegCompression = jpegCompression;

        return this;
    }

    public TextureClipper forceJPEG(boolean forceJPEG) {
        this.forceJPEG = forceJPEG;
        return this;
    }

    public TextureClipper adaptTextureCoordinates(boolean adaptTexCoords) {
        this.adaptTexCoords = adaptTexCoords;
        return this;
    }

    public TextureClipper withSignificantDigits(int significantDigits) {
        if (significantDigits > 0)
            this.significantDigits = significantDigits;

        return this;
    }

    public TextureClipper withAppearanceDirectory(String appearanceDir) {
        if (appearanceDir != null && !appearanceDir.isEmpty()) {
            try {
                Path path = Paths.get(appearanceDir);
                if (path.isAbsolute())
                    throw new IllegalArgumentException("The appearance directory must be given by a local path.");

                this.appearanceDir = appearanceDir.replaceAll("\\\\", "/");
            } catch (InvalidPathException ignored) {
                //
            }
        }

        return this;
    }

    public TextureClipper withNumberOfBuckets(int noOfBuckets) {
        if (noOfBuckets >= 0)
            this.noOfBuckets = noOfBuckets;

        return this;
    }

    public TextureClipper withTextureFileNamePrefix(String fileNamePrefix) {
        if (fileNamePrefix != null && !fileNamePrefix.isEmpty())
            this.fileNamePrefix = fileNamePrefix;

        return this;
    }

    public TextureClipper withTargetVersion(CityGMLVersion targetVersion) {
        this.targetVersion = targetVersion;
        return this;
    }

    public void clipTextures(Path inputFile, Path outputFile) throws TextureClippingException {
        CityGMLInputFactory in;
        try {
            in = builder.createCityGMLInputFactory();
            in.setProperty(CityGMLInputFactory.FEATURE_READ_MODE, FeatureReadMode.SPLIT_PER_COLLECTION_MEMBER);
        } catch (CityGMLBuilderException e) {
            throw new TextureClippingException("Failed to create CityGML input factory", e);
        }

        CityGMLOutputFactory out = builder.createCityGMLOutputFactory(targetVersion);

        try {
            appearanceDir = createAppearanceDir(outputFile.getParent(), appearanceDir);
        } catch (IOException e) {
            throw new TextureClippingException("Failed to create the output directory '" + outputFile.getParent() + "'.");
        }

        log.debug("Reading city objects from input file and clipping textures.");

        try (CityGMLReader reader = in.createCityGMLReader(inputFile.toFile());
             CityModelWriter writer = out.createCityModelWriter(outputFile.toFile())) {

            writer.setPrefixes(targetVersion);
            writer.setSchemaLocations(targetVersion);
            writer.setDefaultNamespace(targetVersion.getCityGMLModule(CityGMLModuleType.CORE));
            writer.setIndentString("  ");
            boolean isInitialized = false;

            AppearanceWalker walker = new AppearanceWalker(inputFile.getParent(), outputFile.getParent());

            while (reader.hasNext()) {
                CityGML cityGML = reader.nextFeature();

                // write city model
                if (!isInitialized) {
                    ParentInfo parentInfo = reader.getParentInfo();
                    if (parentInfo != null && parentInfo.getCityGMLClass() == CityGMLClass.CITY_MODEL) {
                        CityModelInfo cityModelInfo = new CityModelInfo(parentInfo);
                        writer.setCityModelInfo(cityModelInfo);
                        writer.writeStartDocument();
                        isInitialized = true;
                    }
                }

                if (cityGML instanceof AbstractFeature && !(cityGML instanceof CityModel)) {
                    AbstractFeature feature = (AbstractFeature) cityGML;
                    feature.accept(walker);
                    if (!walker.shouldWalk())
                        throw new TextureClippingException("Failed to process feature with gml:id '" + feature.getId() + "'.");

                    writer.writeFeatureMember(feature);
                }
            }
        } catch (CityGMLReadException e) {
            throw new TextureClippingException("Failed to read city objects.", e);
        } catch (CityGMLWriteException e) {
            throw new TextureClippingException("Failed to write city objects.", e);
        }
    }

    private final class AppearanceWalker extends FeatureWalker {
        private final Path inputDir;
        private final Path outputDir;
        private Appearance appearance;
        private int textureCounter;
        private Map<String, String> copies;
        private CopyBuilder copyBuilder;

        private Matcher extensionMatcher = Pattern.compile("(.*)\\.(.+)$").matcher("");

        AppearanceWalker(Path inputDir, Path outputDir) {
            this.inputDir = inputDir;
            this.outputDir = outputDir;
            copies = new HashMap<>();
            copyBuilder = new ShallowCopyBuilder();
        }

        @Override
        public void visit(Appearance appearance) {
            this.appearance = appearance;
            super.visit(appearance);
        }

        @Override
        public void visit(ParameterizedTexture texture) {
            Path textureFile;
            try {
                textureFile = inputDir.resolve(texture.getImageURI().replaceAll("\\\\", "/"));
            } catch (InvalidPathException e) {
                log.debug("Parameterized texture '" + texture.getId() + "': Failed to create a valid path for image URI '" + texture.getImageURI() + "'. Keeping image URI as is.");
                return;
            }

            boolean copyTextureImage = false;
            BufferedImage image = null;
            ImageInfo imageInfo = null;

            // check whether we have texture coordinates
            for (TextureAssociation target : texture.getTarget()) {
                if (target.isSetHref()) {
                    log.debug("Parameterized texture '" + texture.getId() + "': Found unsupported xlink:href target. Copying texture image without change.");
                    copyTextureImage = true;
                } else if (!(target.getTextureParameterization() instanceof TexCoordList)) {
                    log.debug("Parameterized texture '" + texture.getId() + "': Found unsupported TexCoordGen target. Copying texture image without change.");
                    copyTextureImage = true;
                }
            }

            if (!copyTextureImage) {
                // try and read texture image
                try {
                    try {
                        image = Imaging.getBufferedImage(textureFile.toFile());
                    } catch (ImageReadException | IOException e) {
                        // use ImageIO as fallback
                        image = ImageIO.read(textureFile.toFile());
                    }

                    if (image != null)
                        imageInfo = Imaging.getImageInfo(textureFile.toFile());
                    else
                        throw new IOException("Parameterized texture '" + texture.getId() + "': Unsupported texture file format '" + texture.getImageURI() + "'.");

                } catch (IOException | ImageReadException e) {
                    log.error("Parameterized texture '" + texture.getId() + "': Failed to read texture image '" + texture.getImageURI() + "'. Copying texture image without change.", e);
                    copyTextureImage = true;
                }
            }

            if (copyTextureImage) {
                // copy texture image and return
                try {
                    texture.setImageURI(copy(texture.getImageURI(), false));
                } catch (IOException e) {
                    log.error("Failed to copy texture image '" + texture.getImageURI() + "'.", e);
                    setShouldWalk(false);
                }

                return;
            }

            // adapt texture coordinates
            Set<String> targets = new HashSet<>();
            for (TextureAssociation target : texture.getTarget()) {
                if (!targets.add(target.getUri())) {
                    log.debug("Parameterized texture '" + texture.getId() + "': Found duplicate target '" + target.getUri() + "'. Skipping target.");
                    continue;
                }

                ParameterizedTexture copy = (ParameterizedTexture) texture.copy(copyBuilder);
                copy.unsetImageURI();

                TexCoordList texCoordList = (TexCoordList) target.getTextureParameterization();
                BoundingBox textureSpaceRegion = new BoundingBox();

                // calculate texture space region based on texture coordinates
                for (TextureCoordinates texCoords : texCoordList.getTextureCoordinates()) {
                    List<Double> value = texCoords.getValue();
                    boolean reported = false;

                    if ((value.size() & 1) == 1) {
                        log.error("Parameterized texture '" + texture.getId() + "': Found uneven number of texture coordinates for target '" + target.getUri() + "'.");
                        setShouldWalk(false);
                        return;
                    }

                    for (int i = 0; i < value.size(); i += 2) {
                        double s = BigDecimal.valueOf(value.get(i)).setScale(significantDigits, RoundingMode.HALF_UP).doubleValue();
                        double t = BigDecimal.valueOf(value.get(i + 1)).setScale(significantDigits, RoundingMode.HALF_UP).doubleValue();

                        if (s < 0 || s > 1 || t < 0 || t > 1) {
                            if (adaptTexCoords) {
                                if (s < 0) s = 0;
                                if (s > 1) s = 1;
                                if (t < 0) t = 0;
                                if (t > 1) t = 1;

                                if (!reported) {
                                    log.debug("Parameterized texture '" + texture.getId() + "': Fixed texture coordinates outside [0,1] for target '" + target.getUri() + "'.");
                                    reported = true;
                                }
                            } else {
                                log.debug("Parameterized texture '" + texture.getId() + "': Found texture coordinates outside [0,1] " +
                                        "for target '" + target.getUri() + "'. Copying texture image without change.");
                                copyTextureImage = true;
                                break;
                            }
                        }

                        textureSpaceRegion.update(s, t, 0);
                    }

                    if (!copyTextureImage &&
                            (textureSpaceRegion.getLowerCorner().isEqual(Double.MAX_VALUE)
                            || textureSpaceRegion.getUpperCorner().isEqual(-Double.MAX_VALUE))) {
                        log.error("Parameterized texture '" + texture.getId() + "': Failed to calculate texture space region for target '" + target.getUri() + "'.");
                        setShouldWalk(false);
                        return;
                    } else if (textureSpaceRegion.getLowerCorner().isEqual(0, 0, 0)
                            && textureSpaceRegion.getUpperCorner().isEqual(1, 1, 0))
                        copyTextureImage = true;
                }

                String imageURI;
                if (copyTextureImage) {
                    // copy texture image and return
                    try {
                        imageURI = copy(texture.getImageURI(), false);
                    } catch (IOException e) {
                        log.error("Failed to copy texture image '" + texture.getImageURI() + "'.", e);
                        setShouldWalk(false);
                        return;
                    }
                } else {
                    // calculate sub-image region
                    // CityGML defines the origin to be located in the lower left corner so we need to convert the y value
                    double x = Math.floor(image.getWidth() * textureSpaceRegion.getLowerCorner().getX());
                    double y = Math.ceil(image.getHeight() * (1 - textureSpaceRegion.getLowerCorner().getY()));
                    double width = Math.ceil(image.getWidth() * textureSpaceRegion.getUpperCorner().getX()) - x;
                    double height = y - Math.floor(image.getHeight() * (1 - textureSpaceRegion.getUpperCorner().getY()));

                    if (width <= 0) {
                        log.warn("Parameterized texture '" + texture.getId() + "': Width of subimage is negative or zero for target '" + target.getUri() + "'. Skipping target.");
                        continue;
                    }

                    if (height <= 0) {
                        log.warn("Parameterized texture '" + texture.getId() + "': Height of subimage is negative or zero for target '" + target.getUri() + "'. Skipping taregt.");
                        continue;
                    }

                    // clip texture image
                    BufferedImage clippedImage = image.getSubimage((int) x, (int) y - (int) height, (int) width, (int) height);

                    // adapt texture coordinates for clipped image
                    for (TextureCoordinates texCoords : texCoordList.getTextureCoordinates()) {
                        List<Double> value = texCoords.getValue();

                        for (int i = 0; i < value.size(); i += 2) {
                            double s = image.getWidth() * value.get(i);
                            double t = image.getHeight() * (1 - value.get(i + 1));

                            // calculate new texture coordinates
                            s = (s - x) / width;
                            t = (y - t) / height;

                            // truncate texture coordinates to significants digits
                            value.set(i, BigDecimal.valueOf(s).setScale(significantDigits, RoundingMode.HALF_UP).doubleValue());
                            value.set(i + 1, BigDecimal.valueOf(t).setScale(significantDigits, RoundingMode.HALF_UP).doubleValue());
                        }
                    }

                    // write clipped image
                    try {
                        String fileName = getTextureFileName(texture.getImageURI(), false);
                        StringBuilder targetURI = new StringBuilder(appearanceDir);
                        Path targetDir = outputDir.resolve(appearanceDir);

                        if (noOfBuckets > 0) {
                            int bucket = getBucket();
                            targetURI.append("/").append(bucket);
                            targetDir = targetDir.resolve(String.valueOf(bucket));
                        }

                        if (!forceJPEG && imageInfo.getFormat() == ImageFormats.TIFF) {
                            fileName += ".tiff";
                            imageURI = targetURI.append("/").append(fileName).toString();

                            Map<String, Object> params = new HashMap<>();
                            params.put(ImagingConstants.PARAM_KEY_COMPRESSION, TiffConstants.TIFF_COMPRESSION_UNCOMPRESSED);
                            Imaging.writeImage(clippedImage, targetDir.resolve(fileName).toFile(), ImageFormats.TIFF, params);
                        }

                        else {
                            String format = !forceJPEG && imageInfo.isTransparent() ? "png" : "jpg";

                            fileName += "." + format;
                            imageURI = targetURI.append("/").append(fileName).toString();

                            ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
                            ImageWriteParam param;

                            if (imageInfo.isTransparent())
                                param = writer.getDefaultWriteParam();
                            else {
                                param = writer.getDefaultWriteParam();
                                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                                param.setCompressionQuality(jpegCompression);
                            }

                            writer.setOutput(ImageIO.createImageOutputStream(targetDir.resolve(fileName).toFile()));
                            writer.write(null, new IIOImage(clippedImage, null, null), param);
                        }
                    } catch (ImageWriteException | IOException e) {
                        log.error("Parameterized texture '" + texture.getId() + "': Failed to write texture subimage '" + texture.getImageURI() + "'.");
                        setShouldWalk(false);
                        return;
                    }
                }

                // update parameterized texture
                copy.setId(DefaultGMLIdManager.getInstance().generateUUID());
                copy.setImageURI(imageURI);
                copy.unsetTarget();
                copy.addTarget(target);
                appearance.addSurfaceDataMember(new SurfaceDataProperty(copy));
            }

            appearance.unsetSurfaceDataMember((SurfaceDataProperty) texture.getParent());
        }

        @Override
        public void visit(GeoreferencedTexture texture) {
            try {
                texture.setImageURI(copy(texture.getImageURI(), true));
            } catch (IOException e) {
                log.error("Georeferenced texture '" + texture.getId() + "': Failed to copy texture image '" + texture.getImageURI() + "'.", e);
                setShouldWalk(false);
            }
        }

        private String copy(String imageURI, boolean handleWorldFiles) throws IOException {
            String copy = copies.get(imageURI);

            if (copy == null) {
                try {
                    Path textureFile = inputDir.resolve(imageURI.replaceAll("\\\\", "/"));
                    String fileName = getTextureFileName(imageURI, true);
                    StringBuilder targetURI = new StringBuilder(appearanceDir);
                    Path targetDir = outputDir.resolve(appearanceDir);

                    if (noOfBuckets > 0) {
                        int bucket = getBucket();
                        targetURI.append("/").append(bucket);
                        targetDir = targetDir.resolve(String.valueOf(bucket));
                    }

                    copy = targetURI.append("/").append(fileName).toString();
                    copies.put(imageURI, copy);

                    Files.copy(textureFile, targetDir.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
                    if (handleWorldFiles)
                        copyWorldFile(textureFile, copy);

                } catch (InvalidPathException e) {
                    log.debug("Failed to create a valid path for image URI '" + imageURI + "'. Keeping image URI as is.");
                    copy = imageURI;
                }
            }

            return copy;
        }

        private void copyWorldFile(Path source, String target) throws IOException {
            List<Path> candidates = new ArrayList<>();
            candidates.add(source.resolveSibling(source.getFileName().toString() + "w"));

            extensionMatcher.reset(source.getFileName().toString());
            if (extensionMatcher.matches()) {
                String extension = extensionMatcher.group(2);
                if (extension.length() == 3)
                    candidates.add(source.resolveSibling(
                            extensionMatcher.group(1) + "." + extension.substring(0, 1) + extension.substring(2) + "w"));
            }

            for (Path candidate : candidates) {
                if (Files.exists(candidate)) {
                    String targetFile = target;

                    extensionMatcher.reset(candidate.getFileName().toString());
                    if (extensionMatcher.matches()) {
                        String extension = extensionMatcher.group(2);
                        extensionMatcher.reset(targetFile);
                        if (extensionMatcher.matches())
                            targetFile = targetFile.replaceAll(extensionMatcher.group(2) + "$", extension);
                    }

                    Files.copy(candidate, outputDir.resolve(targetFile), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }

        private String getTextureFileName(String imageURI, boolean addExtension) {
            String extension = "";
            if (addExtension) {
                extensionMatcher.reset(imageURI);
                if (extensionMatcher.matches())
                    extension = "." + extensionMatcher.group(2);
            }

            return fileNamePrefix + ++textureCounter + extension;
        }

        private int getBucket() {
            return Math.abs((textureCounter - 1) % noOfBuckets + 1);
        }
    }

    private synchronized String createAppearanceDir(Path outputDir, String appearanceDir) throws IOException {
        String path = appearanceDir;
        int level = 0;

        while (Files.exists(outputDir.resolve(path)))
            path = appearanceDir + "_" + ++level;

        Files.createDirectories(outputDir.resolve(path));
        for (int i = 0; i < noOfBuckets; i++)
            Files.createDirectories(outputDir.resolve(path).resolve(String.valueOf(i + 1)));

        return path;
    }
}
