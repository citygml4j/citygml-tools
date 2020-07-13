/*
 * citygml-tools - Collection of tools for processing CityGML files
 * https://github.com/citygml4j/citygml-tools
 *
 * citygml-tools is part of the citygml4j project
 *
 * Copyright 2018-2020 Claus Nagel <claus.nagel@gmail.com>
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

package org.citygml4j.tools.command.validate;

import org.citygml4j.builder.jaxb.CityGMLBuilder;
import org.citygml4j.builder.jaxb.CityGMLBuilderException;
import org.citygml4j.tools.CityGMLTools;
import org.citygml4j.tools.command.CityGMLTool;
import org.citygml4j.tools.option.InputOptions;
import org.citygml4j.tools.option.LoggingOptions;
import org.citygml4j.tools.common.log.LogLevel;
import org.citygml4j.tools.common.log.Logger;
import org.citygml4j.tools.util.ObjectRegistry;
import org.citygml4j.tools.util.Util;
import org.citygml4j.xml.schema.SchemaHandler;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import picocli.CommandLine;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@CommandLine.Command(name = "xml",
        description = "Performs XML validation against the CityGML schemas.",
        versionProvider = CityGMLTools.class,
        mixinStandardHelpOptions = true,
        showAtFileInUsageHelp = true)
public class XMLSchemaCommand implements CityGMLTool {
    @CommandLine.Option(names = {"-s", "--suppress-validation-errors"}, description = "Do not show validation errors for a concise report.")
    private boolean suppressValidationErrors;

    @CommandLine.Option(names = "--schema", paramLabel = "<URI>", description = "Load additional XML schema from a file or URL. Note that the official CityGML schemas cannot be replaced.")
    private List<String> schemas;

    @CommandLine.Mixin
    private InputOptions input;

    @CommandLine.Mixin
    LoggingOptions logging;

    @Override
    public Integer call() throws Exception {
        Logger log = Logger.getInstance();
        CityGMLBuilder cityGMLBuilder = ObjectRegistry.getInstance().get(CityGMLBuilder.class);

        log.info("Performing XML validation against the official CityGML schemas.");

        log.debug("Searching for CityGML input files.");
        List<Path> inputFiles;
        try {
            inputFiles = new ArrayList<>(Util.listFiles(input.getFile(), "**.{gml,xml}"));
            log.info("Found " + inputFiles.size() + " file(s) at '" + input.getFile() + "'.");
        } catch (IOException e) {
            log.warn("Failed to find file(s) at '" + input.getFile() + "'.");
            return 0;
        }

        log.debug("Loading default CityGML schemas.");
        SchemaHandler schemaHandler;
        try {
            schemaHandler = cityGMLBuilder.getDefaultSchemaHandler();
        } catch (CityGMLBuilderException e) {
            log.error("Failed to load default CityGML schemas.", e);
            return 1;
        }

        if (schemas != null && !schemas.isEmpty()) {
            for (String schema : schemas) {
                try {
                    log.debug("Loading additional XML schema from '" + schema + "'.");
                    schemaHandler.parseSchema("", schema);
                } catch (SAXException e) {
                    log.error("Failed to load XML schema from '" + schema + "'.", e);
                    return 1;
                }
            }
        }

        Validator validator;
        try {
            SchemaFactory schemaFactory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
            Schema schema = schemaFactory.newSchema(schemaHandler.getSchemaSources());
            validator = schema.newValidator();
        } catch (SAXException e) {
            log.error("Failed to create validator object.", e);
            return 1;
        }

        ValidationErrorHandler errorHandler = new ValidationErrorHandler();
        int invalid = 0;

        for (int i = 0; i < inputFiles.size(); i++) {
            Path inputFile = inputFiles.get(i);
            log.info("[" + (i + 1) + "|" + inputFiles.size() + "] Validating file '" + inputFile.toAbsolutePath() + "'.");

            try {
                validator.setErrorHandler(errorHandler);
                validator.validate(new StreamSource(inputFile.toFile()));

                if (errorHandler.errors == 0)
                    log.info("The file is valid.");
                else {
                    log.warn("The file is invalid. Found " + errorHandler.errors + " error(s).");
                    invalid++;
                }
            } catch (SAXException | IOException e) {
                log.error("Failed to validate CityGML file.", e);
                return 1;
            } finally {
                validator.reset();
                errorHandler.reset();
            }
        }

        if (invalid == 0) {
            log.info("Validation complete. All files are valid.");
            return 0;
        } else {
            log.warn("Validation complete. Found " + invalid + " invalid file(s).");
            return 3;
        }
    }

    private class ValidationErrorHandler implements ErrorHandler {
        private final Logger log = Logger.getInstance();
        private String location;
        private int errors;

        @Override
        public void warning(SAXParseException e) throws SAXException {
            report(e, "Warning", LogLevel.WARN);
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            report(e, "Invalid content", LogLevel.ERROR);
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            report(e, "Invalid content", LogLevel.ERROR);
        }

        private void report(SAXParseException e, String type, LogLevel level) {
            String location = e.getLineNumber() + ", " + e.getColumnNumber();
            if (!location.equals(this.location)) {
                this.location = location;
                errors++;
            }

            if (!suppressValidationErrors)
                log.log(level, type + " at [" + location + "]: " + e.getMessage());
        }

        private void reset() {
            location = null;
            errors = 0;
        }
    }
}
