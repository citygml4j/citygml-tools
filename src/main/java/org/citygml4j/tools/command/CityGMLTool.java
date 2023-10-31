/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2023 Claus Nagel <claus.nagel@gmail.com>
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
import org.citygml4j.tools.ExecutionException;
import org.citygml4j.tools.log.Logger;
import org.citygml4j.tools.option.CityGMLOutputOptions;
import org.citygml4j.tools.option.CityJSONOutputOptions;
import org.citygml4j.tools.option.InputOptions;
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

import javax.xml.XMLConstants;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public abstract class CityGMLTool implements Command {
    final Logger log = Logger.getInstance();
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

    CityGMLReader createCityGMLReader(CityGMLInputFactory in, Path file, InputOptions options) throws ExecutionException, CityGMLReadException {
        return createCityGMLReader(in, file, options, null);
    }

    CityGMLReader createSkippingCityGMLReader(CityGMLInputFactory in, Path file, InputOptions options, String... localNames) throws ExecutionException, CityGMLReadException {
        CityGMLInputFilter filter = null;
        if (localNames != null) {
            Set<String> names = new HashSet<>(Arrays.asList(localNames));
            filter = name -> !names.contains(name.getLocalPart())
                    || !CityGMLModules.isCityGMLNamespace(name.getNamespaceURI());
        }

        return createCityGMLReader(in, file, options, filter);
    }

    private CityGMLReader createCityGMLReader(CityGMLInputFactory in, Path file, InputOptions options, CityGMLInputFilter filter) throws ExecutionException, CityGMLReadException {
        CityGMLReader reader;
        try {
            reader = in.createCityGMLReader(file, options.getEncoding());
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

    CityGMLChunkWriter createCityGMLChunkWriter(CityGMLOutputFactory out, Path file, CityGMLOutputOptions options) throws ExecutionException {
        try {
            return out.createCityGMLChunkWriter(file, options.getEncoding())
                    .withDefaultPrefixes()
                    .withDefaultSchemaLocations()
                    .withDefaultNamespace(CoreModule.of(out.getVersion()).getNamespaceURI())
                    .withIndent(options.isPrettyPrint() ? "  " : null);
        } catch (CityGMLWriteException e) {
            throw new ExecutionException("Failed to create CityGML writer.", e);
        }
    }

    CityJSONInputFactory createCityJSONInputFactory() throws ExecutionException {
        return getCityJSONContext().createCityJSONInputFactory();
    }

    CityJSONReader createCityJSONReader(CityJSONInputFactory in, Path file, InputOptions options) throws ExecutionException {
        try {
            return options.isSetEncoding() ?
                    in.createCityJSONReader(file, options.getEncoding()) :
                    in.createCityJSONReader(file);
        } catch (CityJSONReadException e) {
            throw new ExecutionException("Failed to create CityJSON reader.", e);
        }
    }

    CityJSONOutputFactory createCityJSONOutputFactory(CityJSONVersion version) throws ExecutionException {
        return getCityJSONContext().createCityJSONOutputFactory(version);
    }

    AbstractCityJSONWriter<?> createCityJSONWriter(CityJSONOutputFactory out, Path file, CityJSONOutputOptions options) throws ExecutionException {
        try {
            AbstractCityJSONWriter<?> writer = options.isJsonLines() ?
                    out.createCityJSONFeatureWriter(file, options.getEncoding()) :
                    out.createCityJSONWriter(file, options.getEncoding())
                            .withIndent(options.isPrettyPrint() ? "  " : null);

            return writer.setHtmlSafe(options.isHtmlSafe());
        } catch (CityJSONWriteException e) {
            throw new ExecutionException("Failed to create CityJSON writer.", e);
        }
    }

    void setCityGMLVersion(CityGMLReader reader, CityGMLOutputFactory out) throws CityGMLReadException {
        reader.hasNext();
        CityGMLVersion version = reader.getNamespaces().get().stream()
                .filter(CityGMLModules::isCityGMLNamespace)
                .map(CityGMLModules::getCityGMLVersion)
                .filter(Objects::nonNull)
                .findFirst().orElse(null);

        if (version != null) {
            log.debug("Using CityGML " + version + " for the output file.");
            out.withCityGMLVersion(version);
        } else {
            log.warn("Failed to detect CityGML version from input file. " +
                    "Using CityGML " + out.getVersion() + " for the output file.");
        }
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
            log.warn("The input file uses unsupported non-CityGML namespace(s): " +
                    String.join(", ", unsupported) + ".");
            log.info("Non-CityGML content is skipped unless a matching ADE extension has been loaded.");
        }
    }

    Path getOutputFile(Path file, String suffix, boolean overwrite) throws ExecutionException {
        return overwrite ?
                createTempFile() :
                file.resolveSibling(appendFileNameSuffix(file, suffix));
    }

    Path createTempFile() throws ExecutionException {
        try {
            return Files.createTempFile("citygml-tools-", "");
        } catch (IOException e) {
            throw new ExecutionException("Failed to create temp file.", e);
        }
    }

    void replaceInputFile(Path targetFile, Path sourceFile) throws ExecutionException {
        try {
            Files.move(sourceFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new ExecutionException("Failed to replace input file.", e);
        }
    }

    String appendFileNameSuffix(Path file, String suffix) {
        String[] parts = splitFileName(file);
        return parts[0] + suffix + "." + parts[1];
    }

    String replaceFileExtension(Path file, String extension) {
        String fileName = splitFileName(file)[0];
        if (!extension.startsWith(".")) {
            extension = "." + extension;
        }

        return fileName + extension;
    }

    String stripFileExtension(Path file) {
        return splitFileName(file)[0];
    }

    String[] splitFileName(Path file) {
        String fileName = file.getFileName().toString();
        int index = fileName.lastIndexOf('.');
        return index > 0 ?
                new String[]{fileName.substring(0, index), fileName.substring(index + 1)} :
                new String[]{fileName, ""};
    }
}
