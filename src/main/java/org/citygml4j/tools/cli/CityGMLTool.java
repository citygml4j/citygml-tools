/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2022 Claus Nagel <claus.nagel@gmail.com>
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

package org.citygml4j.tools.cli;

import org.citygml4j.cityjson.CityJSONContext;
import org.citygml4j.cityjson.CityJSONContextException;
import org.citygml4j.cityjson.model.CityJSONVersion;
import org.citygml4j.cityjson.writer.AbstractCityJSONWriter;
import org.citygml4j.cityjson.writer.CityJSONOutputFactory;
import org.citygml4j.cityjson.writer.CityJSONWriteException;
import org.citygml4j.core.model.CityGMLVersion;
import org.citygml4j.xml.CityGMLContext;
import org.citygml4j.xml.CityGMLContextException;
import org.citygml4j.xml.module.citygml.CityGMLModules;
import org.citygml4j.xml.module.citygml.CoreModule;
import org.citygml4j.xml.reader.CityGMLInputFactory;
import org.citygml4j.xml.reader.CityGMLReadException;
import org.citygml4j.xml.reader.CityGMLReader;
import org.citygml4j.xml.writer.CityGMLChunkWriter;
import org.citygml4j.xml.writer.CityGMLOutputFactory;
import org.citygml4j.xml.writer.CityGMLWriteException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public abstract class CityGMLTool implements Command {
    private CityGMLContext cityGMLContext;
    private CityJSONContext cityJSONContext;

    protected CityGMLContext getCityGMLContext() throws ExecutionException {
        if (cityGMLContext == null) {
            try {
                cityGMLContext = CityGMLContext.newInstance();
            } catch (CityGMLContextException e) {
                throw new ExecutionException("Failed to create CityGML context.", e);
            }
        }

        return cityGMLContext;
    }

    protected CityJSONContext getCityJSONContext() throws ExecutionException {
        if (cityJSONContext == null) {
            try {
                cityJSONContext = CityJSONContext.newInstance();
            } catch (CityJSONContextException e) {
                throw new ExecutionException("Failed to create CityJSON context.", e);
            }
        }

        return cityJSONContext;
    }

    protected CityGMLInputFactory createCityGMLInputFactory() throws ExecutionException {
        try {
            return getCityGMLContext().createCityGMLInputFactory();
        } catch (CityGMLReadException e) {
            throw new ExecutionException("Failed to create CityGML input factory.", e);
        }
    }

    protected CityGMLReader createCityGMLReader(CityGMLInputFactory in, Path file, CityGMLInputOptions options) throws ExecutionException {
        try {
            return in.createCityGMLReader(file, options.getEncoding());
        } catch (CityGMLReadException e) {
            throw new ExecutionException("Failed to create CityGML reader.", e);
        }
    }

    protected CityGMLReader createFilteredCityGMLReader(CityGMLInputFactory in, Path file, CityGMLInputOptions options, String... localNames) throws ExecutionException {
        CityGMLReader reader = createCityGMLReader(in, file, options);
        if (localNames != null) {
            Set<String> names = new HashSet<>(Arrays.asList(localNames));
            return in.createFilteredCityGMLReader(reader, name -> !names.contains(name.getLocalPart())
                    || !CityGMLModules.isCityGMLNamespace(name.getNamespaceURI()));
        } else {
            return reader;
        }
    }

    protected CityGMLOutputFactory createCityGMLOutputFactory(CityGMLVersion version) throws ExecutionException {
        return getCityGMLContext().createCityGMLOutputFactory(version);
    }

    protected CityGMLChunkWriter createCityGMLChunkWriter(CityGMLOutputFactory out, Path file, CityGMLOutputOptions options) throws ExecutionException {
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

    protected CityJSONOutputFactory createCityJSONOutputFactory(CityJSONVersion version) throws ExecutionException {
        return getCityJSONContext().createCityJSONOutputFactory(version);
    }

    protected AbstractCityJSONWriter<?> createCityJSONWriter(CityJSONOutputFactory out, Path file, CityJSONOutputOptions options) throws ExecutionException {
        try {
            AbstractCityJSONWriter<?> writer = options.isWriteCityJSONFeatures() ?
                    out.createCityJSONFeatureWriter(file, options.getEncoding()) :
                    out.createCityJSONWriter(file, options.getEncoding())
                        .withIndent(options.isPrettyPrint() ? "  " : null);

            return writer.setHtmlSafe(options.isHtmlSafe());
        } catch (CityJSONWriteException e) {
            throw new ExecutionException("Failed to create CityJSON writer.", e);
        }
    }

    protected Path getOutputFile(Path file, String suffix, boolean overwrite) {
        return file.resolveSibling(overwrite ?
                ".tmp_" + UUID.randomUUID() :
                appendFileNameSuffix(file, suffix));
    }

    protected void replaceInputFile(Path inputFile, Path tempFile) throws ExecutionException {
        try {
            Files.delete(inputFile);
            Files.move(tempFile, tempFile.resolveSibling(inputFile.getFileName()));
        } catch (IOException e) {
            throw new ExecutionException("Failed to replace input file.", e);
        }
    }

    protected String appendFileNameSuffix(Path file, String suffix) {
        String[] parts = splitFileName(file);
        return parts[0] + suffix + "." + parts[1];
    }

    protected String replaceFileExtension(Path file, String extension) {
        String fileName = splitFileName(file)[0];
        if (!extension.startsWith(".")) {
            extension = "." + extension;
        }

        return fileName + extension;
    }

    protected String stripFileExtension(Path file) {
        return splitFileName(file)[0];
    }

    protected String[] splitFileName(Path file) {
        String fileName = file.getFileName().toString();
        int index = fileName.lastIndexOf('.');
        return index > 0 ?
                new String[]{fileName.substring(0, index), fileName.substring(index + 1)} :
                new String[]{fileName, ""};
    }
}
