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

package org.citygml4j.tools.command;

import org.citygml4j.cityjson.CityJSONContext;
import org.citygml4j.cityjson.CityJSONContextException;
import org.citygml4j.cityjson.model.CityJSONVersion;
import org.citygml4j.cityjson.reader.CityJSONInputFactory;
import org.citygml4j.cityjson.reader.CityJSONReadException;
import org.citygml4j.cityjson.reader.CityJSONReader;
import org.citygml4j.cityjson.writer.AbstractCityJSONWriter;
import org.citygml4j.cityjson.writer.CityJSONOutputFactory;
import org.citygml4j.cityjson.writer.CityJSONWriteException;
import org.citygml4j.core.ade.ADERegistry;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.tools.CityGMLTools;
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.io.FileHelper;
import org.citygml4j.tools.io.InputFile;
import org.citygml4j.tools.io.InputFiles;
import org.citygml4j.tools.io.OutputFile;
import org.citygml4j.tools.log.Logger;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.CityJSONOutputOptions;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.option.OverwriteInputOptions;
import org.citygml4j.tools.util.IdCreator;
import org.citygml4j.xml.CityGMLADELoader;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.CityGMLContextException;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.module.citygml.CoreModule;
import org.citygml4j.xml.reader.*;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;
import org.xmlobjects.schema.SchemaHandler;

import javax.xml.XMLConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

public abstract class CityGMLTool implements Command {
    final Logger log = Logger.getInstance();
    private final Set<String> outputDirs = new HashSet<>();
    private CityGMLContext cityGMLContext;
    private CityJSONContext cityJSONContext;

    CityGMLContext getCityGMLContext() throws ExecutionException {
        if (cityGMLContext == null) {
            try {
                cityGMLContext = CityGMLContext.newInstance();
            } catch (CityGMLContextException e) {
                throw new ExecutionException("Failed to create CityGML context.", e);
            }
        }

        return cityGMLContext;
    }

    CityJSONContext getCityJSONContext() throws ExecutionException {
        if (cityJSONContext == null) {
            try {
                cityJSONContext = CityJSONContext.newInstance();
            } catch (CityJSONContextException e) {
                throw new ExecutionException("Failed to create CityJSON context.", e);
            }
        }

        return cityJSONContext;
    }

    CityGMLInputFactory createCityGMLInputFactory() throws ExecutionException {
        try {
            return getCityGMLContext().createCityGMLInputFactory().withIdCreator(new IdCreator());
        } catch (CityGMLReadException e) {
            throw new ExecutionException("Failed to create CityGML input factory.", e);
        }
    }

    CityGMLReader createCityGMLReader(CityGMLInputFactory in, InputFile file, InputOptions options) throws ExecutionException, CityGMLReadException {
        return createCityGMLReader(in, file, options.getEncoding(), null);
    }

    CityGMLReader createCityGMLReader(CityGMLInputFactory in, InputFile file, String encoding) throws ExecutionException, CityGMLReadException {
        return createCityGMLReader(in, file, encoding, null);
    }

    CityGMLReader createSkippingCityGMLReader(CityGMLInputFactory in, InputFile file, InputOptions options, String... localNames) throws ExecutionException, CityGMLReadException {
        return createSkippingCityGMLReader(in, file, options.getEncoding(), localNames);
    }

    CityGMLReader createSkippingCityGMLReader(CityGMLInputFactory in, InputFile file, String encoding, String... localNames) throws ExecutionException, CityGMLReadException {
        CityGMLInputFilter filter = null;
        if (localNames != null) {
            Set<String> names = new HashSet<>(Arrays.asList(localNames));
            filter = name -> !names.contains(name.getLocalPart())
                    || !CityGMLModules.isCityGMLNamespace(name.getNamespaceURI());
        }

        return createCityGMLReader(in, file, encoding, filter);
    }

    private CityGMLReader createCityGMLReader(CityGMLInputFactory in, InputFile file, String encoding, CityGMLInputFilter filter) throws ExecutionException, CityGMLReadException {
        CityGMLReader reader;
        try {
            reader = in.createCityGMLReader(file.getFile(), encoding);
            if (filter != null) {
                reader = in.createFilteredCityGMLReader(reader, filter);
            }
        } catch (CityGMLReadException e) {
            throw new ExecutionException("Failed to create CityGML reader.", e);
        }

        reportUnsupportedNamespaces(reader);
        return reader;
    }

    CityGMLOutputFactory createCityGMLOutputFactory(CityGMLVersion version) throws ExecutionException {
        return getCityGMLContext().createCityGMLOutputFactory(version);
    }

    CityGMLChunkWriter createCityGMLChunkWriter(CityGMLOutputFactory out, OutputFile file, CityGMLOutputOptions options) throws ExecutionException {
        return createCityGMLChunkWriter(out, file, options.getEncoding(), options.isPrettyPrint());
    }

    CityGMLChunkWriter createCityGMLChunkWriter(CityGMLOutputFactory out, OutputFile file, String encoding, boolean prettyPrint) throws ExecutionException {
        try {
            return out.createCityGMLChunkWriter(file.getFile(), encoding)
                    .withDefaultPrefixes()
                    .withDefaultSchemaLocations()
                    .withDefaultNamespace(CoreModule.of(out.getVersion()).getNamespaceURI())
                    .withIndent(prettyPrint ? "  " : null);
        } catch (CityGMLWriteException e) {
            throw new ExecutionException("Failed to create CityGML writer.", e);
        }
    }

    CityJSONInputFactory createCityJSONInputFactory() throws ExecutionException {
        return getCityJSONContext().createCityJSONInputFactory();
    }

    CityJSONReader createCityJSONReader(CityJSONInputFactory in, InputFile file, InputOptions options) throws ExecutionException {
        try {
            return options.isSetEncoding() ?
                    in.createCityJSONReader(file.getFile(), options.getEncoding()) :
                    in.createCityJSONReader(file.getFile());
        } catch (CityJSONReadException e) {
            throw new ExecutionException("Failed to create CityJSON reader.", e);
        }
    }

    CityJSONOutputFactory createCityJSONOutputFactory(CityJSONVersion version) throws ExecutionException {
        return getCityJSONContext().createCityJSONOutputFactory(version);
    }

    AbstractCityJSONWriter<?> createCityJSONWriter(CityJSONOutputFactory out, OutputFile file, CityJSONOutputOptions options) throws ExecutionException {
        try {
            AbstractCityJSONWriter<?> writer = options.isJsonLines() ?
                    out.createCityJSONFeatureWriter(file.getFile(), options.getEncoding()) :
                    out.createCityJSONWriter(file.getFile(), options.getEncoding())
                            .withIndent(options.isPrettyPrint() ? "  " : null);

            return writer.setHtmlSafe(options.isHtmlSafe());
        } catch (CityJSONWriteException e) {
            throw new ExecutionException("Failed to create CityJSON writer.", e);
        }
    }

    void setCityGMLVersion(CityGMLReader reader, CityGMLOutputFactory out) throws CityGMLReadException {
        reader.hasNext();
        CityGMLVersion version = getCityGMLVersion(reader);
        if (version != null) {
            log.debug("Using CityGML " + version + " for the output file.");
            out.withCityGMLVersion(version);
        } else {
            log.warn("Failed to detect CityGML version from input file. " +
                    "Using CityGML " + out.getVersion() + " for the output file.");
        }
    }

    CityGMLVersion getCityGMLVersion(CityGMLReader reader) throws CityGMLReadException {
        reader.hasNext();
        return reader.getNamespaces().get().stream()
                .filter(CityGMLModules::isCityGMLNamespace)
                .map(CityGMLModules::getCityGMLVersion)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);
    }

    FeatureInfo getFeatureInfo(CityGMLReader reader) throws CityGMLReadException {
        reader.hasNext();
        return reader.getParentInfo();
    }

    private void reportUnsupportedNamespaces(CityGMLReader reader) throws CityGMLReadException {
        reader.hasNext();
        Set<String> namespaces = CityGMLModules.of(CityGMLVersion.v3_0).getNamespaces();
        namespaces.addAll(CityGMLModules.of(CityGMLVersion.v2_0).getNamespaces());
        namespaces.addAll(CityGMLModules.of(CityGMLVersion.v1_0).getNamespaces());
        namespaces.add("http://www.opengis.net/citygml/profiles/base/2.0");
        namespaces.add("http://www.opengis.net/citygml/texturedsurface/2.0");
        namespaces.add("http://www.opengis.net/citygml/profiles/base/1.0");
        namespaces.add("http://www.opengis.net/citygml/texturedsurface/1.0");

        CityGMLADELoader loader = ADERegistry.getInstance().getADELoader(CityGMLADELoader.class);
        Set<String> unsupported = new HashSet<>();
        for (String namespace : reader.getNamespaces().get()) {
            if (!XMLConstants.W3C_XML_SCHEMA_NS_URI.equals(namespace)
                    && !XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI.equals(namespace)
                    && !XMLConstants.XMLNS_ATTRIBUTE_NS_URI.equals(namespace)
                    && !XMLConstants.XML_NS_URI.equals(namespace)
                    && !namespaces.contains(namespace)
                    && loader.getADEModule(namespace) == null) {
                unsupported.add(namespace);
            }
        }

        if (!unsupported.isEmpty()) {
            log.warn("The input file uses unsupported non-CityGML namespaces: " + String.join(", ", unsupported) + ".");
            log.info("Non-CityGML content is skipped unless a matching ADE extension has been loaded.");
        }
    }

    List<InputFile> getInputFiles(InputOptions options) throws ExecutionException {
        return getInputFiles(InputFiles.of(options.getFile()));
    }

    List<InputFile> getInputFiles(InputOptions options, String suffix) throws ExecutionException {
        return getInputFiles(InputFiles.of(options.getFile())
                .withFilter(path -> !FileHelper.stripFileExtension(path).endsWith(suffix)));
    }

    List<InputFile> getInputFiles(InputFiles builder) throws ExecutionException {
        log.debug("Searching for CityGML input files.");
        List<InputFile> inputFiles = builder.find();
        if (inputFiles.isEmpty()) {
            log.warn("No files found at " + String.join(", ", builder.getFiles()) + ".");
        } else if (inputFiles.size() > 1) {
            log.info("Found " + inputFiles.size() + " file(s) at " + String.join(", ", builder.getFiles()) + ".");
        }

        return inputFiles;
    }

    Path getOutputDirectory(InputFile file, CityGMLOutputOptions outputOptions) throws ExecutionException {
        return getOutputDirectory(file, outputOptions.getOutputDirectory());
    }

    Path getOutputDirectory(InputFile file, CityJSONOutputOptions outputOptions) throws ExecutionException {
        return getOutputDirectory(file, outputOptions.getOutputDirectory());
    }

    private Path getOutputDirectory(InputFile file, Path outputDir) throws ExecutionException {
        if (outputDir == null || outputDir.equals(file.getBasePath())) {
            return file.getFile().getParent();
        } else if (!file.getBasePath().equals(file.getFile().getParent())) {
            outputDir = outputDir.resolve(file.getBasePath().relativize(file.getFile().getParent()));
        }

        return getOrCreateOutputDirectory(outputDir);
    }

    Path getOrCreateOutputDirectory(Path outputDir) throws ExecutionException {
        if (outputDirs.add(outputDir.toString()) && !Files.exists(outputDir)) {
            try {
                Files.createDirectories(outputDir);
            } catch (Exception e) {
                throw new ExecutionException("Failed to create output directory " + outputDir + ".", e);
            }
        }

        return outputDir;
    }

    OutputFile getOutputFile(InputFile file, String suffix, CityGMLOutputOptions outputOptions, OverwriteInputOptions overwriteInputOptions) throws ExecutionException {
        if (outputOptions.getOutputDirectory() != null) {
            return OutputFile.of(getOutputDirectory(file, outputOptions.getOutputDirectory())
                    .resolve(file.getFile().getFileName()));
        } else if (overwriteInputOptions.isOverwrite()) {
            try {
                return OutputFile.temp("citygml-tools-", "");
            } catch (IOException e) {
                throw new ExecutionException("Failed to create temporary output file.", e);
            }
        } else {
            return OutputFile.of(file.getFile()
                    .resolveSibling(FileHelper.appendFileNameSuffix(file.getFile(), suffix)));
        }
    }

    void replaceInputFile(InputFile targetFile, OutputFile sourceFile) throws ExecutionException {
        try {
            log.debug("Replacing input file with temporary output file.");
            Files.move(sourceFile.getFile(), targetFile.getFile(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ExecutionException("Failed to replace input file.", e);
        }
    }

    void loadSchemas(Set<String> schemas, SchemaHandler schemaHandler) throws ExecutionException {
        if (schemas != null) {
            for (String schema : schemas) {
                try {
                    Path schemaFile = CityGMLTools.WORKING_DIR.resolve(schema).toAbsolutePath();
                    log.debug("Reading additional XML schema from " + schemaFile + ".");
                    schemaHandler.parseSchema(schema);
                } catch (Exception e) {
                    throw new ExecutionException("Failed to read XML schema from " + schema + ".", e);
                }
            }
        }
    }
}
